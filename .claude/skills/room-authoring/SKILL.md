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
7. **The room's WALLS and CEILING must be sealed** - no gaps. On placement the room is shrink-wrapped
   in a layer of Nullstone worked out by flooding air inward from outside and stopping at solid
   blocks (`RoomContainment`). A hole in a wall or the ceiling lets that flood leak into the interior,
   which wraps the INSIDE walls in Nullstone too and ruins the room.

   **The FLOOR is exempt, deliberately.** Rooms are stamped at `FLOOR_Y = 0`, which is the Null
   Domain's min build height, so there is nothing under a room and `shrinkWrap` skips its search
   volume's bottom plane for exactly that reason. A hole in a room's floor is therefore a hole into
   the void — a genuine, lethal drop and a legitimate thing to author. It is one of the few actually
   dangerous features available in a dimension where nothing spawns. `verify_room_nbt.py` models this
   the same way, so it will not flag one.

Note that every block a room is built from is now **breakable** (altar blocks are `strength(3.0)`,
dropless). That is deliberate: digging out has to look possible. What a player actually finds behind
a broken wall is the Nullstone shrink-wrap, then five blocks of empty space, then a Nullstone +
Forsaken Essence cage with no floor. Only the two Nexus beds and the Forsaken Essence are unbreakable.

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

A skimmable command cheat sheet for the user lives at [WORLDEDIT.md](../../../WORLDEDIT.md) in
the repo root - keep the two in sync when a new command or gotcha is learned.


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

## Techniques that make a room look built rather than generated

**Damage clean geometry rather than placing ruins.** Percentage patterns work in any `//set` or
`//replace`: `//replace <brick> 80%<brick>,20%<cracked>` weathers a whole room in one command, and
`//replace <brick> 58%<brick>,42%air` run over an `//hcyl` ring collapses it into a broken
colonnade. This is the highest-value trick available.

**Stepped dais**: three `//cyl` of decreasing radius with `/tp ~ ~1 ~` between them.

**Lava**: a source spreads into adjacent AIR, so control the neighbours. A source placed in the
CEILING has four solid sides and only down open, giving a perfect 1-block-wide lavafall that can
never flood. `//set minecraft:lava` across a floor region gives a flush lake or river. Never leave a
source floating in open air or embedded in a wall face. Lava is not air, so it still counts as
sealed for the shrink-wrap.

---

## Generated rooms — when the shape is maths

**Anything per-block-mathematical** (spirals, helicoids, rippled surfaces) is neither a WorldEdit
primitive nor pasteable as chat commands. Write a Python generator in `tools/` that emits a datapack
function of relative `setblock` lines into the builder world's own datapack; the author stands where
they want it and runs one `/function build:<name>`.

Three exist:

| script | function | what it makes |
|---|---|---|
| `tools/generate_spiral_function.py` | `/function build:spiral` | helicoid stair tower, 17×41×17 |
| `tools/generate_basin_room.py` | `/function build:basin` | antiphase rippled floor + ceiling, 41×17×41 |
| `tools/generate_causeway_room.py` | `/function build:causeway` | Nullstone void with a brick walkway, 31×25×31 |
| `tools/generate_oubliette_room.py` | `/function build:oubliette` | nested rings, funnelled ceiling, 23×10×23 |
| `tools/generate_carpet_room.py` | `/function build:carpet` | Sierpinski-carpet floor over a drop, 33×27×33 |
| `tools/generate_unicursal_room.py` | `/function build:unicursal` | Hilbert-curve corridor maze, 21×7×21 |
| `tools/generate_hypostyle_room.py` | `/function build:hypostyle` | Cantor-dust colonnade, 47×47×47 |
| `tools/generate_lattice_room.py` | `/function build:lattice` | 3D Cantor dust in a black void, 35×35×35 |
| `tools/generate_knot_room.py` | `/function build:knot` | 3D Hilbert curve as pipe + cut bridge, 31×31×31 |
| `tools/generate_pyramid_room.py` | `/function build:pyramid` | hollow stepped pyramid + lavafall, 45×47×45 |
| `tools/generate_throat_room.py` | `/function build:throat` | rifled tapering circular bore, 25×25×47 |
| `tools/generate_anamorph_room.py` | `/function build:anamorph` | anamorphic figure in void, 24×44×46 |

### Design tricks worth reusing

**Antiphase surfaces.** `ceiling = CLEARANCE - floor(r)` instead of `CLEARANCE + floor(r)`: the two
surfaces mirror rather than run parallel, so headroom swings by *twice* the ripple amplitude. The
Basin goes from a 13-block vault to a 3-block crawl and back on an amplitude of only 3. Parallel
surfaces feel like a corridor; mirrored ones feel like the room is squeezing.

**A walkway one block over a Nullstone floor.** Nullstone renders perfectly flat — no directional
face shading, no ambient occlusion, no visible lightmap falloff, because black times any shading term
is still black. A Nullstone floor is therefore indistinguishable from a hole in the world, and a lit
brick path raised one block above it reads as a bridge over void. Stepping off the edge is a
one-block drop onto a floor the player was certain was not there. The Causeway is built entirely on
this; it costs one block of elevation.

Note the corollary: **white does not survive that shading**, which is why plain Allstone read as
quartz until its model got `"shade": false`, `"ambientocclusion": false` and NeoForge's
`"neoforge_data": {"block_light": 15, "sky_light": 15}`. Nullstone needs none of it and Allstone
needs all of it, for the same reason.

**Self-similarity defeats scale judgement.** A fractal floor looks identical at every zoom, so a
player cannot tell how far across the room is or how far they have come. That is the most liminal
thing geometry can do and it costs nothing — the fractal does it, not the decoration.

**Space-filling curves are the other half of this.** A Hilbert curve never branches and never crosses
itself, so a corridor cut along one is a *unicursal* labyrinth - one path, no choices, no way to get
lost and no way to shortcut - and it is self-similar, so no stretch of it can be told from any other.
Pitch 2 (one-block corridor, one-block wall) is what makes a one-block slot through a wall a *window*
rather than a tunnel, and because every cell knows its own index along the curve you can place those
windows where they look out onto corridor the player is a long way from. The maths decides where the
cruelty goes.

**Budget a maze by seconds, not by cleverness.** The first cut of the Unicursal was an order-4 curve
walked end to end: 502 blocks and about three minutes of identical corridor, which is a commute, not
a room. Order 3 with the dark Nexus placed *partway along the curve* rather than at its far end gives
66 blocks — around twenty seconds — which is long enough to lose your bearings and short enough that
nobody resents it. Reckon on roughly 3 blocks/second in a one-wide corridor; you cannot sprint
through turns. Leaving 56 cells of corridor running on past the exit is the other half of it: a maze
you have completely traversed is solved, and one that visibly continues past your exit is not.

**An extruded 2D fractal is invisible from inside it.** This is the single most important lesson so
far and it cost a whole room. The Hypostyle's Cantor dust was a floor *plan*, pushed straight up into
columns — and a player standing in the pattern is in the one place they can never see it. From inside
it read as a big room full of pillars. **A fractal room must recurse in all three axes**, or the
maths is decoration on a map nobody looks at.

**Anamorphosis: depth is quantised, and you must verify by rendering.** A 1-block cube subtends
`1/d` radians, so it fills its cell of a projected picture at exactly one distance and nowhere else -
nearer it spills over its neighbours, further it leaves a gap. So a cell can sit at `D0` as one
block, or `2*D0` as a 2×2×2 cluster, or `3*D0` as 3×3×3, and *nothing in between*. Assigning cells
randomly between layers is what scatters the thing in depth. The 48-block cap allows two layers.

Never eyeball an anamorph: a subtly broken one looks like scattered blocks, which is also exactly
what a correct one looks like from anywhere but the arrival square. **Ray-march the finished block
set from the computed eye, one ray per picture cell, and assert the silhouette equals the picture** -
then do it again from a few blocks aside and assert it has *fallen apart*, because something that
reads from everywhere is a statue. The first run of the Anamorph failed that check with 23 cells
missing: the march limit was measured along the forward axis, but off-axis rays are much longer, so
the top of the figure was beyond the end of the ray. Nothing else would have caught it.

**Arrival is deterministic, and almost nothing uses that.** The pale Nexus supplies both the
player's position and their facing, so we know exactly where their eyes are the instant they enter a
room. That permits illusions calibrated to one viewpoint — forced perspective, anamorphosis, impossible
objects. See the "Cool build ideas" section of CLAUDE.md.

The Throat is the first to use it: a circular bore tapering from 11 across to 4 over thirty-four
blocks, floor rising to meet it. The eye assumes a constant bore, so it puts the far end at ~94
blocks. Two rules make it work — the taper must never widen (assert it, or the illusion inverts and
the tunnel reads *shorter*), and nothing of known size may sit at the far end, so the dark Nexus goes
off the sightline in the end chamber.

**Round beats rectangular for this, and for a reason worth reusing.** A rectangular tapering corridor
has a wall/ceiling join running the whole way down it — a straight edge the eye can measure along,
which gives the taper away almost immediately. A circular section has no join anywhere, so there is
no line to read, and the same taper becomes much harder to see as a taper. It also stops the room
resolving in one second, which the rectangular first cut of this room did.

**Three things decide whether a circle reads as a circle.** All of them cost nothing and the Throat
needed all three before it stopped looking like a chamfered box:

- **Do not cut the bottom off.** A wide flat floor removes most of the lower half, and everything that
  makes a circle look circular lives there. Use a *narrow catwalk suspended on the axis* with open
  bore under it instead.
- **Radius.** A discretised circle of radius 2.5 is a lozenge and radius 5 is an octagon; 8.5 is a
  circle. This is the single biggest lever and it only costs the 48-block cap.
- **Draw it.** Circumferential ribs every few blocks state the circle outright instead of leaving the
  eye to infer it from a curved wall — and evenly spaced down a tapering tube they reinforce the
  perspective as well.

Line only the *underside* of a bore in Nullstone, never the whole thing: looking over the catwalk
edge then gives void instead of a brick gutter, while the rest of the ring stays brick and legible.
An all-black bore is a circle nobody can see.

**Rifling makes a static room feel like it is turning.** Three helical ribs wound down the inside of
a bore read as rotating when you move along the axis, and because you are the thing moving, the
rotation reads as *your own*. Make the twist accelerate (`t ** 1.6`) and the apparent spin speeds up
as you walk in. Keep the ribs above head height — the effect is far worse in peripheral vision than
somewhere you can look directly at, and it keeps the walkway clear.

**One constant can decide whether a room is a room.** The Pyramid's `RISE` is how many blocks its
wall climbs before stepping one inward. At 1 the interior is a 45° staircase the player simply walks
up to the apex — a ramp, not a tomb. At 2 every ledge is a two-block riser, too tall to step or jump
onto, so they are held on the floor and the space stays something you look *up into*. Same shape,
same block count, completely different room. Look for the constant like that before adding anything.

**A one-block sloped shell is airtight.** A 45° (or steeper) staircase of single blocks has no
face-adjacent path from outside to inside, so a stepped pyramid seals without a second skin. Diagonal
gaps do not leak, because the shrink-wrap flood is 6-connected.

**A Cantor dust can never fill a room — that is its definition.** It deletes the middle third at
every level, so a void at the dead centre is structural, and a totally disconnected set can only ever
be scattered identical clusters. If the brief is "fill the whole room, centre included", the answer is
a **space-filling curve**: a 3D Hilbert curve passes within a fixed distance of every point in the
cube by construction, is one connected line, never branches and never crosses itself. Rendered as a
one-block pipe on a 3-block pitch it comes out around 14% density — dense enough that no direction is
empty, open enough to see through.

**Verify a space-filling curve, do not trust it.** Skilling's transform is easy to get subtly wrong
and a wrong one still produces a plausible-looking tangle that nobody would question. Assert the point
sequence visits all 8^order cells exactly once and that every consecutive pair differs by one on
exactly one axis. Only a real Hilbert curve passes both.

**Cut the route through, and let the cut show.** A 14%-dense knot is unwalkable, so the Knot has a
three-wide brick bridge driven straight through the middle with a three-block channel cleared above
it. The severed pipe ends hanging on both sides are the feature, not damage: it is obvious the cut
came second, which is worth more than a room that politely left a corridor.

**Check the fractal's projections before committing to it.** The obvious 3D pick is the Sierpinski
tetrahedron, and it is wrong for a rectangular room: its projection along every coordinate axis is a
*filled square*, so viewed straight down any wall it collapses into a solid slab. A 3D **Cantor dust**
projects to a Cantor dust on all three axes — there is no angle from which it resolves into something
simple. That is why the Lattice uses one.

**Nullstone hides a room and shows an object.** Because it is flat black with no shading, a box whose
every face is Nullstone gives the eye nothing at all — walls, floor and ceiling all read as absence.
Put anything brick inside it and that object appears to hang in nothing. The Lattice is 512
unconnected blocks suspended in exactly that, which is what sells "this should not exist": nothing is
load-bearing, nothing is connected, nothing is a surface.

**48 is a hard ceiling on "big".** Structure blocks cannot capture more than 48 in any axis, so the
largest room the format can hold is 47×47×47 no matter how much space is cleared for it. The
Hypostyle is exactly that. Say so up front when someone asks for a huge room.

**A totally disconnected fractal gives a walkable hall for free.** A **Cantor dust** (the 2D product
of a Cantor set with itself) is totally disconnected, so its complement is connected — put columns on
the dust and every floor cell is reachable without designing a single route. Three iterations on a
43-block span gives 64 columns of 2×2 with aisles 1, 4 and 15 wide: three scales, so every view is a
scaled copy of every other and the aisle you are in tells you nothing about how far you have come. In
a regular colonnade you can count your way across; in this one you cannot. Assert the aisle widths
come in at least three distinct sizes — integer rounding can quietly flatten the recursion into a
plain grid, and nothing else would notice.

**A room this size earns being a junction.** Rooms may hold several dark Nexus beds and until the
Hypostyle none of the authored ones did. Three of them on three sides makes the room a genuine fork
and gives the size a purpose: choosing one means giving up the others for that trip.

Pick the fractal by its **connectivity**, not its looks. The Sierpinski carpet is a connected set
whose complement is not, so *filled = floor, holes = holes* is walkable everywhere at every scale;
invert it (walls on the carpet, walk in the gaps) and the walkable space shatters into eight sealed
chambers. A Menger sponge has the same appeal and the opposite problem — its tunnel network is
connected but largely vertical, so most of it is unreachable without ladders. Level count is capped
by the 48 limit: a level-N carpet is 3^N across, so 3 levels (27) is the most that fits.

Whenever a room hinges on a property like that, **assert the property**. `generate_carpet_room.py`
BFSes the carpet and fails if any cell is an island.

**A drop must not soft-lock.** A pit a player cannot climb out of is a run ender in a mod whose only
escape is waiting for a drug to expire. Give it a floor they land on and a way back up. Better, make
falling a *shortcut*: the Carpet puts the dark Nexus on the sub-floor, so the safe route (cross the
fractal, find the stair in the big central hole, walk down) is strictly longer than stepping into a
hole. The room rewards the mistake.

**Ceiling height as a function of distance from the centre.** `headroom(d) = BASE + round(d * SLOPE)`
turns a flat room into a funnel. Use **Chebyshev** distance (`max(|x|, |z|)`) for square rooms and
Euclidean (`hypot`) for round ones. The Oubliette runs six blocks of headroom at the outer wall down
to two at the centre, and the steps deliberately land mid-corridor rather than at the walls, so there
is never a doorway to brace for — the room just quietly closes as you go in. Costs nothing; a flat
ceiling was the same number of blocks.

**Two-thick walls let a doorway be a lie.** Cut a door through the outer layer only and it becomes a
one-block-deep recess with a brick face at the back. Three bricked-up doors and one real one per ring
means every exit is visible from the moment you arrive and you still have to walk the whole thing —
no hidden switches, no puzzle, just refusal. Put the real door on a different side each ring and the
route switchbacks.

**A Nullstone skin over a different interior.** Build the shell two layers thick — the interior
material inside, one layer of Nullstone outside. From within the room the palette is whatever you
chose; from outside, or through a hole a player digs, the build reads as void rather than as a box
someone assembled. (The Domain's own shrink-wrap already coats rooms in Nullstone on placement, so
this mostly matters for how the room reads in the builder world and behind a breached wall — but it
costs one extra layer and makes the intent explicit.)

### A generator must assert its own invariants

This is the whole reason generating beats hand-building — the script can prove the room is valid
before a single block exists, so a bad constant fails in the terminal instead of after a capture.

Every room generator checks that the room is **sealed**: flood air inward from a shell outside the
build and assert it cannot reach the interior. That is the same algorithm `RoomContainment.shrinkWrap`
runs at placement time, so it is a true test of rule 7 above rather than an approximation. Beyond
that, each checks whatever its own shape can get wrong:

- **Basin** — no orthogonal floor step over 1 block (walkable; keep `AMP * 2π / WAVELENGTH` under
  ~0.8), and headroom never under 3 (passable).
- **Causeway** — the walkway is one connected path reachable on foot from the entrance (otherwise
  part of the room is decoration the player can only look at), both beds stand on it, and the single
  lava source is fully enclosed at walkway level so it cannot spread.
- **Oubliette** — headroom never drops under the 2 blocks a player occupies, the dark bed is
  reachable on foot from the pale one, and the walk is at least `MIN_PATH` blocks long. That last one
  is the interesting check: it fails if the open doors drift onto the same side of each ring, which
  turns the labyrinth into a straight line without breaking anything a seal check would notice.

Same philosophy as the texture scripts, which assert their own tiling, loop seams and (for the
Nullstone/Allstone pair) that the two are exact channel-wise inverses. Fail loudly rather than ship
a broken artefact.

### Running and re-running one

Build at a fixed point rather than flying to a coordinate — the function reads whatever block the
executor is standing in, and hovering on exactly the right block is fiddly:

```
/reload
/execute positioned 100 0 100 run function build:causeway
```

Iterating means wiping the last attempt. `/fill` the capture box, corners included so the structure
blocks go with it, and keep the volume under **32,768** or `/fill` refuses (33 × 27 × 33 = 29,403
fits). Stand well outside first — when the floor becomes air it is a long way down:

```
/fill 84 -1 84 116 25 116 air
```

Two command-syntax traps that have each cost a round trip: `/tp <x> <y> <z> <yaw> <pitch>` is not a
valid overload (the rotation form needs an explicit target, `/tp @s 100 3 88 0 0`; yaw 0 = south),
and a `random.seed` hex literal must actually be hex — `0x5P1RAL` and `0xBA51N` have both been
typo'd.

### Ordering matters when emitting

Emit **fluids last**. The Causeway's lava source is the final line in its function so the room is
already finished when it appears; placed earlier it would flow across an unbuilt floor. Same
reasoning for beds — place both halves adjacently so the second one resolves the first's
`updateShape` while the other half exists.

## Gotchas already paid for

**`//hollow` will eat the whole build.** It hollows an object that sits *inside* the selection with
air around it, working out inside-vs-outside from that air. A solid that fills the selection edge to
edge has no outside reference and everything gets removed. Use `//faces` / `//walls`, or the
solid-then-carve cylinder recipe. Never `//set` + `//hollow`.

**Round rooms capture the terrain in the box corners.** A circular room in a square capture box means
the corners contain whatever surrounds it — grass, dirt, stone — and that gets saved into the room and
then placed in the Null Domain. Square rooms happen to dodge this by filling their box. Fixes, best
first: build in the air (the rig does this for you), or clear the terrain before saving - either
inside a selection, or with the radius-based utility commands which need no selection at all:

```
//replace minecraft:grass_block minecraft:air     # needs a selection
/replacenear 30 grass_block air                   # no selection - <size> <mask> <pattern>
/removenear grass_block 30                        # no selection - <mask> <size>  (order is reversed!)
```

`//distr` prints a block breakdown of the selection, which is the quickest way to catch stray
terrain before hitting SAVE.

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
change** — and with no compile error to catch a bad one. Always run the gate:

```
python tools/verify_room_nbt.py <path to .nbt>
```

It parses the capture read-only and checks it is **sealed** (air flooded from outside the box cannot
reach the space above any Nexus bed — RoomContainment's own algorithm), has exactly one pale bed and
at least one dark bed, fits in 48³, and caught no stray terrain.

Run it **after any hand-tweaking**, not just on rooms from a collaborator. A generator proves its own
output is sealed; nothing proves it is still sealed once someone has dug a hole in the floor to see
what was under it. That is not hypothetical — it is exactly how the Causeway's shell got a 2×3 hole
punched through both floor layers, which nothing else would have noticed until Nullstone appeared on
the inside walls in game.

Never *edit* an `.nbt` — the verifier is read-only on purpose. Editing needs a codec proven to
round-trip the original byte-for-byte first (see `mc-modding-notes`); fix the build in world and
re-capture instead.
