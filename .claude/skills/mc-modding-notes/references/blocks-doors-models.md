# Blocks, doors, and models

## Reuse vanilla's shared model templates instead of authoring geometry from scratch

Vanilla door block models are NOT self-contained geometry. `iron_door_bottom_left.json` is just a
thin wrapper:
```json
{
  "parent": "minecraft:block/door_bottom_left",
  "textures": { "bottom": "minecraft:block/iron_door_bottom", "top": "minecraft:block/iron_door_top" }
}
```
`minecraft:block/door_bottom_left` (no material prefix) is the actual shared geometry template,
reused by every vanilla door regardless of material. This means a custom door can get 100% correct
door geometry/hinge-rotation "for free" by writing the same thin wrapper pattern pointing at your
own textures - you never need to hand-author door cuboid geometry. This pattern likely generalizes
to other vanilla block families with material variants (trapdoors, fence gates, etc) - check for a
shared unprefixed template model before assuming you need to build geometry by hand.

The full set of door model variants needed (8 total, combined with blockstate y-rotations to cover
all 32 facing/hinge/half/open combinations):
```
door_bottom_left(.json)       door_bottom_left_open
door_bottom_right             door_bottom_right_open
door_top_left                 door_top_left_open
door_top_right                door_top_right_open
```

**Blockstate JSON**: copy vanilla's full `iron_door.json` variants table verbatim (32 entries),
just renaming the model paths to your own namespace - the facing/hinge/open-to-rotation mapping
is intricate (asymmetric y-rotation values per combination) and easy to transcribe wrong by hand.
Verify correctness by diffing your file against the extracted vanilla original programmatically
(e.g. a small Python script comparing rotation values per key) rather than eyeballing a 32-entry
table.

## Item placement for double-tall blocks

Use `net.minecraft.world.item.DoubleHighBlockItem` (not plain `BlockItem`) for the item that places
a 2-tall block like a door - it handles placing the block, then separately clearing/placing the
block above it (`context.getClickedPos().above()`), which a plain `BlockItem` doesn't do.

## Door hinge-selection is a standard vanilla heuristic, not something you control per-block

`DoorBlock.getStateForPlacement` / the private `getHinge` method choose left/right hinge based on
neighboring block collision shapes plus which way the player is facing when placing - this is
shared by every door in the game (oak, iron, custom) since it lives in the base `DoorBlock` class,
untouched by subclassing. **If a custom door "opens the wrong way" or "hinge is on the wrong
side," verify it's not just this standard context-dependent behavior before assuming there's a
bug** - diff your blockstate JSON against vanilla's iron_door.json first (see above). Vanilla
IRON doors specifically can't even be opened by hand at all (`BlockSetType.IRON` has
`canOpenByHand=false`, redstone-only) - if a user's mental model of "normal door" comes from wood
doors, that's a different `BlockSetType`, not a hinge bug.

To make a custom door hand-openable but keep other door-like sound/behavior, construct your own
`BlockSetType` instance (it's just a public record) rather than reusing `BlockSetType.IRON`:
```java
public static final BlockSetType MY_DOOR_SET = new BlockSetType(
    "my_door", true, true, false, BlockSetType.PressurePlateSensitivity.EVERYTHING,
    SoundType.STONE, SoundEvents.IRON_DOOR_CLOSE, SoundEvents.IRON_DOOR_OPEN,
    SoundEvents.IRON_TRAPDOOR_CLOSE, SoundEvents.IRON_TRAPDOOR_OPEN,
    SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
    SoundEvents.STONE_BUTTON_CLICK_OFF, SoundEvents.STONE_BUTTON_CLICK_ON);
```
(`SoundType` is `net.minecraft.world.level.block.SoundType`, NOT `net.minecraft.sounds.SoundType`
- that's an easy import mistake since `SoundEvents` lives in `net.minecraft.sounds`.)

## Forcing a hinge side, and finding where the swung-open panel actually sits

You *can* override where a custom door's hinge ends up, cleanly, without touching the placement
heuristic itself - `DoorBlock.getStateForPlacement` is public and overridable, so just call
`super` and patch the result:
```java
@Override
public BlockState getStateForPlacement(BlockPlaceContext context) {
    BlockState state = super.getStateForPlacement(context);
    return state == null ? null : state.setValue(HINGE, DoorHingeSide.LEFT);
}
```
(`state` can be `null` if placement is invalid, e.g. no room for the upper half - preserve that.)
Since `setPlacedBy` copies this same state up to the upper half, one override covers both.

Separately, `DoorBlock.getShape` (its actual `VoxelShape`/collision method) is a small, exhaustive
switch table worth reading directly rather than reverse-engineering from rotation math:
```java
protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    Direction direction = state.getValue(FACING);
    boolean closed = !state.getValue(OPEN);
    boolean hingeRight = state.getValue(HINGE) == DoorHingeSide.RIGHT;
    return switch (direction) {
        case SOUTH -> closed ? SOUTH_AABB : (hingeRight ? EAST_AABB : WEST_AABB);
        case WEST -> closed ? WEST_AABB : (hingeRight ? SOUTH_AABB : NORTH_AABB);
        case NORTH -> closed ? NORTH_AABB : (hingeRight ? WEST_AABB : EAST_AABB);
        default -> closed ? EAST_AABB : (hingeRight ? NORTH_AABB : SOUTH_AABB); // EAST
    };
}
```
This is the reliable way to find out **where the physically swung-open door panel sits** (useful
if you're rendering something that needs to avoid or track it): don't hand-derive it, just query
`state.setValue(OPEN, true).getShape(level, pos).bounds()` and read the resulting `AABB` off the
real block, rather than working out the geometry yourself. Same trick works for "where does the
CLOSED slab sit" via `state.setValue(OPEN, false).getShape(...)` regardless of the block's actual
current open state - handy for keeping some other piece of geometry (a rendered effect, a hitbox)
anchored to the closed-door frame position even while the door is actually open and the physical
mesh has rotated away from it.

## Don't shrink a cutout texture's opaque border down to a hairline

Enlarging a transparent "window" cutout in an otherwise-opaque texture (e.g. to make more of a
custom shader effect show through) has a real ceiling: if the remaining opaque border gets down to
~1px against a large transparent area, GPU mipmapping blends that thin sliver into visible garbage
at any distance or oblique angle - it can look like the texture "lost its detail" entirely, or
show "hollow"/washed-out patches, even though the source PNG is completely correct pixel-for-pixel
up close. This isn't a code bug and won't show up in any log - it only shows up visually, in-game,
at a distance/angle. Concretely: two 7x7 window blocks with 1px margins on a 16x16 canvas (meant to
give bigger ~5x5 transparent interiors) left only a 1px opaque strip around the very edge of the
texture, which is what caused it. Keep real margin - a rough rule of thumb is the remaining opaque
border shouldn't be thinner than the cutout itself for the transition to hold up at any viewing
distance.

## Texture/asset copyright - never copy Mojang's actual files into the mod

Copying pixel data from Mojang's shipped textures (extracted from the game jar) into our own mod's
`src/main/resources` is redistributing their copyrighted art as part of a distributed mod - a real
EULA/IP problem, not a style nitpick. If the user asks to "just use the vanilla X texture," flag
this distinction:
- **Fine**: referencing their asset by resource location in a model JSON
  (`"top": "minecraft:block/iron_door_top"`) - the user's own legitimate game client supplies the
  actual pixels at runtime, nothing is duplicated/redistributed.
- **Not fine**: extracting the PNG and saving a copy (even a modified one, like punching
  transparency into part of it) inside our mod's own asset folder.
- If part of the vanilla look needs to be selectively modified (e.g., punching transparent
  "windows" into part of an otherwise iron-door-styled texture), the safe path is to author
  original artwork with a similar palette/style, not to start from a copy of their file.

## Animated block/item textures don't need a shader - just a taller PNG + a `.mcmeta`

Don't reach for the custom-shader machinery (see `rendering-shaders-blockentities.md`) for a
normal animated texture (glowing veins, flowing liquid, anything vanilla's lava/fire/etc. do) -
that technique is specifically for parallax/view-angle-dependent effects like the End Portal. A
regular animated texture is much simpler: a single PNG taller than it is wide (16 wide x 16*N
tall, one frame per 16px band stacked top-to-bottom) plus a sidecar file at the same path with
`.mcmeta` appended, e.g. `textures/block/my_block.png` + `textures/block/my_block.png.mcmeta`:
```json
{ "animation": { "frametime": 4, "interpolate": true } }
```
`frametime` is in game ticks per frame (20 ticks/sec). `interpolate: true` blends between frames
(including the last-frame-back-to-first wraparound) instead of hard-cutting, which matters a lot
for how smooth it reads.

For the animation to actually look like *movement* rather than "pulsing brightness in place," each
frame's pattern needs to be procedurally shifted, not just recolored at a fixed position - e.g.
compute pixel color from a continuous function of `(x, y, frame/FRAMES)` where the frame term
shifts a periodic pattern's phase. Parametrize the animated term as `frame/FRAMES * K` (K = an
integer number of full cycles over the whole loop) rather than some other derived quantity - that
guarantees the sequence is seamless across the frame-23-to-frame-0 wraparound for free, since it's
just evenly-spaced samples of one truly periodic function. Smooth (non-pixel-hard) gradients read
much better than a binary "this pixel is part of the pattern or not" classification - blend colors
continuously (e.g. `lerp(bg, glow, smoothstep(...))`) rather than assigning pixels to a fixed set.

## Reuse vanilla's actual Block/Item Java classes when the behavior is already exactly right

Don't reflexively write a new Block subclass for something vanilla already implements identically.
Vanilla block classes are normal public, non-final classes with public `Properties`-taking
constructors - if you want "obsidian-hard iron bars" or "a plain flower with no stew effect," you
can register a **new instance of the vanilla class itself** with your own `Properties` and your own
textures, and get 100% correct behavior (connection logic, placement heuristics, shapes) for free:
```java
// Dark Iron Bars: identical connection/shape logic to vanilla iron_bars, just harder.
BLOCKS.register("dark_iron_bars", () -> new IronBarsBlock(
    BlockBehaviour.Properties.of().requiresCorrectToolForDrops()
        .strength(50.0F, 1200.0F).sound(SoundType.METAL).noOcclusion()));

// Datura: FlowerBlock normally takes a MobEffect for its suspicious-stew effect, but there's a
// second constructor overload taking a SuspiciousStewEffects directly - pass EMPTY for a flower
// with the standard wobble/instabreak/placement behavior but no stew effect, no custom class needed.
BLOCKS.register("datura", () -> new FlowerBlock(SuspiciousStewEffects.EMPTY,
    BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak()
        .sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ)
        .pushReaction(PushReaction.DESTROY)));
```
Same principle extends to the blockstate/model side: `iron_bars.json`'s multipart blockstate and
its 6 model files (`iron_bars_post`, `_post_ends`, `_cap`, `_cap_alt`, `_side`, `_side_alt`) can be
copied verbatim (renamespaced, texture key repointed) with zero geometry changes - the connection
*logic* lives in the Java class (`CrossCollisionBlock`/`IronBarsBlock`), not the JSON.

## Extending a vanilla tag (tool tiers, mineable-by, etc.) from a mod without touching vanilla's file

Tags merge additively across datapacks/mods that both define the same tag path - you don't need
(and shouldn't try) to copy vanilla's full tag file. Just ship a small file at the *same*
`data/minecraft/tags/...` path inside your own mod's resources, containing only your additions:
```json
// src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json
{ "values": ["dimdescent:dark_iron_bars"] }
```
No `"replace"` key needed (defaults to `false` = merge). This is how a block gets gated to "needs a
diamond pickaxe minimum" (paired with `.requiresCorrectToolForDrops()` on the block itself) or
gets pickaxe mining-speed recognition (`data/minecraft/tags/block/mineable/pickaxe.json`) without
forking vanilla's tag definitions. Verify vanilla's actual tier-gated block list first (e.g.
`obsidian`'s tags) before assuming which tag enforces what - `needs_diamond_tool` is what makes a
pickaxe below diamond simply fail to drop anything, `mineable/pickaxe` is a separate "pickaxe is
the efficient tool" recognition tag.

## `Item.appendHoverText` for a custom tooltip line (1.21.1 signature)

```java
@Override
public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
    tooltipComponents.add(Component.translatable("item.dimdescent.datura_seeds.tooltip")
        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
}
```
Note the second parameter is `Item.TooltipContext` (nested type), not a bare `Level` or a
top-level `TooltipContext` class - an easy thing to get wrong from stale training-data memory of
older MC versions where this method's signature was different.

## Generating placeholder textures

No image-generation tool is available in this environment, but Python 3 + Pillow (PIL) is
installed and usable for quick procedural placeholder textures (flat colors, borders, alpha
cutouts) via a throwaway script - confirmed working with `python3 -c "import PIL; print(PIL.__version__)"`.
Good enough for functional placeholders (proving the transparency/render-layer pipeline works);
swap for real hand-painted art later. Note: the Bash tool's Git-Bash environment and Windows-native
Python don't share the same view of `/tmp` - write scripts/read outputs using actual Windows paths
(the project's own scratchpad directory, or resolve with `cygpath -w`) so both sides can see the
same files.
