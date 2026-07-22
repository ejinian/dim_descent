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

## Extracting shared logic

If both a debug command (`/rift enter`) and an in-world block (a door) need to compute "where does
this entity go," factor the dimension-selection logic into a small shared helper class rather than
duplicating it - the command can call `player.changeDimension(helper.getTransitionFor(...))`
directly, while the block implements `Portal.getPortalDestination` by delegating to the same
helper.
