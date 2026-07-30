---
name: room-authoring
description: In-game workflow for authoring Null Domain rooms for dim_descent - the permanent structure-block capture rig, WorldEdit commands for building shells/cylinders/walls, the exact setblock and Relative Position/Structure Size numbers, and how to get a finished .nbt into the mod. Use this whenever the user is building or saving a room, asks where to place structure blocks, asks how to capture/save a build, asks a WorldEdit question, or hands over a finished room to import. Contains the arithmetic so it never has to be re-derived, and the gotchas (grass in the corners of round rooms, 48-block cap, //hollow eating builds) that have already cost time once.
---

# Authoring Null Domain rooms

Rooms are the mod's content and the main ongoing work. A room is an ordinary Minecraft build,
captured with vanilla Structure Blocks. WorldEdit (installed in `run/mods/`) does the building.

The authoring world is **`DimDescentRoomBuilder`** — a flat creative world. See the
`room-builder-world` memory.

---

## The rules a room must follow

1. **Exactly one PALE Nexus of Eternal Slumber** (`dimdescent:pale_dream_bed`). This is the entrance
   *and* the way back. Players arrive beside it facing into the room, so it doubles as the spawn
   marker and supplies arrival facing. No marker block, no per-room config file.
2. **One or more DARK Nexus beds** (`dimdescent:dream_bed`). Each one is its own branch to its own
   room — a room with three dark beds is a three-way fork.
3. **48 × 48 × 48 maximum.** Hard structure-block limit.
4. **Nothing but intended blocks inside the capture box.** Anything in the box gets saved, including
   terrain. See the round-room gotcha below.
5. Lighting is decorative only — the Null Domain renders at full brightness, so Daemonlights are
   atmosphere, never utility.
6. Empty vanilla chests are welcome; the loader points them at a loot table on placement.

---

## The capture rig (set up once, use forever)

Placing corner blocks by hand and hunting coordinates is the slow way. **Corner blocks are not
required at all** — the SAVE block's GUI takes `Relative Position` and `Structure Size` directly.
So build one permanent rig in the air and never do coordinate maths again.

Place the rig block once:

```
/setblock 0 99 0 minecraft:structure_block[mode=save]{mode:"SAVE",name:"dimdescent:rooms/scratch"}
```

Right-click it and set, once:

- **Relative Position:** `0` `1` `0`
- **Structure Size:** `48` `48` `48`

That claims the 48³ box sitting directly on top of the block, starting at `0 100 0` and running to
`47 147 47`. The rig itself sits at Y 99, **outside** the box, so it never captures itself.

### Per-room loop

1. Build inside the box (origin corner `0 100 0`).
2. Right-click the rig → change **only the name** to `dimdescent:rooms/<name>` → **SAVE**.
3. Clear it for the next room — these take literal coordinates, so they work from anywhere:

```
//pos1 0,100,0
//pos2 47,147,47
//set air
```

Why the rig wins: no corner hunting, no coordinate maths (only the name changes), it's in the air so
unused space is already air, and every room shares an origin so placement behaves predictably.

Cost: every room saves as 48³ with a lot of empty air. Harmless — air compresses to nearly nothing
and the Domain is void anyway. Shrink the Structure Size field for a tighter file if it ever matters.

---

## If not using the rig: corner-block arithmetic

The captured region is **exclusive of both corner blocks** (`detectSize()` does `pos = min+1`,
`size = span-1`), so corners sit one block outside the build on **every** axis.

For a build centred on the player's feet with radius `r` and height `h`:

```
/setblock ~-(r+1) ~-1 ~-(r+1) minecraft:structure_block[mode=save]{mode:"SAVE",name:"dimdescent:rooms/NAME"}
/setblock ~(r+1)  ~h  ~(r+1) minecraft:structure_block[mode=corner]{mode:"CORNER",name:"dimdescent:rooms/NAME"}
```

Radius 20, height 14 → offsets `21` and `14`; DETECT should read **41 × 14 × 41**. The two names must
match exactly or DETECT won't pair them. Set both the blockstate `mode=` and the NBT `mode:` — the
blockstate alone doesn't configure the block entity.

---

## WorldEdit recipes

Select with `//wand` (wooden axe): **left-click** = corner 1, **right-click** = corner 2. `//sel`
clears the selection — it's only a visual overlay, it never affects placed blocks. `//undo` steps
back one command at a time and is reliable.

**Rectangular room**

```
//expand <n> up
//faces dimdescent:altar_stone_bricks    # all six sides - a hollow box in one command
//walls dimdescent:altar_stone_bricks    # four vertical sides only, keeps an existing floor
```

Run these on an **empty** selection. Do NOT `//set` solid first and then `//hollow` — see below.

**Cylindrical room** — solid-then-carve, because it can't desync:

```
//cyl dimdescent:altar_stone_bricks 20 14   # solid pillar, 41 across, 14 tall
/tp ~ ~1 ~                                  # exactly one block up, no horizontal drift
//cyl air 19 12                              # carve the interior out
```

Gives a 1-thick floor, wall and ceiling, concentric **by construction**. Carve radius = outer − 1;
carve height = outer − 2. Diameter is `2r+1`, so keep `r ≤ 23` to stay under 48.

**Ceiling only**, from a full-height selection:

```
//contract <n> down     # pulls the bottom face up, leaving the top layer
//set dimdescent:altar_stone_bricks
```

Other useful shapes: `//sphere`, `//hsphere`, `//hcyl`, `//pyramid`,
`//br sphere <block> <r>` (brush; `//br none` unbinds), `//stack <n> <dir>`, `//copy` / `//paste`,
`//replace <from> <to>`.

Builder-world quality of life: `/gamerule doMobSpawning false`, `/difficulty peaceful`,
`/gamerule doWeatherCycle false`.

---

## Gotchas already paid for

**`//hollow` will eat the whole build.** It hollows an object that sits *inside* the selection with
air around it, working out inside-vs-outside from that air. A solid that fills the selection edge to
edge has no outside reference and everything gets removed. Use `//faces` / `//walls`, or the
solid-then-carve cylinder recipe. Never `//set` + `//hollow`.

**Round rooms capture the terrain in the box corners.** A circular room in a square capture box means
the corners contain whatever surrounds it — grass, dirt, stone — and that gets saved into the room and
then placed in the Null Domain. Square rooms happen to dodge this by filling their box. Fixes, best
first: build in the air (the rig does this for you), or clear the box before saving:

```
//replace minecraft:grass_block minecraft:air
//replace minecraft:dirt minecraft:air
```

**Three shapes centred "on the player" will drift.** Building a floor disc, then walls, then a
ceiling as three separate commands relies on standing in exactly the same spot three times; a
fraction of a block puts the circles out of alignment. Prefer solid-then-carve, and use `/tp ~ ~1 ~`
(literal `~`, no typed coordinates) whenever a step needs a height change.

**A just-saved structure is invisible to LOAD mode until the world is reloaded.** `/reload` does not
re-index the `generated/` cache — quit to title and rejoin. To iterate on a base build, prefer
`/clone` or `//copy` in-world over save/load.

**`/place structure` vs `/place template`.** `/place structure <id>` is for registered worldgen
structures (the altar) and runs real placement logic including heightmap projection — handy for
testing placement without making a new world. `/place template <id>` places a saved structure-block
template (`dimdescent:rooms/...`) exactly as captured.

**Structure-block DATA mode is hidden**, not missing — hold Alt while cycling from Corner mode. It's
a deprecated mode and we don't use it.

**WorldEdit's `//schem save` is the wrong format.** It writes `.schem`; the mod's loader reads vanilla
structure NBT. Build with WorldEdit, always save with Structure Blocks.

---

## Getting a finished room into the mod

Saved files land in:

```
run/saves/DimDescentRoomBuilder/generated/dimdescent/structures/<name>.nbt
```

Copy into:

```
src/main/resources/data/dimdescent/structure/rooms/<name>.nbt
```

The pool is discovered at runtime from that folder, so a new `.nbt` joins the rotation with **no code
change**. Verify a handed-over room before importing — size within 48³, exactly one pale bed, at
least one dark bed, and no stray terrain blocks — by parsing the NBT (see the round-trip-safe codec
notes in `mc-modding-notes`, and don't edit an `.nbt` without proving the codec round-trips the
original byte-for-byte first).
