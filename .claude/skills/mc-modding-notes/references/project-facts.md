# Project facts

- **Game/modding stack**: Minecraft 1.21.1, NeoForge 21.1.241, Java 21, ModDevGradle (Gradle
  plugin `net.neoforged.moddev`), scaffolded from the official NeoForge MDK template.
- **mod_id**: `dimdescent`
- **base package**: `com.ejinian.dimdescent`
- **repo**: `E:\ProgrammingProjects\MinecraftMods\dim_descent`, GitHub remote
  `github.com/ejinian/dim_descent`
- Renamed off the MDK's default `examplemod` / `com.example.examplemod` scaffold naming (the
  template ships with that as a placeholder - always check gradle.properties/package names
  haven't been left as the template defaults on a fresh scaffold).

## Concept (see repo's own `CLAUDE.md` for the full pitch)

A Dimensional-Doors-inspired liminal-horror dungeon-exploration mod, but built around **depth** as
a first-class mechanic - a legible "how deep am I" push-your-luck loop, with difficulty/loot
scaling by depth, rather than DD's flat pool of scary rooms. Key differentiators from DD: depth as
a visible signal (fog/sound/color grading), depth-tiered enemies, procedurally-composed room
variety (hundreds of non-repeating rooms rather than a finite pool), intentional rift placement.

## Structural decisions made so far

- **Custom dimension** (`dimdescent:rift`): data-driven via `data/dimdescent/dimension_type/rift.json`
  + `data/dimdescent/dimension/rift.json`. No skylight, has a ceiling, fixed night-time lighting,
  flat generator with `dimdescent:forsaken_fiber` (bottom, unbreakable boundary) and
  `dimdescent:nullstone` (the walkable "floor," insta-break void look) as its layers. See
  `dimensions-teleportation-portals.md` for the general technique.
- **Room grid** (`NullDomainRooms`): the Null Domain is a Dimensional-Doors-style pocket dungeon,
  NOT a flat platform. Rooms are stamped imperatively (plain `level.setBlock`, not worldgen) on a
  coarse grid - `SPACING = 512` blocks per cell, mirroring DimDoors' 32-chunk `pocketGridSize` -
  keyed by a monotonic integer index. Index -> cell is a square SPIRAL out from origin
  (`spiralCell`, an O(1) closed form verified bijective against a brute-force spiral walk), so rooms
  fan into both axes and stay within ~sqrt(N)/2 cells of origin (tightest collision-free packing)
  rather than marching off one axis. Collision-safety is NOT from the layout - it's the single
  global monotonic index (`GridData.takeNextIndex`), so two players at once or the same player across
  the world's whole history never get the same cell. Each room is a pitch-black box: interior faces
  of walls + ceiling lined with Nullstone (dead black), backed by an unbreakable Forsaken Fiber shell
  one block further out/up (so it's black yet unbreachable in survival), altar-brick floor with
  Daemonlight lighting, type-specific decor, and ONE onward Dream Bed against the far wall. Crucially
  there is NOTHING under the floor - the altar bricks sit directly over the void, so breaking one
  (creative) drops you out of the world; the dimension's flat generator was emptied to `"layers": []`
  to remove the old ground far below. Five code-generated `RoomType`s (PILLAR_HALL, LONG_GALLERY,
  GRAND_CHAMBER, CRAMPED_CELLS, HALL_OF_BARS) picked uniformly; three can carry a loot chest
  (`RandomizableContainerBlockEntity.setLootTable` -> `dimdescent:chests/altar`). The next index is
  persisted in a `GridData extends SavedData` on the rift level. Generation is LAZY (on door entry),
  like DimDoors' `LazyPocketGenerator`. Deliberately dropped from DimDoors for this POC: their
  authored `.schem` room pool and their depth axis (`VirtualLocation.depth`) - selection is a flat
  uniform pick with no depth weighting yet.
- **Dream Bed** (`dimdescent:dream_bed`, `DreamBedBlock`): the retirement of the Rift Door (whose
  block/BE/portal-renderer/`RiftDoorLinkData`/`DoorLocation` are all DELETED) and the Null Domain's
  onward-travel device. Extends vanilla `BedBlock` purely to inherit the two-part shape/placement/
  collision, but:
  - `getRenderShape` -> `RenderShape.MODEL` and `newBlockEntity` -> `null`, so it renders a normal
    JSON block model (custom `dream_bed_foot`/`_head` models, gray tattered zero-saturation textures)
    instead of vanilla's bed-entity renderer. This is the trick for "a bed with my own texture"
    without a `BedBlockEntity` + custom `BlockEntityRenderer`.
  - `useWithoutItem` is fully overridden: in the rift -> `RiftTeleporter.toNextRoom` + `changeDimension`
    (same-dim teleport works; `ServerPlayer.changeDimension` short-circuits to a reposition when
    `newLevel == current`); anywhere else -> vanilla's wrong-dimension bed explosion
    (`removeBlock` both halves + `level.explode(..., badRespawnPointExplosion(center), ..., 5.0F, true, BLOCK)`),
    reached in EVERY non-Domain dimension (overworld included), not just where `bedWorks()` is false.
    Sleeping is impossible because we never call `startSleepInBed`.
  - Gotcha: `BedBlock.codec()` is invariant `MapCodec<BedBlock>`, so a subclass can't narrow it to
    `MapCodec<DreamBedBlock>`. Type the `simpleCodec(DreamBedBlock::new)` field as `MapCodec<BedBlock>`
    (inference picks `B=BedBlock` from the target type; the factory still builds DreamBedBlock).
  - Gotcha: stamping the two halves in code, use `setBlock(pos, state, 2)` (no neighbour updates) for
    BOTH halves, or vanilla `BedBlock.updateShape` self-deletes a half whose partner isn't placed yet.
  - Unbreakable (`strength(-1, 3600000)`, `noLootTable`, `pushReaction(BLOCK)`) so a player can never
    mine away their own way onward; its own detonation uses `removeBlock`, bypassing blast resistance.
  - Display name is **"Nexus of Eternal Slumber"** (lang only; block id stays `dream_bed`, don't
    rename the id - placed blocks would orphan). Full-bed inventory icon comes from a dedicated
    `dream_bed_inventory` model (a whole bed compressed into one 16^3 cell); the in-world foot/head
    models stay separate. The item still isn't a vanilla-style BEWLR, just a static model.
  - Overworld detonation is currently gated OFF by `EXPLODE_OUTSIDE_DOMAIN = false` (temporary, so
    accidental clicks don't wreck overworld builds while rooms are being authored). Flip to restore.
- **The three beds** all extend a shared `NexusBedBlock extends BedBlock` base (which supplies
  `RenderShape.MODEL` + null block entity + a `resolveHead` helper, and never calls `startSleepInBed`):
  - `dream_bed` (dark) — deeper. `pale_dream_bed` (pale, SAME display name) — back one room, and it
    is also the room's ENTRANCE: players land beside it (`NullDomainRooms.landingFor`), so it doubles
    as the spawn marker and supplies arrival facing. `corrupted_bed` — what an overworld bed becomes
    after a player refuses the trip: breakable (0.2) but `noLootTable`, un-sleepable (narrated line),
    no creative entry, and IS in `#minecraft:beds` so it stays a valid respawn point.
  - `BedLinkData` (SavedData on the rift level) keys travel on BEDS, not players - this is what makes
    the Domain multiplayer. `forward`: BedKey -> room index (this bed opens this room, forever, for
    everyone). `back`: that room's pale-bed BedKey -> the bed that opened it. Both written once when a
    room is minted. `BedKey` = (dimension, pos) normalised to the HEAD half; identity is the PLACE, so
    breaking and replacing a bed on the same spot keeps its destination. A previous design stored a
    per-player room chain instead, which made the Domain single-player in all but name (two people in
    the same bed got different rooms and could never meet) - it was deleted. Don't reintroduce
    per-player routing. Room landings are recomputed from the index (`roomTypeFor` re-draws the FIRST
    value off the same seed), so no per-room position is stored; `entranceBedHead(index)` must stay in
    exact agreement with `placeEntranceBed`.
  - `NexusReturn.refuseTrip` is the outermost-room exit: corrupt the origin bed, place the player 3-6
    blocks away on a heightmap-checked standable spot, teleport, then remove Attunement and apply the
    comedown. Order matters - teleport FIRST, then strip Attunement, or `RiftEjectionEvents` races it.
    `ServerPlayer.changeDimension` keeps the same instance across dimensions (verified in source), so
    applying effects to the same reference afterwards is safe.
- **No bespoke authoring blocks ship.** A `dimdescent:spawn_marker` block was briefly added to mark
  room spawn points, then DELETED on the principle that a level-editor block sitting in the creative
  menu is coupling that doesn't belong in a released mod. Room spawn/facing is instead derived from
  blocks already in the room (see the DimDoors entrance note in
  `dimensions-teleportation-portals.md`). Don't reintroduce an authoring-only block.
- **Shared teleport logic** lives in a `RiftTeleporter` helper class, used by the `/rift enter|leave`
  debug command, the sleep crossing (`SleepEntryEvents`), and the Dream Bed - avoid duplicating
  dimension-selection logic across entry points. Leaving the Null Domain happens two ways only: the
  manual `/rift leave`, and Attunement expiry (`RiftEjectionEvents` ejects to the respawn point the
  tick the effect ends). A voluntary exit is a separate, not-yet-built item.
- **Nullstone** (`dimdescent:nullstone`): Dimensional Doors' "Fabric of Reality" equivalent -
  insta-break (`Properties.instabreak()`), pure uniform `(0,0,0)` black texture (explicitly no
  noise/variation - a black texture stays black under every one of Minecraft's per-face lighting
  multipliers, satisfying "zero reflection from light" without needing emissive/fullbright
  rendering tricks).
- **Forsaken Fiber** (`dimdescent:forsaken_fiber`): Dimensional Doors' "Ancient Fabric" equivalent
  - unbreakable (`strength(-1, 3600000F)`, `.noLootTable()`, `.isValidSpawn(Blocks::never)`, same
  as vanilla bedrock), animated texture (dark maroon base, a barely-visible dark-orange vein
  pattern that actually scrolls across frames - see the animated-texture note in
  `blocks-doors-models.md`).
- **Dark Iron Bars** (`dimdescent:dark_iron_bars`): Attunement Gate ruin material - reuses
  vanilla's `IronBarsBlock` class directly (same connection/waterlogging logic, new `Properties`),
  obsidian-tier hardness (`requiresCorrectToolForDrops()` + `needs_diamond_tool`/`mineable/pickaxe`
  tag membership - both required together, see `block-robustness-checklist.md`), original
  criss-cross lattice texture with real alpha gaps (not vanilla's actual texture - copyright, see
  `blocks-doors-models.md`). Placed as Null Domain room decor (cage rings, bar screens, cell
  gateposts - see `NullDomainRooms`); still has no world-gen spawn or recipe, so it's give/creative
  only as an item.
- **Datura** (`dimdescent:datura`) + **Datura Seeds** (`dimdescent:datura_seeds`): a `FlowerBlock`
  (`SuspiciousStewEffects.EMPTY`, no custom class needed) with an original white-trumpet-flower
  texture. Breaking it without Silk Touch drops 1-2 Datura Seeds instead of the plant itself
  (Silk Touch drops the plant); seeds carry a lore-flavored tooltip nudging toward brewing. Not
  yet spawned via world-gen; give/creative only.
- **Potion of Attunement** (`dimdescent:attunement` potion + `dimdescent:attunement` MobEffect):
  brewed via `Awkward Potion + Datura Seeds`, registered through NeoForge's
  `RegisterBrewingRecipesEvent`/`PotionBrewing.Builder.addMix` (GAME bus, not MOD bus - that event
  doesn't implement `IModBusEvent` despite the "Register" name). Redstone extends it to
  `dimdescent:long_attunement` (3600 -> 9600 ticks, matching vanilla's exact ratio for every other
  awkward-derived potion) - it deliberately reuses the base potion's `name` field rather than a
  `"long_"`-prefixed one, matching how vanilla itself has no separate `long_night_vision` lang key
  either. `MobEffect`'s constructor is `protected`; needed a thin subclass
  (`AttunementMobEffect`) to get a public one. The lethality mechanic itself (dying without the
  effect active in a rift) isn't implemented yet - this is just the potion/effect registration and
  brewing recipe.
- **First-brew thunderclap** (`AttunementBrewingEvents`, `FirstAttunementBrewData`): the very
  first time Potion of Attunement (either variant) is ever completed in a world,
  `PotionBrewEvent.Post` triggers `ServerLevel.setWeatherParameters` (instant storm) plus a real
  (`setVisualOnly(true)`) `LightningBolt` entity spawned above each connected player - this gets
  both the sky-flash (`Level.setSkyFlashTime`, client-side) and the thunder+impact sound bundled
  together for free from vanilla's own `LightningBolt.tick()`, rather than faking either
  separately. A `SavedData` flag on the overworld (`FirstAttunementBrewData`) makes sure it only
  ever fires once, persisted across restarts.

## Naming decisions made

Chosen and implemented (see also the `block-naming-fabric-analogues` memory entry, which has the
same info for cross-session recall outside this repo): **Nullstone** = Fabric of Reality
equivalent, **Forsaken Fiber** = Ancient Fabric equivalent.
