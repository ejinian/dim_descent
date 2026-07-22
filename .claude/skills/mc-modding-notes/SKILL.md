---
name: mc-modding-notes
description: Living knowledge base of hard-won NeoForge/Minecraft 1.21.1 modding findings for the dim_descent project - decompiled-source lookup paths, testing/verification without visual access to the game, and gotchas around custom dimensions, portals, block entity renderers, custom shaders, door blocks, and render layers. Consult this skill for ANY Minecraft/NeoForge modding task in this repo - adding blocks, items, dimensions, rendering, textures, commands, or debugging why something doesn't render/register/behave as expected - even if the user doesn't name the skill. Check it BEFORE re-deriving an API signature from memory or debugging a rendering/registration issue from scratch, since the answer may already be recorded here. ALWAYS append newly-learned, non-obvious findings back into this skill's reference files after solving something the hard way, so the next session doesn't have to re-discover it.
---

# dim_descent modding notes

This is a **project-specific, continually-growing** knowledge base for building the dim_descent
NeoForge mod. It exists because Minecraft modding APIs churn every version, official docs lag
behind, and a lot of the real answers only exist in decompiled source or by trial and error in
this exact repo. Every time we burn effort figuring something out the hard way, it belongs here -
otherwise the next session (or the next hour, after context compaction) pays that cost again.

**If you read this skill and then learn something new or surprising while working** - a gotcha,
an API signature that didn't match training-data assumptions, a technique that took real digging
to find - add it to the relevant reference file below (or create a new one) before you finish the
task. Treat this as mandatory bookkeeping, not an afterthought. A finding that isn't written down
here didn't really get learned.

## Project facts

- Minecraft 1.21.1, NeoForge 21.1.241, Java 21, ModDevGradle-based MDK
- mod_id: `dimdescent`, base package: `com.ejinian.dimdescent`
- Full details, naming history, and conventions: [references/project-facts.md](references/project-facts.md)

## Quick-reference (the gotchas most likely to bite again immediately)

1. **Don't guess API signatures from training data - decompiled source is one `unzip` away.**
   MC/NeoForge APIs change every version; verify against the actual jars in the Gradle cache
   before writing code that calls them. See
   [references/finding-ground-truth-source.md](references/finding-ground-truth-source.md).

2. **Java code changes need a full client restart; JSON/texture changes don't.**
   Resource-only changes hot-reload in-game with F3+T. Any `.java` change requires killing the
   dev client and re-running `runClient` - **do this automatically, don't ask first** (standing
   instruction from the user). Full verification workflow (and what we *can't* verify without the
   human's eyes) is in
   [references/testing-and-verification.md](references/testing-and-verification.md).

3. **A block with transparent (alpha=0) texture pixels still renders them as opaque black unless
   you explicitly set its render layer to `RenderType.cutout()`.** The default "solid" layer
   ignores alpha entirely. See the render-layer section in
   [references/rendering-shaders-blockentities.md](references/rendering-shaders-blockentities.md).

4. **`@EventBusSubscriber(bus = ...)` is deprecated - just omit `bus` entirely** and let it
   auto-infer GAME vs MOD from the event type. Same file as above.

5. **Custom dimensions are static datapack JSON, not runtime-created pocket dimensions** like the
   original Dimensional Doors did it. See
   [references/dimensions-teleportation-portals.md](references/dimensions-teleportation-portals.md).

6. **Never copy Mojang's actual texture/asset files into our mod's resources** - that's
   redistributing their copyrighted art, a real EULA/IP problem. Author original placeholder art
   instead (Python + Pillow is available in this environment for quick procedural textures), or
   reference their asset by resource location without duplicating the file. Details in
   [references/blocks-doors-models.md](references/blocks-doors-models.md).

7. **Commit and push after any significant change - don't wait to be asked.** Standing instruction
   from the user. "Significant" means a working feature/fix landed and was verified (compiled,
   client booted clean), not every intermediate edit mid-debugging. Write a real commit message
   (what changed and why), not a placeholder.

## Reference files

| File | Covers |
|---|---|
| [finding-ground-truth-source.md](references/finding-ground-truth-source.md) | Exact Gradle-cache jar paths for decompiled vanilla + NeoForge + FancyModLoader source; how to extract and read a class |
| [testing-and-verification.md](references/testing-and-verification.md) | What we can verify ourselves (compile, log-grepping) vs. what needs the human in-game; restart rules; EULA caution; don't kill an active play session without asking |
| [dimensions-teleportation-portals.md](references/dimensions-teleportation-portals.md) | Data-driven dimension JSON structure, `DimensionTransition`/`changeDimension`, the `Portal` interface pattern for custom portal blocks |
| [rendering-shaders-blockentities.md](references/rendering-shaders-blockentities.md) | Custom `BlockEntityRenderer` + custom core shader technique (how vanilla's End Portal effect actually works and how to re-theme it), `@EventBusSubscriber` bus rules, the render-layer/cutout gotcha |
| [blocks-doors-models.md](references/blocks-doors-models.md) | Reusing vanilla's shared door model templates instead of authoring geometry from scratch, `DoubleHighBlockItem`, door hinge-selection heuristic, texture/IP caution |
| [project-facts.md](references/project-facts.md) | Mod identity, naming history, conventions specific to this repo |

## How to add a new finding

Pick the reference file that matches the topic (or create a new one and add a row to the table
above). Write it the same way the existing entries are written: what the surprising behavior was,
why it matters, and the concrete fix/API - not just "watch out for X." Include file paths, class
names, and code snippets where relevant, so a future read can act on it immediately without
re-deriving anything.
