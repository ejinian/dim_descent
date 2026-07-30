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

## Counting / checking

| | |
|---|---|
| `//count B` | how many of that block are in the selection |
| `//distr` | full block breakdown of the selection |

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

**Clear terrain out of a capture box**
```
//replace minecraft:grass_block minecraft:air
//replace minecraft:dirt minecraft:air
```

---

## Don't

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
