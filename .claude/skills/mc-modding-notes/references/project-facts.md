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
- **Spawn platform**: since the flat generator's floor is an insta-break void block everywhere, a
  10x10x1 stone-brick patch is stamped in imperatively (plain `level.setBlock` calls, not real
  worldgen) around the rift's fixed spawn point the first time anyone arrives there - see
  `RiftTeleporter.ensureSpawnPlatform`. Not a permanent solution; revisit once real room
  generation/placement is being designed.
- **Rift door block** (`dimdescent:rift_door`): extends vanilla `DoorBlock`, implements `Portal`
  for bidirectional overworld<->rift teleport, has a `BlockEntityRenderer` drawing a re-themed
  (red/orange) End-Portal-style shader effect through transparent window cutouts in its texture -
  small 4-window "peekaboo" boxes while closed, one big box filling the doorway once open. Hinge
  is forced to always be LEFT regardless of placement context (this is a special door, not meant
  to behave like a real one). The teleport hitbox is intentionally scoped to just the visible glow
  box, not the door's whole 1x1x2 cell - see the `entityInside`-fires-for-the-whole-cell gotcha in
  `dimensions-teleportation-portals.md`. See also `rendering-shaders-blockentities.md` and
  `blocks-doors-models.md`.
- **Shared teleport logic** lives in a `RiftTeleporter` helper class, used by both the `/rift
  enter|leave` debug command and the door block's `Portal.getPortalDestination` - avoid
  duplicating dimension-selection logic across entry points.
- **Door pairing** (`RiftDoorLinkData`, a `SavedData` on the overworld): there is exactly ONE
  generated exit door in the rift, ever - not one per overworld door. Walking through any door
  from outside the rift always lands at that same shared door (generated once, first time it's
  needed); what makes the return trip go to the right place is a per-player record of "which door
  did this specific player most recently enter from," not a per-door-position link. A door placed
  by hand inside the rift isn't that shared door, so it always falls back to the default
  overworld-spawn exit and never gets tracked. Don't reintroduce a per-overworld-door generated
  exit - that was tried and explicitly walked back (see git history around the door-pairing
  commits) because it doesn't match the intended "one to one, always the same door" behavior.
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
  `blocks-doors-models.md`). Not yet spawned via world-gen; give/creative only.
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
