# Block robustness checklist

Standing instruction from the user: new blocks should interact correctly with the rest of the
game (explosions, pistons, fire, tools, loot) by default, the same way any vanilla block does -
not because someone tested every case by hand, but because the properties were set deliberately
and checked against real vanilla precedent up front. The goal is that the user shouldn't have to
be the one who discovers "oh, breaking this block drops nothing" or "a piston can't push this and
I don't know why" days later. When adding a new block, run down this list - most items are a
single `BlockBehaviour.Properties` call, cheap to get right the first time.

## The checklist

1. **Loot table.** Every block needs either a `data/<modid>/loot_table/blocks/<name>.json` or an
   explicit `.noLootTable()` on its `Properties`. There is no safe default - a block with neither
   silently drops nothing when broken, with no error/warning to catch it. This is easy to miss
   because it doesn't show up at compile time OR in the `runClient` boot log; it only manifests as
   "I broke this in survival and got nothing back," which reads as a bug even when it might have
   been intentional. If the block should drop itself (most blocks), copy the vanilla self-drop
   loot table pattern:
   ```json
   { "type": "minecraft:block", "pools": [{ "bonus_rolls": 0.0,
     "conditions": [{ "condition": "minecraft:survives_explosion" }],
     "entries": [{ "type": "minecraft:item", "name": "<modid>:<name>" }], "rolls": 1.0 }],
     "random_sequence": "<modid>:blocks/<name>" }
   ```
   If the block is genuinely meant to drop nothing (found this out the hard way with Nullstone),
   call `.noLootTable()` explicitly so the no-drop reads as a deliberate choice, not a gap.

2. **Explosion resistance.** Set via `.strength(hardness, explosionResistance)` (or `.strength(x)`
   for both equal, or `.instabreak()` for both zero). Ask: should TNT/creepers actually be able to
   destroy this? A block meant to be a hard boundary (like Forsaken Fiber) needs bedrock-tier
   resistance (`-1.0F, 3600000.0F`), not just a high mining hardness - those are two independent
   numbers and it's easy to set one and forget the other.

3. **Piston push reaction - usually free, verify before adding anything manually.** Checked
   `PistonBaseBlock.isPushable` directly: a block with `getDestroySpeed() == -1.0F` (i.e.
   `strength(-1.0F, ...)`, same as bedrock) is **already** unpushable with no extra property
   needed - the check happens before `getPistonPushReaction()` is even consulted. Don't reflexively
   add `.pushReaction(PushReaction.BLOCK)` to every unbreakable block; it's redundant with `-1`
   hardness and only needed for blocks that are breakable-but-still-shouldn't-move. Do explicitly
   set `.pushReaction(PushReaction.DESTROY)` for decorative/non-solid blocks (flowers, etc.) that
   should just pop off rather than shove around, matching vanilla's own flower behavior.

4. **Tool tier gating.** If a block should require a minimum tool tier (diamond pickaxe, etc.),
   two things are both required together, not just one: `.requiresCorrectToolForDrops()` on the
   block's `Properties`, AND membership in the matching `data/minecraft/tags/block/needs_*_tool.json`
   tag (additive - ship a small file with just your block's ID, see
   `blocks-doors-models.md`). Also add it to the relevant `mineable/<tool>.json` tag so the correct
   tool gets its mining-speed bonus recognized.

5. **Fire/flammability.** If a block is wood-like or should burn/spread fire, register it via
   `FireBlock.setFlammable(block, encouragement, flammability)` (not covered yet in this codebase -
   revisit if a flammable block is ever added). If a block should explicitly resist fire spread
   (like a metal/stone block), the vanilla default of "not flammable" already covers it with zero
   extra work - don't add anything unless the block actually needs to burn.

6. **Waterlogging, if the block can sensibly be submerged.** Vanilla's `IronBarsBlock`
   (`CrossCollisionBlock`) already has a `WATERLOGGED` property and handles it correctly via
   `getFluidState`/`updateShape` - reusing the vanilla class (see `blocks-doors-models.md`) gets
   this for free. Writing a *new* block class from scratch that a player might place underwater
   and not accounting for waterlogging is the kind of gap this checklist exists to catch.

7. **Reuse the vanilla class when the mechanical behavior should be identical**, rather than
   hand-rolling placement/shape/connection logic that vanilla already solved correctly (see the
   "Reuse vanilla's actual Block/Item Java classes" section in `blocks-doors-models.md`). Every
   category of bug this checklist exists to catch (piston edge cases, waterlogging, shape
   correctness) is something vanilla's own class has almost certainly already handled - don't
   reintroduce the risk by reimplementing it.

## Applying this retroactively

When this checklist was written, auditing the mod's existing blocks against it caught two real
gaps immediately: `rift_door` and `nullstone` both had neither a loot table nor `.noLootTable()`.
For the door this was an actual bug (breaking a placed door silently destroyed the item instead of
returning it, unlike every vanilla door); for Nullstone it was a missing explicit no-drop opt-out.
Worth doing a quick pass over this list any time a new block is added, not just for brand new ones.
