# Custom dimensions, teleportation, and portal blocks

## Custom dimensions are static datapack JSON, not runtime-created

The original Dimensional Doors mod (the inspiration for this project) dynamically created pocket
dimensions at runtime. That is NOT how modern (1.21.1) modded dimensions work, and isn't something
we've tried to replicate - a custom dimension here is defined once via datapack JSON shipped
inside the mod's own resources, loaded at world-load time like a built-in datapack:

```
src/main/resources/data/<modid>/dimension_type/<name>.json
src/main/resources/data/<modid>/dimension/<name>.json
```

`dimension_type` controls physics/rendering properties (skylight, ceiling, ambient light, fixed
time, height bounds, monster spawn light levels, `effects` for sky rendering). Get the exact field
list by extracting vanilla's own `overworld.json` / `the_end.json` from the client jar rather than
guessing - see `finding-ground-truth-source.md`.

`dimension` references a `dimension_type` by ID and provides a chunk generator. For a guaranteed
walkable floor (rather than dropping into void), use the `minecraft:flat` generator type with
explicit `layers`:
```json
{
  "type": "dimdescent:rift",
  "generator": {
    "type": "minecraft:flat",
    "settings": {
      "biome": "minecraft:the_void",
      "lakes": false,
      "features": false,
      "layers": [
        { "block": "minecraft:bedrock", "height": 1 },
        { "block": "minecraft:stone", "height": 5 }
      ]
    }
  }
}
```

Reference the dimension elsewhere in code via a `ResourceKey<Level>`:
```java
ResourceKey<Level> RIFT_LEVEL = ResourceKey.create(
    Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(MODID, "rift"));
ServerLevel riftLevel = server.getLevel(RIFT_LEVEL);
```

## Teleportation: `ServerPlayer.changeDimension(DimensionTransition)`

`DimensionTransition` is a record:
```java
record DimensionTransition(
    ServerLevel newLevel, Vec3 pos, Vec3 speed, float yRot, float xRot,
    boolean missingRespawnBlock, DimensionTransition.PostDimensionTransition postDimensionTransition
)
```
Prebuilt `PostDimensionTransition` callbacks: `DimensionTransition.DO_NOTHING`,
`.PLAY_PORTAL_SOUND`, `.PLACE_PORTAL_TICKET` (and `.then(...)` to chain them).

Simple command-driven teleport (no portal block involved):
```java
player.changeDimension(new DimensionTransition(
    targetLevel, targetPos, entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(),
    DimensionTransition.DO_NOTHING));
```

## Custom portal blocks: implement `net.minecraft.world.level.block.Portal`

Don't hand-roll teleport-on-touch logic directly in `entityInside` by calling `changeDimension`
every tick - that has no cooldown/debounce and will flicker/re-trigger constantly. Instead
implement the `Portal` interface, which plugs into vanilla's existing portal-cooldown machinery:

```java
public interface Portal {
    default int getPortalTransitionTime(ServerLevel level, Entity entity) { return 0; }
    @Nullable DimensionTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos);
    default Portal.Transition getLocalTransition() { return Portal.Transition.NONE; }
}
```

Only `getPortalDestination` is required. Pattern (mirrors vanilla `EndPortalBlock`, which is the
best real reference - extract it from decompiled source and read it directly):

```java
public class MyPortalBlock extends SomeBlock implements Portal {
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (/* your open/active condition */ && entity.canUsePortal(false)) {
            entity.setAsInsidePortal(this, pos);
        }
    }

    @Override
    public DimensionTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        boolean goingHome = level.dimension() == MY_CUSTOM_DIMENSION_KEY;
        ResourceKey<Level> targetKey = goingHome ? Level.OVERWORLD : MY_CUSTOM_DIMENSION_KEY;
        ServerLevel targetLevel = level.getServer().getLevel(targetKey);
        if (targetLevel == null) return null; // dimension failed to load - handle gracefully
        Vec3 targetPos = goingHome ? Vec3.atBottomCenterOf(targetLevel.getSharedSpawnPos()) : MY_FIXED_SPAWN;
        return new DimensionTransition(targetLevel, targetPos, entity.getDeltaMovement(),
                entity.getYRot(), entity.getXRot(), DimensionTransition.DO_NOTHING);
    }

    @Override
    public int getPortalTransitionTime(ServerLevel level, Entity entity) { return 0; } // instant, vs nether's ~4s delay
}
```

`entity.canUsePortal(false)` and `entity.setAsInsidePortal(this, pos)` give you the same
cooldown/eligibility handling nether and end portals use, for free.

If the same block class is used on both sides of the portal (e.g. one door in the overworld, its
mirror in the custom dimension), branch on `level.dimension()` inside `getPortalDestination` to
decide direction - don't hardcode a single target.

## `entityInside` fires for the whole block cell, not just the visible/collision shape

`Block.entityInside(state, level, pos, entity)` is called for every block position an entity's
bounding box overlaps, **regardless of that block's actual `VoxelShape`**. It is NOT pre-filtered
to only fire where the block is actually "solid" or visually present. This bit us building a
portal door: gating a teleport purely on `isOpen(state) && entity.canUsePortal(false)` triggered
for an entity standing anywhere in the door's 1x1x2 cell, not just where the glowing portal effect
was actually rendered - so walking past an open door without touching the visible effect still
teleported the player.

Fix (same pattern vanilla's `EndPortalBlock` uses, which explicitly intersects against its own
shape rather than trusting an implicit filter): compute the specific `AABB` you want to be the
"real" trigger zone, and manually check `entityBoundingBox.intersects(triggerBounds)` (or use
`Shapes.joinIsNotEmpty` for a true `VoxelShape`) before calling `setAsInsidePortal`. Don't assume
the engine does this restriction for you.

## Gotcha: local-space geometry for a 2-tall block must anchor to one consistent half

`entityInside` (and `newBlockEntity`) fire independently for EACH block position a multi-block
structure occupies - for a door, that means it can be called once with `pos` = the lower half and
separately with `pos` = the upper half, both carrying the *same* `BlockState` values (facing,
hinge, open) since those are shared, but a *different* `pos`. If you're computing some local-space
geometry (e.g. "the window is at Y=[1.5, 1.8] relative to the block") and blending world-position
math into it, resolve to a single canonical anchor first:
```java
BlockPos lowerPos = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
```
then always offset your local coordinates by `lowerPos`, never by whichever `pos` happened to
trigger the call - otherwise geometry computed relative to the upper half ends up off by a full
block when the lower half is what actually triggered, or vice versa.

## Extracting shared logic

If both a debug command (`/rift enter`) and an in-world block (a door) need to compute "where does
this entity go," factor the dimension-selection logic into a small shared helper class rather than
duplicating it - the command can call `player.changeDimension(helper.getTransitionFor(...))`
directly, while the block implements `Portal.getPortalDestination` by delegating to the same
helper.

## Authoring & importing structures (.nbt), and patching them in code

**Capture in-game with a Structure Block.** Two blocks in CORNER mode at opposite corners plus one
switched to SAVE (Save block counts as its own corner). The captured region is EXCLUSIVE of both
corner blocks - `detectSize()` does `pos = min+1, size = span-1` - so each corner block sits one
block OUTSIDE the build on all axes. Name it `<modid>:<name>` (bare name defaults to `minecraft:`).
Saved to `run/saves/<world>/generated/<modid>/structures/<name>.nbt` (note: the world folder uses
plural `structures/`, but the datapack/mod resource path is SINGULAR `data/<modid>/structure/<name>.nbt`).
Max 48^3.

**Generated/ shadows the mod copy.** `StructureTemplateManager` sources are checked in order:
generated first, then mod resources/datapacks. So a world that has its own
`generated/.../structures/<name>.nbt` will use THAT for `/place template`, ignoring the mod's copy -
which bites when you patch the mod copy and test in the same world it was saved in. Delete the
world's generated file (or test in a fresh world) to fall through to the mod resource.

**Chests spawn empty unless their block entity has a loot table.** A structure only contains blocks
you actually built, and a captured chest's block-entity nbt has `{id, Items:[]}`. Point it at a loot
table by setting the string key `"LootTable"` (read by `RandomizableContainer.tryLoadLootTable`) in
that chest block's `nbt` compound, e.g. `"dimdescent:chests/altar"`, and drop the empty `Items`. The
loot rolls on FIRST OPEN, so the chest looks empty in the file and in-world until opened.

**Patching an .nbt in code** (to inject the LootTable without re-saving in-game): there is no NBT
library in this Python env, so a hand-rolled codec is required. Structure NBT is gzipped, big-endian.
A round-trip-safe representation: tags as `(type_int, value)`, compounds as `dict[str,tag]`, lists as
`(elem_type, [values...])` - keeping the element type exact so re-encode is byte-faithful. VERIFY the
codec by decoding->encoding->decoding and asserting equality BEFORE trusting a patch, then re-read the
written file to confirm the change landed. LIST payloads are BARE values (element type declared once),
so palette/blocks entries are dicts directly, and an INT list's payloads are plain ints, not tuples -
easy to trip on when walking the tree. (Reusable codec lived in the session scratchpad as
`nbt_codec.py`.)

## Loot table & recipe datapack folders are SINGULAR in 1.21

`data/<ns>/loot_table/...` and `data/<ns>/recipe/...` (renamed from the old plural `loot_tables`/
`recipes`). A file in the old plural folder is silently ignored. Same singular-rename applies to
`structure/`, `advancement/`, `worldgen/...`, etc.

## Jigsaw structure required fields (bit us - crashed world creation)

Placing a single authored NBT via world-gen = a `minecraft:jigsaw` structure with a one-element
`template_pool`. The jigsaw structure codec's REQUIRED fields (verified against
`Structure.StructureSettings.CODEC` and `JigsawStructure.CODEC`) are:

- `biomes`, `spawn_overrides`, `step`  (from the base StructureSettings)
- `start_pool`, `size`, `start_height`, `use_expansion_hack`, `max_distance_from_center`  (jigsaw)

`spawn_overrides` is REQUIRED even when empty - use `"spawn_overrides": {}`. Omitting it throws
`No key spawn_overrides` and crashes world creation. Optional (safe to omit): `terrain_adaptation`,
`project_start_to_heightmap`, `start_jigsaw_name`, `pool_aliases`, `dimension_padding`,
`liquid_settings`. A working minimal altar structure:

```json
{
  "type": "minecraft:jigsaw",
  "biomes": "#dimdescent:has_altar",
  "spawn_overrides": {},
  "step": "surface_structures",
  "size": 1,
  "start_pool": "dimdescent:altar",
  "start_height": {"absolute": 0},
  "project_start_to_heightmap": "WORLD_SURFACE_WG",
  "max_distance_from_center": 80,
  "terrain_adaptation": "beard_thin",
  "use_expansion_hack": false
}
```

The one-element pool (`template_pool/altar.json`): `single_pool_element`, `location` = the NBT id,
`processors` = `"minecraft:empty"`, `projection` = `"rigid"`, plus `"fallback": "minecraft:empty"`.
The `structure_set` is a `random_spread` with `spacing`/`separation` (villages are 34/8) and a unique
`salt`. Scope the biome tag to LAND (`#minecraft:is_forest`/`is_taiga`/... plus plains/desert/etc.)
rather than `#minecraft:is_overworld`, or a surface building spawns half-submerged in oceans.
