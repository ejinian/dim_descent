#!/usr/bin/env python3
"""Generate a .mcfunction that builds the Oasis.

    python3 tools/generate_oasis_room.py

THE IDEA
One oak tree, a pool of water, grass and flowers, in a small black room.

Nothing else in the Null Domain is alive. Every other room is brick, void and iron, so the single
most disturbing thing that can be put in here is not a monster - it is a lawn. A player who has spent
ten minutes in the Domain and then walks into somewhere with *daisies in it* has to decide what that
means, and there is no good answer.

IT IS NOT A FLOOR WITH GRASS ON IT
That was the version to avoid. This is a piece of ground, cut out and set down: the island rises in
three courses off the Nullstone in **stone, then dirt, then turf**, so the soil profile is visible in
section all the way round its edge. It reads as excavated rather than decorated - somebody took a
core of the overworld and left it here, and the black floor runs right up to the cut.

The steps are one block each, so the strata double as the way up onto it.

  A NOISY RIM.   The island's outline is a circle plus two sine terms, so it is irregular without
                 being random - a machined circle would give the game away instantly.
  LIFE FALLS OFF WITH WATER.  Plant density is a function of distance from the pool: thick at the
                 waterline, thinning through bare turf, gone before the edge. That is what an oasis
                 IS, and it means the green fades into the black rather than stopping at a line.
  ONE TREE.      Off-centre, beside the water, the way a real one would be.

TWO THINGS THAT WOULD SILENTLY BREAK IN GAME, BOTH ASSERTED
Leaves decay when they are placed away from a log, so every leaf is written with `persistent=true`
and the generator refuses to emit one that is not. And water spreads: every source block has to have
solid ground beneath it and water or solid on all four sides, or the pool empties itself across the
turf the moment the room is stamped. Neither failure is visible in the .nbt - only in play, later.

Grass, incidentally, does NOT need light to survive; GrassBlock only reverts to dirt when the block
ABOVE it blocks light, so a sealed lightless room is fine. It will never spread, which is the point -
this is not going anywhere.
"""

import math
import os
import random
from collections import deque

WORLD = "run/saves/DimDescentRoomBuilder"
PACK = f"{WORLD}/datapacks/dimdescent_build"
FUNC_DIR = f"{PACK}/data/build/function"
NAME = "oasis"

VOID = "dimdescent:nullstone"
STONE = "minecraft:stone"
DIRT = "minecraft:dirt"
TURF = "minecraft:grass_block[snowy=false]"
SAND = "minecraft:sand"
WATER = "minecraft:water[level=0]"
LOG = "minecraft:oak_log[axis=y]"
LEAF = "minecraft:oak_leaves[distance=7,persistent=true,waterlogged=false]"
SHORT_GRASS = "minecraft:short_grass"
DARK_BED = "dimdescent:dream_bed"
PALE_BED = "dimdescent:pale_dream_bed"

FLOWERS = ("minecraft:poppy", "minecraft:dandelion", "minecraft:azure_bluet",
           "minecraft:oxeye_daisy", "minecraft:cornflower", "minecraft:lily_of_the_valley")

HALL = 12          # interior half-width -> 25x25
TOP = 16           # interior height

ISLAND_R = 8.0     # mean radius of the turf island
RIM_WOBBLE = ((1.3, 3, 0.7), (0.8, 5, 2.1))   # (amplitude, lobes, phase) - noise on the outline
TERRACE = (0.82, 0.56)   # fractional radii where the island steps down 3 -> 2 -> 1 courses

POOL_AT = (-1, 1)  # pool centre, offset from the island's
POOL_R = 2.2
TREE_AT = (3, -2)  # trunk position, offset from the island's
TREE_H = 6

GRASS_NEAR = 0.75  # plant chance at the waterline...
GRASS_FAR = 0.05   # ...and at the island's edge
FLOWER_SHARE = 0.22

random.seed(20260814)


def main():
    def rim(x, z):
        """Island radius in this direction: a circle plus two sine terms."""
        t = math.atan2(z, x)
        return ISLAND_R + sum(a * math.sin(n * t + p) for a, n, p in RIM_WOBBLE)

    def courses(x, z):
        """How many blocks of ground stand here: 3 in the middle, stepping to 0 outside."""
        r = math.hypot(x, z)
        edge = rim(x, z)
        if r > edge:
            return 0
        d = r / edge
        return 1 if d > TERRACE[0] else (2 if d > TERRACE[1] else 3)

    ground = {}
    for x in range(-HALL, HALL + 1):
        for z in range(-HALL, HALL + 1):
            h = courses(x, z)
            if h:
                ground[(x, z)] = h

    blocks = {}

    def put(x, y, z, block):
        blocks[(x, y, z)] = block

    # Nullstone box, and the black floor the island sits on.
    skin = HALL + 2
    for x in range(-skin, skin + 1):
        for z in range(-skin, skin + 1):
            for y in range(-2, TOP + 3):
                if (max(abs(x), abs(z)) > HALL or y in (-2, -1, TOP + 1, TOP + 2)):
                    put(x, y, z, VOID)

    # The core: stone, then dirt, then turf, so the section shows at every step.
    surface = {}
    for (x, z), h in ground.items():
        for y in range(0, h):
            put(x, y, z, STONE if y <= h - 3 else (DIRT if y == h - 2 else TURF))
        surface[(x, z)] = h - 1

    # The pool, sunk into the top terrace, with a sand shore one ring wide.
    # A cell only gets water if IT and all four of its neighbours are on the top terrace. Without
    # that the pool can reach the step down, where the ground beside it is a block lower and the
    # water has open air at its own level - which drains it across the turf.
    def top_terrace(x, z):
        return courses(x, z) == 3

    pool = set()
    for (x, z), y in list(surface.items()):
        d = math.hypot(x - POOL_AT[0], z - POOL_AT[1])
        walled = all(top_terrace(x + dx, z + dz)
                     for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if d <= POOL_R and top_terrace(x, z) and walled:
            put(x, y, z, WATER)
            put(x, y - 1, z, SAND)
            pool.add((x, y, z))
        elif d <= POOL_R + 1.5 and courses(x, z) == 3:
            put(x, y, z, SAND)

    # One oak, beside the water.
    tx, tz = TREE_AT
    if (tx, tz) not in surface:
        raise AssertionError("the tree is off the island - move TREE_AT.")
    base = surface[(tx, tz)] + 1
    for y in range(base, base + TREE_H):
        put(tx, y, tz, LOG)
    crown = base + TREE_H - 1
    for dy, r in ((-2, 2.6), (-1, 2.6), (0, 1.8), (1, 1.2)):
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                if math.hypot(dx, dz) > r or (dx == 0 and dz == 0 and dy <= 0):
                    continue
                if math.hypot(dx, dz) > r - 0.7 and random.random() < 0.4:
                    continue          # ragged edge, so it is not a machined blob
                put(tx + dx, crown + dy, tz + dz, LEAF)

    # Vegetation, thickest at the waterline and gone before the rim.
    plants = 0
    for (x, z), y in surface.items():
        top = (x, y, z)
        if blocks.get(top) != TURF:
            continue
        above = (x, y + 1, z)
        if above in blocks:
            continue
        d = math.hypot(x - POOL_AT[0], z - POOL_AT[1])
        t = min(1.0, d / (ISLAND_R + 2))
        if random.random() > GRASS_NEAR + (GRASS_FAR - GRASS_NEAR) * t:
            continue
        put(above[0], above[1], above[2],
            random.choice(FLOWERS) if random.random() < FLOWER_SHARE else SHORT_GRASS)
        plants += 1

    pale_foot, pale_head = (0, 0, HALL - 1), (0, 0, HALL)
    dark_foot, dark_head = (0, 0, -(HALL - 1)), (0, 0, -HALL)

    # ---- checks ------------------------------------------------------------
    solid = set(blocks)

    for c in pool:
        if (c[0], c[1] - 1, c[2]) not in blocks:
            raise AssertionError(f"water at {c} has nothing under it - the pool will drain.")
        for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (c[0] + d[0], c[1], c[2] + d[1])
            if n not in blocks:
                raise AssertionError(
                    f"water at {c} is open at {n} - it will spread across the turf. Shrink POOL_R "
                    f"or move POOL_AT further from the island's edge.")

    for c, b in blocks.items():
        if "oak_leaves" in b and "persistent=true" not in b:
            raise AssertionError(f"leaf at {c} is not persistent - it will decay away in game.")
        if b in FLOWERS or b == SHORT_GRASS:
            under = blocks.get((c[0], c[1] - 1, c[2]), "")
            if "grass_block" not in under and under != DIRT:
                raise AssertionError(f"plant at {c} is standing on '{under}' and will pop off.")

    if crown + 1 >= TOP:
        raise AssertionError(f"the tree reaches y={crown + 1}, into the ceiling at {TOP}.")

    def standable(p):
        return ((p[0], p[1] - 1, p[2]) in solid
                and all((p[0], p[1] + k, p[2]) not in solid for k in (0, 1)))

    for label, p in (("pale", pale_foot), ("dark", dark_foot)):
        if not standable(p):
            raise AssertionError(f"the {label} Nexus at {p} is not standing on anything.")

    reach, queue = {pale_foot}, deque([pale_foot])
    while queue:
        x, y, z = queue.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for ny in (y + 1, y, y - 1):
                n = (x + dx, ny, z + dz)
                if max(abs(n[0]), abs(n[2])) <= HALL and n not in reach and standable(n):
                    reach.add(n)
                    queue.append(n)
                    break
    if dark_foot not in reach:
        raise AssertionError("the dark Nexus is unreachable on foot.")
    if not any(c in reach for c in
               [(x, surface[(x, z)] + 1, z) for (x, z) in surface if surface[(x, z)] == 2]):
        raise AssertionError("the top of the island cannot be walked onto - the terraces are too "
                             "steep. Widen TERRACE.")

    lo, hi = (-skin, -2, -skin), (skin, TOP + 2, skin)
    seen, queue = set(), deque()
    for x in range(lo[0], hi[0] + 1):
        for y in range(lo[1], hi[1] + 1):
            for z in range(lo[2], hi[2] + 1):
                if (x in (lo[0], hi[0]) or y == hi[1] or z in (lo[2], hi[2])) \
                        and (x, y, z) not in solid:
                    seen.add((x, y, z))
                    queue.append((x, y, z))
    while queue:
        x, y, z = queue.popleft()
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + d[0], y + d[1], z + d[2])
            if all(lo[i] <= n[i] <= hi[i] for i in range(3)) and n not in seen and n not in solid:
                seen.add(n)
                queue.append(n)
    if pale_foot in seen:
        raise AssertionError("room is NOT sealed - outside air reaches the floor.")

    # ---- emit --------------------------------------------------------------
    shift = 2
    lines = ["# The Oasis. Stand at the CENTRE of the room, in CLEAR AIR, and run:",
             f"#   /function build:{NAME}",
             "# Generated by tools/generate_oasis_room.py - do not hand-edit.", ""]
    # Ground before water, water before plants: a source block placed over a hole floods.
    order = {VOID: 0, STONE: 1, DIRT: 1, SAND: 1, TURF: 2, LOG: 3, LEAF: 3, WATER: 4}
    for c, b in sorted(blocks.items(), key=lambda kv: order.get(kv[1], 5)):
        lines.append(f"setblock ~{c[0]} ~{c[1] + shift} ~{c[2]} {b}")
    for block, foot, head, facing in ((PALE_BED, pale_foot, pale_head, "south"),
                                      (DARK_BED, dark_foot, dark_head, "north")):
        lines.append(f"setblock ~{foot[0]} ~{foot[1] + shift} ~{foot[2]} "
                     f"{block}[facing={facing},part=foot]")
        lines.append(f"setblock ~{head[0]} ~{head[1] + shift} ~{head[2]} "
                     f"{block}[facing={facing},part=head]")

    os.makedirs(FUNC_DIR, exist_ok=True)
    with open(f"{PACK}/pack.mcmeta", "w") as f:
        f.write('{\n  "pack": {\n    "pack_format": 48,\n'
                '    "description": "dim_descent build helpers"\n  }\n}\n')
    with open(f"{FUNC_DIR}/{NAME}.mcfunction", "w") as f:
        f.write("\n".join(lines) + "\n")

    span_xz, span_y = 2 * skin + 1, TOP + 5
    print(f"wrote {FUNC_DIR}/{NAME}.mcfunction")
    print(f"  {len(lines) - 4} setblock commands")
    print(f"  island {len(ground)} columns in 3 courses (stone/dirt/turf), rim noise on 3 and 5 lobes")
    print(f"  pool {len(pool)} water blocks, verified enclosed and bedded on sand")
    print(f"  one oak {TREE_H} tall, crown at y={crown}, all leaves persistent")
    print(f"  {plants} plants, {FLOWER_SHARE:.0%} of them flowers, density falling off from the water")
    print(f"  capture size {span_xz} x {span_y} x {span_xz} "
          f"({'OK' if max(span_xz, span_y) <= 48 else 'TOO BIG'} for the 48 cap)")
    print()
    print("  structure blocks, relative to the room CENTRE P:")
    print(f"    SAVE   corner at  P + ({-skin - 1}, -1, {-skin - 1})")
    print(f"    CORNER corner at  P + ({skin + 1}, {span_y}, {skin + 1})")


if __name__ == "__main__":
    main()
