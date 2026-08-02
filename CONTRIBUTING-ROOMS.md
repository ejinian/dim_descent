# Building rooms for dim_descent

Setup guide for a collaborator who wants to build Null Domain rooms and send them over as `.nbt`
files. **Windows 10 is fine** — nothing here needs anything newer.

Repo: **https://github.com/ejinian/dim_descent**

You do **not** need a Minecraft account, a launcher, or a purchased copy of the game. The development
client runs offline and logs you in as a dummy player called `Dev`.

---

## 1. Prerequisites

### Java 21 (required)

Download **Eclipse Temurin JDK 21** (not 17, not 24 — the project pins 21):
https://adoptium.net/temurin/releases/?version=21 — choose **Windows / x64 / JDK / .msi**.

During install, on the "Custom Setup" screen, change **"Set JAVA_HOME variable"** from
`Entire feature will be unavailable` to **`Will be installed on local hard drive`**. This is the
single most common thing people miss and it causes a confusing Gradle failure later.

Verify — open a **new** terminal (the variable won't exist in one that's already open):

```bash
java -version
```

Should print `openjdk version "21.0.x"`. If it prints 17 or 8, you have another JDK ahead of it on
your PATH; that's fine as long as `JAVA_HOME` points at 21 — check with `echo $JAVA_HOME` in Git Bash
or `echo %JAVA_HOME%` in cmd.

### Git (required)

https://git-scm.com/download/win — accept all defaults.

This installs **Git Bash**, which is the terminal to use for everything below. Open it by
right-clicking in any folder → **"Open Git Bash here"** (Windows 10) or **"Git Bash Here"**.

If you'd rather use PowerShell, every `./gradlew` below becomes `.\gradlew.bat`.

### IntelliJ IDEA Community (optional)

https://www.jetbrains.com/idea/download/ — scroll down to **Community Edition**, which is free.
You only need this if you want to read the code; building rooms doesn't require it.

### Disk space

**~5 GB.** Gradle downloads and decompiles Minecraft and NeoForge into `C:\Users\<you>\.gradle`
on the first run. That cache is shared, so a second mod project wouldn't pay it again.

---

## 2. Clone and run

In Git Bash, from wherever you keep projects:

```bash
git clone https://github.com/ejinian/dim_descent.git
cd dim_descent
```

```bash
./gradlew runClient
```

**First run takes 5–15 minutes** and prints a lot of output — it's downloading Minecraft, patching it
with NeoForge, and decompiling. It is not frozen. Later runs take about a minute.

You're done when the **Minecraft title screen** appears. The mod is already loaded — no installation
step, no launcher profile.

To stop it, just close the Minecraft window.

### Opening it in IntelliJ (optional)

**File → Open** → select the `dim_descent` folder (not a file inside it) → **Trust Project** when
prompted. Wait for the Gradle sync in the bottom status bar to finish — a few minutes the first time.
Run configurations named `runClient` and `runServer` then appear in the dropdown at the top right.

---

## 3. Add WorldEdit

WorldEdit is what makes building large rooms bearable. It isn't in the repo (the `run/` folder is
gitignored), so grab it yourself.

**Run the client once first** — that's what creates the `run/` folder.

Download these two, and put both in `dim_descent/run/mods/`:

| Mod | Version | Link |
|---|---|---|
| **WorldEdit** | 7.3.8 — pick the **NeoForge** file, `worldedit-mod-7.3.8.jar` | https://modrinth.com/plugin/worldedit/version/7.3.8 |
| **WorldEditCUI** *(optional but recommended)* | `WorldEditCUI-NeoForge-1.21.1+01-SNAPSHOT.jar` | https://modrinth.com/mod/worldeditcui-forge/version/1.21.1+01-SNAPSHOT |

CUI draws your selection box in the world so you can see what you've selected — genuinely worth it.

If `run/mods/` doesn't exist, create the folder manually.

Restart with `./gradlew runClient`, get into a world, and type `//wand` in chat. If you receive a
wooden axe, it's working.

---

## 4. Make a builder world

**Singleplayer → Create New World**

- **Game Mode: Creative**
- **More → World Type: Superflat** (a flat grass world — nothing to dig through)
- **Allow Cheats: ON**

Name it something obvious like `RoomBuilder`.

Once inside, paste these once each:

```
/gamerule doMobSpawning false
/gamerule doWeatherCycle false
/difficulty peaceful
/weather clear
/time set noon
```

Nothing will spawn, rain, or get dark on you again.

---

## 5. What a room must contain

Open the creative inventory and search **`nexus`**. Two beds appear, and — deliberately — **they share
the same name**, "Nexus of Eternal Slumber":

- the **dark, tattered, gray** one → `dimdescent:dream_bed`
- the **pale, near-white** one → `dimdescent:pale_dream_bed`

The rules:

1. **Exactly one PALE Nexus.** This is the room's entrance. Players arrive standing beside it, facing
   *into* the room, so **where you place it and which way it faces decides the spawn point and the
   direction players look when they arrive.** Put it against a wall, pointing at whatever you want
   them to see first.
2. **One or more DARK Nexus beds.** Each is a separate exit leading to its own separate room. One dark
   bed = a corridor; three dark beds = a three-way fork. Both are good; variety is the point.
3. **48 × 48 × 48 maximum**, hard limit. That's the largest a structure block can save.
4. **Nothing unintended inside the capture box.** Whatever sits in the box gets saved — including
   grass and dirt, which would then appear floating in a void dimension. The simple fix is to build
   in the air (step 6 does this for you).
5. **Lighting is decoration only.** The Null Domain renders at full brightness no matter what, so
   torches never help anyone see. Use them for atmosphere.
6. **Empty vanilla chests are welcome** — place them empty; they get filled with loot automatically
   when the room is used.
7. **Seal the room.** No holes in the outer shell. The game automatically encases each room in a
   layer of black Nullstone worked out from its shape, and a gap lets that leak inside and coat the
   interior walls too. Walls, floor and ceiling should be unbroken.

Useful blocks for the mod's look, all searchable in creative: `altar_stone_bricks`,
`cracked_altar_stone_bricks`, `altar_stone_brick_slab`, `altar_stone_brick_stairs`, `nullstone`
(pure black), `dark_iron_bars`, `daemonlight` (a demonic torch — place it, then right-click with
flint and steel to light it).

---

## 6. Build and save

Building commands are listed in **[WORLDEDIT.md](WORLDEDIT.md)** — start with `//wand`, `//set`,
`//faces` and `//walls`.

### Set up a capture rig, once

Fly somewhere clear, away from where you'll build, and run this — it places a structure block a few
blocks above the flat world's ground level:

```
/setblock -100 -55 -100 minecraft:structure_block[mode=save]{mode:"SAVE",name:"dimdescent:rooms/scratch"}
```

Fly to it (`/tp -100 -54 -100`), right-click it, and set two fields:

- **Relative Position:** `0` `1` `0`
- **Structure Size:** `48` `48` `48`

Click **Done**. That block now permanently claims the 48×48×48 box sitting directly on top of itself —
from `-100, -54, -100` to `-53, -7, -53`. The rig itself sits one block below the box, so it never
saves itself.

*(Superflat ground is around Y −60. If your world's ground is elsewhere, put the rig ~5 blocks above
it and adjust the numbers below to match.)*

### Then, for each room

1. **Build inside the box.** Its floor is at Y −54.
2. **Save it:** right-click the rig → change only the **Structure Name** to
   `dimdescent:rooms/<yourname>` → click **SAVE**. Use lowercase letters, numbers and underscores
   only — `dimdescent:rooms/spiral_stair`, not `Spiral Stair`.
3. **Clear it for the next one:**

```
//pos1 -100,-54,-100
//pos2 -53,-7,-53
//set air
```

Those take typed coordinates, so they work from anywhere — no clicking or flying to corners.

---

## 7. Send the file

Saved structures land here:

```
dim_descent\run\saves\<your world name>\generated\dimdescent\structures\rooms\<name>.nbt
```

Send that `.nbt` (Discord, email, anything). That's the entire deliverable — it gets dropped into the
mod's `data/dimdescent/structure/rooms/` folder and joins the room pool with no code changes.

They're small, usually well under 100 KB.

**Handy:** before saving, run `//distr` on a selection to list every block type inside it. If you see
`minecraft:grass_block` or `minecraft:dirt` in that list, clear it out first:

```
/replacenear 30 grass_block air
/replacenear 30 dirt air
```

---

## 8. If something breaks

| Symptom | Cause / fix |
|---|---|
| `JAVA_HOME is not set` or `Could not determine java version` | Temurin installed without the JAVA_HOME option. Re-run the installer, enable it, then open a **new** terminal. |
| `'gradlew' is not recognized` | You're either not in the `dim_descent` folder (`cd dim_descent`), or you're in PowerShell — use `.\gradlew.bat`. |
| Gradle fails strangely on the first run | `./gradlew --refresh-dependencies`, then try again. If it still fails, `./gradlew clean` and retry. |
| Build seems frozen for ages the first time | It isn't — decompiling Minecraft takes 5–15 minutes. Leave it. |
| `//wand` does nothing | The WorldEdit jar isn't in `run/mods/`, or the client wasn't restarted after adding it. Also check you grabbed the **NeoForge** file, not Fabric. |
| Structure block won't SAVE | The name needs a namespace: `dimdescent:rooms/foo`, not `foo`. |
| A saved room doesn't show up in LOAD mode | Quit to title and rejoin. `/reload` doesn't re-index saved structures. |
| `//hollow` deleted my whole build | Known trap — see WORLDEDIT.md. Use `//faces` or `//walls` instead. `//undo` gets it back. |
| Everything is grass-colored in the saved file | You built on the ground and the capture box caught terrain. Build in the air, or use `/replacenear` as above. |
