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

## Generating placeholder textures

No image-generation tool is available in this environment, but Python 3 + Pillow (PIL) is
installed and usable for quick procedural placeholder textures (flat colors, borders, alpha
cutouts) via a throwaway script - confirmed working with `python3 -c "import PIL; print(PIL.__version__)"`.
Good enough for functional placeholders (proving the transparency/render-layer pipeline works);
swap for real hand-painted art later. Note: the Bash tool's Git-Bash environment and Windows-native
Python don't share the same view of `/tmp` - write scripts/read outputs using actual Windows paths
(the project's own scratchpad directory, or resolve with `cygpath -w`) so both sides can see the
same files.
