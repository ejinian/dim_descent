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
  flat stone-floor generator (guaranteed landing platform, not void). See
  `dimensions-teleportation-portals.md` for the general technique.
- **Rift door block** (`dimdescent:rift_door`): extends vanilla `DoorBlock`, implements `Portal`
  for bidirectional overworld<->rift teleport, has a `BlockEntityRenderer` drawing a re-themed
  (red/orange) End-Portal-style shader effect through transparent window cutouts in its texture.
  See `rendering-shaders-blockentities.md` and `blocks-doors-models.md`.
- **Shared teleport logic** lives in a `RiftTeleporter` helper class, used by both the `/rift
  enter|leave` debug command and the door block's `Portal.getPortalDestination` - avoid
  duplicating dimension-selection logic across entry points.

## Naming/flavor decisions pending or in discussion

- Dimensional Doors' "Fabric of Reality" (insta-break void-look floor block) and "Ancient Fabric"
  (unbreakable dungeon-boundary block, bedrock-equivalent) need our own equivalents. Direction as
  of the last discussion: tie the void-floor block's look to the depth mechanic rather than flat
  black; boundary block should be mechanically identical to bedrock
  (`Block.Properties.strength(-1, 3600000F)`-style unbreakable) with original flavor/name (ideas
  floated: "Frayed Bedrock", "Seam Stone") and a reddish-orange flowing/veiny texture - not yet
  implemented as of this writing.
