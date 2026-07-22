# Finding ground-truth API source

Minecraft modding APIs churn heavily between versions - method names, constructor signatures,
even whole architectural patterns (like how portals/dimensions/rendering work) change from one
minor version to the next. Training data about "how NeoForge modding works" is frequently stale
or describes the wrong version. Rather than guess and iterate via compile errors, it's faster and
more reliable to pull the actual decompiled source for this exact project's MC/NeoForge version
straight out of the Gradle cache and read it.

This works because ModDevGradle (the Gradle plugin this project uses) downloads and decompiles
vanilla + NeoForge source as part of setting up the dev environment, and caches the results
locally. As long as the project has been synced/built at least once, these jars exist on disk.

## The jars, and what's in each

**Decompiled vanilla + NeoForge source** (the big one - use this first for almost everything):
```
~/.gradle/caches/neoformruntime/intermediate_results/sourcesAndCompiledWithNeoForge_<hash>_output.jar
```
Find it with:
```bash
find ~/.gradle/caches/neoformruntime/intermediate_results -maxdepth 1 -iname "sourcesAndCompiledWithNeoForge*"
```
Contains full `.java` source (not just `.class`) for vanilla Minecraft classes patched with
NeoForge's changes - e.g. `ServerPlayer.java`, `DoorBlock.java`, `RenderType.java`,
`EndPortalBlock.java`, `DimensionTransition.java`. This is where you go to verify exact method
signatures, field visibility, and see real reference implementations of vanilla features you want
to imitate (a custom door, a custom portal, a custom block entity renderer, etc).

**Vanilla client jar** (for extracting actual game assets as reference - JSON blockstates/models,
shader files, resource-location layouts):
```
~/.gradle/caches/neoformruntime/artifacts/minecraft_<mc_version>_client.jar
```
Example: `minecraft_1.21.1_client.jar`. Contains the full `assets/minecraft/...` and
`data/minecraft/...` tree exactly as shipped - blockstates, models, shader `.json`/`.vsh`/`.fsh`
files, lang files, textures. Use this to get vanilla's *exact* JSON structure/field names
(e.g. dimension_type fields, or a door's blockstate rotation table) instead of guessing at the
schema - then diff your own file against the extracted original programmatically rather than
eyeballing a long JSON file for transcription errors.

**NeoForge sources jar** (for NeoForge-specific classes not in the vanilla+NeoForge combined jar -
mostly event classes):
```
~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/<version>/<hash>/neoforge-<version>-sources.jar
```
Find the hash with:
```bash
find ~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge -iname "*sources*.jar"
```
Has things like `RegisterShadersEvent.java`, `EntityRenderersEvent.java`,
`RegisterCommandsEvent.java` with full doc comments explaining which bus they fire on and when.

**FancyModLoader jar** (compiled classes only, no sources - for FML-level annotation classes that
aren't in the above two, like `@EventBusSubscriber` itself):
```
~/.gradle/caches/modules-2/files-2.1/net.neoforged.fancymodloader/loader/<version>/<hash>/loader-<version>.jar
```
Find it with:
```bash
find ~/.gradle/caches -iname "*.jar" | grep -v sources | grep -v javadoc | \
  xargs -I{} sh -c 'unzip -l "{}" 2>/dev/null | grep -q "SomeClassName.class" && echo {}'
```
(swap `SomeClassName.class` for whatever you're hunting). Since there's no source jar for this
one, use `javap -v SomeClass.class` on the extracted `.class` file to inspect method signatures,
annotation defaults, etc. This is how we confirmed `@EventBusSubscriber`'s `bus` field defaults to
`GAME` and that specifying `bus` explicitly is deprecated (`javap -v` shows the `AnnotationDefault`
and `Deprecated` bytecode attributes directly).

## General technique

```bash
# find which jar has the class you want
unzip -l <jar> | grep -i "ClassName"

# extract just the .java source (or .class if no source available)
mkdir -p /tmp/mcsrc && cd /tmp/mcsrc
unzip -o -q <jar> "net/minecraft/path/to/ClassName.java"

# then read it with the Read tool, or grep for the specific method/field you need
```

Note: the Bash tool's `/tmp` is Git Bash's temp dir, not directly usable by Windows-native tools
(Read, PowerShell, Python). Get the real Windows path with `cygpath -w <path>` or just check
`pwd -W` after `cd`-ing there, before handing a path to a Windows-side tool.

## Worked examples from this project

- Confirmed `ServerPlayer.changeDimension(DimensionTransition)` is the real teleport entry point
  (not some other method name from stale memory) by grepping `ServerPlayer.java` for
  `changeDimension` and `DimensionTransition`.
- Confirmed `ResourceLocation.fromNamespaceAndPath(namespace, path)` is the current factory method
  (constructors are private/removed in recent versions) by grepping `ResourceLocation.java`.
- Found the exact 7-argument `RenderType.create(...)` overload used by vanilla's
  `RenderType.END_PORTAL` by reading `RenderType.java` directly, rather than guessing parameter
  order.
- Extracted vanilla's `overworld.json`/`the_end.json` dimension_type files and `iron_door.json`
  blockstate from the client jar as ground truth for our own equivalents, then diffed our copies
  against the originals with a small Python script instead of manually re-reading a 32-entry JSON
  table for typos.
