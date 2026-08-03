# WorldEdit cheat sheet

Quick reference for building dim_descent rooms. Full workflow lives in the `room-authoring` skill.

`B` below = a block, e.g. `dimdescent:altar_stone_bricks`.

---

## Select

| | |
|---|---|
| `//wand` | get the wooden axe |
| left-click / right-click | corner 1 / corner 2 |
| `//pos1 x,y,z` `//pos2 x,y,z` | select by typed coords — works from anywhere |
| `//expand 10 up` | grow the selection upward |
| `//expand vert` | grow to full world height (great for editing, too tall to capture) |
| `//contract 10 down` | pull the bottom face up — isolates the top layer |
| `//size` | print the selection's dimensions |
| `//sel` | clear the box (visual only, never touches blocks) |

## Build

| | |
|---|---|
| `//set B` | fill the selection |
| `//set air` | delete everything in it |
| `//faces B` | all six sides — a hollow box in ONE command |
| `//walls B` | four vertical sides only (keeps an existing floor) |
| `//replace from to` | swap one block for another |
| `//stack 5 north` | repeat the selection 5 times — corridors instantly |
| `//copy` / `//paste` | relative to where you stand |
| `//undo` | step back one command. Use freely. |

## Shapes — generated at your feet, centred on you

| | |
|---|---|
| `//cyl B 20 14` | solid cylinder, radius 20, 14 tall |
| `//hcyl B 20 14` | hollow tube (walls only) |
| `//sphere B 10` / `//hsphere B 10` | solid / hollow sphere |
| `//pyramid B 12` | pyramid |
| `//br sphere B 4` | brush — right-click to paint. `//br none` unbinds. |

Diameter is `2r+1`, so **keep r ≤ 23** to fit the 48 cap.

## No selection needed — radius around you

Single slash, not double. Handy when you can't be bothered selecting.

| | |
|---|---|
| `/replacenear 30 grass_block air` | replace nearby blocks — `<size> <mask> <pattern>` |
| `/removenear grass_block 30` | delete nearby blocks — `<mask> <size>` (note: reversed!) |
| `/removeabove 20 10` | delete the column above you — fast fix for a botched ceiling |
| `/removebelow 20 10` | same, downward |
| `/butcher 50` | remove nearby mobs |
| `//fill B 20` | fill air downward from where you stand |

## Counting / checking

| | |
|---|---|
| `//count B` | how many of that block are in the selection |
| `//distr` | full block breakdown of the selection — spot stray grass before saving |

---

## Recipes

**Rectangular room**
```
(click two floor corners)
//expand 12 up
//faces dimdescent:altar_stone_bricks
```

**Round room** — solid, then carve. Can't drift out of alignment:
```
(stand at centre, on the floor)
//cyl dimdescent:altar_stone_bricks 20 14
/tp ~ ~1 ~
//cyl air 19 12
```
Carve radius = outer − 1. Carve height = outer − 2.

**Ceiling only**, from a full-height selection:
```
//contract 12 down
//set dimdescent:altar_stone_bricks
```

**Clear terrain out of a capture box** — with a selection:
```
//replace minecraft:grass_block minecraft:air
//replace minecraft:dirt minecraft:air
```

...or without one, standing in the middle of the build:
```
/replacenear 30 grass_block air
/replacenear 30 dirt air
```

---

## Making it look good

**Percentage patterns** are the single biggest win. Any `//set` or `//replace` takes a weighted list,
which turns flat geometry into something that reads as old and ruined:

```
//replace dimdescent:altar_stone_bricks 80%dimdescent:altar_stone_bricks,20%dimdescent:cracked_altar_stone_bricks
```
Weathers a whole room in one command.

```
//replace dimdescent:altar_stone_bricks 58%dimdescent:altar_stone_bricks,42%air
```
Run on a ring built with `//hcyl`, this collapses it into a broken colonnade. Build clean geometry,
then damage it — far better than trying to place ruins by hand.

**Layer cylinders for a dais.** Three `//cyl` at decreasing radius, one block apart vertically, gives
a stepped altar platform in six commands (`/tp ~ ~1 ~` between them).

**Lava that doesn't spread.** A lava source spreads into any adjacent *air*, so control the
neighbours, not the lava:

| | |
|---|---|
| **In the ceiling** | 4 solid sides, only down is open → a perfect 1-wide lavafall |
| Flush in the floor | `//set minecraft:lava` over a floor region → a lake or river you can walk into |
| In a wall | ✗ the room-facing side is air, so it pours out sideways |
| Floating in air | ✗ spreads in every direction |

Give a fall something to land in — existing lava, or a 1×1 pocket. And lava counts as *sealed* for
the Nullstone shrink-wrap (it isn't air), so a lava-filled ceiling block doesn't break the room.

## `//generate` — formula shapes

`//generate` (aliases `//gen`, `//g`) is real: it evaluates an expression at every block in the
selection and places the pattern wherever the result is non-zero.

**The one thing that trips everybody up:** by default `x`, `y` and `z` are *normalised* — each axis
runs about −1 to +1 across the selection, whatever its real size. So a formula written with block-unit
constants (`radius 10`, `y == 3`) evaluates to nothing at all. Either write the formula in −1…1 terms,
or pass one of the raw/offset coordinate flags (`-r`, `-o`). `-h` makes it hollow.

Normalised, so it needs no flags — a torus in whatever cube you select:

```
//g dimdescent:altar_stone_bricks (sqrt(x*x+z*z)-0.6)*(sqrt(x*x+z*z)-0.6)+y*y<0.0625
```

`0.6` is the major radius as a fraction of the selection, `0.0625` is the minor radius squared
(0.25²). `sqrt()` and plain multiplication avoid arguing with the parser about `^` vs `pow()`.

It's a **shape** tool, though. One pattern for the whole formula, no way to say "the top block is a
grate and the two under it are hollow", and it can't place a bed. For a room, generate a function.

## When commands aren't enough

Spirals, helicoids, rippled surfaces — anything per-block-mathematical — are far too many blocks to
paste as chat commands. Generate a **datapack function** instead. The scripts write thousands of
relative `setblock` lines into `run/saves/<world>/datapacks/dimdescent_build/`, and the whole build
runs from one command wherever you stand:

```
/reload
```
```
/function build:spiral
```

Build at an exact spot without having to stand there — far more reliable than flying to a coordinate,
since the function reads whatever block you happen to be in:

```bash
/execute positioned 100 0 100 run function build:causeway
```

To wipe one and start over, `/fill` the capture box (corners included, so the structure blocks go
too). Keep it under **32,768** blocks or `/fill` refuses — a 33 × 27 × 33 box is 29,403 and fits:

```bash
/fill 84 -1 84 116 25 116 air
```

Stand outside it first. The floor becoming air is a long way down.

| script | function | what it makes |
|---|---|---|
| `tools/generate_spiral_function.py` | `/function build:spiral` | helicoid stair tower, 17×41×17 |
| `tools/generate_basin_room.py` | `/function build:basin` | rippled floor + mirrored ceiling, 41×17×41 |
| `tools/generate_causeway_room.py` | `/function build:causeway` | Nullstone void with a brick walkway, 31×25×31 |
| `tools/generate_oubliette_room.py` | `/function build:oubliette` | nested rings, funnelled ceiling, 23×10×23 |
| `tools/generate_carpet_room.py` | `/function build:carpet` | Sierpinski-carpet floor over a drop, 33×27×33 |
| `tools/generate_unicursal_room.py` | `/function build:unicursal` | Hilbert-curve corridor maze, 21×7×21 |
| `tools/generate_hypostyle_room.py` | `/function build:hypostyle` | Cantor-dust colonnade, 47×47×47 |
| `tools/generate_lattice_room.py` | `/function build:lattice` | 3D Cantor dust in a black void, 35×35×35 |
| `tools/generate_knot_room.py` | `/function build:knot` | 3D Hilbert curve as pipe + cut bridge, 31×31×31 |

Two tricks worth stealing:

**Mirror the ceiling against the floor.** `ceiling = CLEARANCE - floor(r)`, not `+`. Running the two
surfaces antiparallel makes headroom swing by *twice* the ripple amplitude — a 13-block vault
pinching to a 3-block crawl on an amplitude of 3. Parallel surfaces read as a corridor; mirrored ones
read as a room being squeezed.

**Make ceiling height a function of distance from the centre.** `headroom(d) = BASE + round(d*SLOPE)`
turns a flat room into a funnel — Chebyshev distance (`max(|x|,|z|)`) for square rooms, Euclidean for
round. Land the steps mid-corridor rather than at the walls and there is never a doorway to brace
for; the room just quietly closes as you walk in. Costs the same number of blocks as a flat ceiling.

**Two-thick walls let a doorway be a lie.** Cut the door through the outer layer only and you get a
one-block-deep recess with a brick face behind it. Three bricked-up doors and one real one means
every exit is visible from the moment you arrive and you still have to walk the whole ring.

**Raise a walkway one block over a Nullstone floor.** Nullstone renders perfectly flat — no face
shading, no ambient occlusion, no lightmap falloff, because black times anything is black — so a
Nullstone floor is indistinguishable from a hole in the world. Put a lit brick path one block above
it and the room reads as a bridge over void. Stepping off is a one-block drop onto a floor the player
was sure wasn't there. Costs one block of elevation.

These scripts **check their own geometry** before writing: max floor step, minimum headroom, that a
walkway is one connected path rather than stranded islands, that a lava source is fully enclosed so
it can't spread, and a flood-fill proof that the room is sealed. A bad constant fails in the terminal
instead of after you've captured it.

## Don't

- **`/tp <x> <y> <z> <yaw> <pitch>`.** Not a valid overload — the rotation form needs an explicit
  target: `/tp @s 100 3 88 0 0`. Yaw 0 faces south (+Z), 90 west, 180 north, 270 east; pitch 0 is
  level. Plain `/tp <x> <y> <z>` is fine without a target, which is why this one surprises you.
- **`//set` then `//hollow`.** `//hollow` needs air around the object inside the selection; a solid that fills its box edge-to-edge gets deleted entirely. Use `//faces` or solid-then-carve.
- **Build round rooms on the ground.** A circle in a square capture box saves the grass in the corners, and it ends up in the Null Domain. Build in the air.
- **Draw floor, walls and ceiling as three separate player-centred shapes.** A fraction of a block of drift and the circles don't line up. Use `/tp ~ ~1 ~` (literal `~`) for height changes.
- **`//schem save`.** Wrong format — the mod reads vanilla structure NBT. Build with WorldEdit, save with Structure Blocks.

## Builder-world comfort

```
/gamerule doMobSpawning false
/gamerule doWeatherCycle false
/difficulty peaceful
/weather clear
/time set noon
```
