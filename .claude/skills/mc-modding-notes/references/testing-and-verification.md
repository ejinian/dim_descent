# Testing and verification

We have **no way to see or interact with the native Minecraft client window** - only browser
automation tools are available in this environment, and those don't apply to an LWJGL desktop
window. This caps how much we can verify on our own. Be upfront with the user about this ceiling
rather than claiming "it works" when only compilation/log-checking was actually confirmed.

## What we CAN verify ourselves

1. **Compile.** `./gradlew.bat compileJava` catches Java-level errors (bad imports, wrong method
   signatures, type errors). Always do this before anything else after writing code.

2. **Boot the client and check the log.** Launch `runClient` in the background, then grep the log
   for errors:
   ```bash
   ./gradlew.bat runClient --console=plain > /tmp/runclient.log 2>&1 &
   # ...then poll (don't sleep-chain; issue a fresh Bash call each time)...
   grep -n -i -E "ERROR|Exception|missing model|does not exist" /tmp/runclient.log
   ```
   A clean boot with no errors around "Reloading ResourceManager" / texture atlas stitching /
   shader loading is decent evidence that block models, blockstates, dimension JSON, and shaders
   are at least syntactically valid and resolvable. It does NOT confirm they *look* or *behave*
   right in 3D - only that nothing crashed loading them.

3. **Diff generated JSON against a known-good vanilla original programmatically** (see
   `finding-ground-truth-source.md`) instead of eyeballing long files for typos.

## What we CANNOT verify ourselves - be explicit about this

- Whether a texture/model actually looks right (color, alignment, proportions).
- Whether a custom shader/`BlockEntityRenderer`'s geometry is positioned correctly in 3D.
- Whether gameplay behavior (teleport landing spot, door swing direction, mob spawning) feels
  right.
- Whether a placed block interacts correctly with neighboring blocks in a live world.

For all of the above, the human needs to actually load the game and test, then report back
(ideally with a screenshot). Say so plainly instead of overclaiming "verified" or "tested."

## `runGameTestServer` is a dead end for validating dimension/registry JSON

It looked like a promising way to headlessly validate a custom dimension without a GUI, but it
fails immediately with `IllegalArgumentException: No test functions were given!` before it even
gets to loading world/dimension registries - unless the project actually has `@GameTest`-annotated
test methods registered. Don't bother with it for this purpose unless we actually build out a
gametest suite later.

## `runServer` IS the tool for validating worldgen - but never accept the EULA for the user

Booting `./gradlew runServer` loads the datapack registries and generates spawn, which is the one
way to validate worldgen JSON headlessly from here (see the worldgen note below). The ONE caution:
the first run needs `eula=true` in `run/eula.txt`, and writing that is a real agreement-acceptance
action - do NOT create/accept it on the user's behalf. Only run the server if `run/eula.txt` already
says `eula=true` (it will, once the user has ever launched a server/world); if it doesn't, ask first.
Not for validating simple resources (models/textures/blockstates) - a client boot covers those.

## Java changes need a full restart; resource/JSON changes don't

- **Any `.java` change** (a new block class, a new event handler, a shader-registration tweak):
  the dev client must be fully killed and relaunched via `runClient`. There is no hot-reload path
  for compiled code in a running client.
- **Resource-only changes** (block/item textures, models, blockstates, lang files, shader `.vsh`/
  `.fsh`/`.json`, loot tables, recipes, dimension JSON that's already loaded... mostly) can often
  be picked up in-game with F3+T (Reload Resource Packs) without restarting - though data-driven
  world-gen registries (like a `dimension`/`dimension_type` JSON) are only read at world-load
  time, so those specifically need a fresh world load (not necessarily a full client restart) to
  pick up changes.
  - **F3+T caveat (bit us):** the running dev client serves the mod's resources from
    `build/resources/main`, NOT from `src/main/resources`. F3+T reloads from that build directory.
    A `.java` change triggers a full build (which re-runs `processResources`), so restart-driven
    workflows always have fresh resources - but editing *only* a texture/JSON and pressing F3+T
    shows the OLD asset, because nothing copied the edit into `build/resources/main`. Fix: run
    `./gradlew processResources` after the edit, THEN F3+T. Confirm with an md5 of the src vs build
    copy of the file if unsure. (Symptom: "I changed the texture but the game still shows the old
    one" with no error anywhere.)

To restart the client from this environment:
```bash
tasklist //FI "IMAGENAME eq java.exe" 2>/dev/null   # find candidate PIDs
taskkill //PID <pid> //F                             # kill the stale client
./gradlew.bat runClient --console=plain > /tmp/runclient.log 2>&1 &   # relaunch
```

## Standing instruction: always kill and relaunch automatically after a Java change

The user has explicitly said not to ask each time - after any `.java` change, just find the
existing `runClient` process and kill it, then relaunch, without pausing to check first. Don't
revert to "want me to restart it, or are you handling that?" - that question has already been
answered once and for all. Do the restart, then tell the user it's done (so they know to switch
back to the game window), rather than asking permission before doing it.

This doesn't extend to anything more destructive than killing the dev client process itself - it
doesn't authorize touching world save files, `eula.txt`, or anything outside of "restart the
client so new code takes effect."

**This applies even if the user is visibly mid-session** (just sent a screenshot from inside the
game, clearly mid-testing something). The general instinct to check before force-killing a process
someone might be actively using does not apply here - the user has explicitly confirmed, more than
once, that speed of iteration matters more than protecting an in-progress play session. Restart
immediately, don't pause to ask "are you still testing?" first.

## Worldgen/datapack JSON only validates at WORLD LOAD, not client boot

A clean boot to the title screen says NOTHING about worldgen datapack files
(`worldgen/structure`, `structure_set`, `template_pool`, `dimension`, biome/other tags). Those
registries are parsed when a world is created/loaded, and a bad file throws
`IllegalStateException: Failed to load registries` and crashes world creation - which looks to the
user like "I clicked Create World and the game closed".

**Validate worldgen without clicking Create World: boot the dedicated server.** `./gradlew runServer`
loads the datapack registries on startup and generates the spawn area, hitting the exact code path
that crashes world creation. If it reaches `Done (Ns)!` with no `Failed to load registries` /
`Unbound values` / `No key <field>` lines, the worldgen is valid. Grep the server log for those and
for `Preparing spawn area` / `Done (`. Stop it afterwards (kill the process on port 25565). This is
the ONLY way to verify worldgen from this environment - the client can't be driven through the
Create-World button.

The failure message is precise and worth reading fully: `No key spawn_overrides in MapLike[...]`
means that exact required field is missing from that JSON. Codecs report the FIRST missing required
field, so fixing one may reveal another - verify against the actual codec (see the structure-fields
note in `dimensions-teleportation-portals.md`) rather than fixing one at a time via repeated crashes.
