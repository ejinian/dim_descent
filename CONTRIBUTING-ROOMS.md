# Building rooms for dim_descent

Setup guide for a collaborator who wants to build Null Domain rooms and send them over as `.nbt`
files. Windows 10 is fine — nothing here needs anything newer.

Repo: **https://github.com/ejinian/dim_descent**

---

## Prerequisites

1. **Java 21 JDK** — [Adoptium Temurin 21](https://adoptium.net/temurin/releases/?version=21).
   Pick the `.msi`, and tick **"Set JAVA_HOME variable"** during install.
   Verify in a terminal: `java -version` → should say `21.x`.
2. **Git** — [git-scm.com](https://git-scm.com/download/win). Installs **Git Bash**, which is the
   terminal to use for every command below. (PowerShell works too, but then run `.\gradlew.bat`
   instead of `./gradlew`.)
3. **IntelliJ IDEA Community** — [free download](https://www.jetbrains.com/idea/download/).
   Optional if you only want to build rooms, but handy.
4. **~5 GB free disk** — Gradle caches Minecraft, NeoForge and mappings on first run.

You do **not** need a Minecraft launcher or a paid account for this — the dev client runs offline.

---

## Setup

```bash
git clone https://github.com/ejinian/dim_descent.git
cd dim_descent
```

```bash
./gradlew runClient
```

First run takes 5–15 minutes (it's downloading and decompiling Minecraft). Later runs take ~1 minute.
When Minecraft's title screen appears, you're done — the mod's blocks are already in the creative menu.

**In IntelliJ:** File → Open → select the `dim_descent` folder → trust the project → wait for the
Gradle import to finish. Run configs (`runClient`, `runServer`) appear in the top-right dropdown.

---

## Add WorldEdit

Not in the repo (`run/mods/` is gitignored), so grab it yourself. Both go in `dim_descent/run/mods/`:

- **WorldEdit 7.3.8** — https://modrinth.com/plugin/worldedit/version/7.3.8 (pick the NeoForge file)
- **WorldEditCUI** (optional, shows your selection box) —
  https://modrinth.com/mod/worldeditcui-forge/version/1.21.1+01-SNAPSHOT

Restart `runClient` and type `//wand` in game to confirm it loaded.

---

## Make a builder world

Singleplayer → Create New World → **Creative** → World Type: **Superflat** → Allow Cheats **ON**.

Then paste these once:

```
/gamerule doMobSpawning false
/gamerule doWeatherCycle false
/difficulty peaceful
/time set noon
```

---

## The rules a room must follow

1. **Exactly one PALE Nexus of Eternal Slumber** — this is the entrance. Players arrive beside it
   facing into the room, so where you put it and which way it faces decides where players spawn.
2. **One or more DARK Nexus of Eternal Slumber** — each is a separate exit to a separate room. Three
   dark beds = a three-way fork.
3. **48 × 48 × 48 maximum.** Hard limit.
4. **Nothing unintended inside the capture box** — any grass or dirt in the box gets saved into the
   room and will show up in the dimension. Easiest fix is to build in the air.
5. Lighting is decoration only — the Null Domain is full-brightness, so torches never help anyone see.
6. Empty vanilla chests are welcome; they get filled with loot automatically.

Both beds are in the creative menu under **Building Blocks**, and confusingly they share the same
name — the dark one is tattered and gray, the pale one is near-white.

---

## Build and save

Building commands are in [WORLDEDIT.md](WORLDEDIT.md).

Set up a capture rig once, somewhere clear, a few blocks above the ground:

```
/setblock -100 -55 -100 minecraft:structure_block[mode=save]{mode:"SAVE",name:"dimdescent:rooms/scratch"}
```

Right-click it and set **Relative Position** `0` `1` `0` and **Structure Size** `48` `48` `48`. It now
claims the 48³ box directly above itself.

Per room:

1. Build inside that box.
2. Right-click the rig → change the **name** to `dimdescent:rooms/<yourroomname>` → **SAVE**.
3. Clear it for the next one — replace the coords with your own rig position + 1:

```
//pos1 -100,-54,-100
//pos2 -53,-7,-53
//set air
```

---

## Send it over

Saved files land here:

```
dim_descent/run/saves/<your world name>/generated/dimdescent/structures/<name>.nbt
```

Send that `.nbt`. That's the whole deliverable — it gets dropped into
`src/main/resources/data/dimdescent/structure/rooms/` and joins the pool with no code change.

---

## If something breaks

| Problem | Fix |
|---|---|
| `JAVA_HOME is not set` | Reinstall Temurin with "Set JAVA_HOME" ticked, reopen the terminal |
| Gradle fails oddly on first run | `./gradlew --refresh-dependencies` |
| `//wand` does nothing | WorldEdit jar isn't in `run/mods/`, or the client wasn't restarted |
| Structure block won't SAVE | Name must be `namespace:path` — `dimdescent:rooms/foo`, not `foo` |
| Saved room won't appear in LOAD mode | Quit to title and rejoin; `/reload` doesn't re-index saved structures |
| `'gradlew' is not recognized` | You're in the wrong folder, or using PowerShell — use `.\gradlew.bat` |
