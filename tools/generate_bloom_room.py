#!/usr/bin/env python3
"""Generate a .mcfunction that builds the Bloom.

    python3 tools/generate_bloom_room.py

THE IDEA
Every other room in the pool was drawn. This one was grown.

A plain brick chamber, and hanging out of its ceiling, something that got in. It is the
**Ulam-Warburton automaton** in three dimensions, and the entire rule is one line:

    a cell switches on if EXACTLY ONE of its six neighbours is already on.

That is all of it. There is no randomness anywhere in this room - run it twice and you get the same
crystal - and yet what comes out is a branching, six-armed dendrite nobody designed. The reason is
the word "exactly": a cell touching two live neighbours is refused, so growth can never fill in
behind itself. It can only push outward along fronts that are one cell thick, which forces it to keep
splitting, and the splits split. Ulam-Warburton is a genuine fractal - its live-cell count is not a
polynomial in the generation number - and it is the shortest rule I know that produces something that
looks alive.

WHAT I TRIED FIRST, AND WHY THIS IS BETTER
The first pass used diffusion-limited aggregation - random walkers wandering until they stick. It is
the more famous growth process and it makes lovely dendrites, but in a closed box it is enormously
wasteful: walkers pile up against the ceiling and expire without ever finding the cluster. Doubling
the particle count from 2600 to 5200 moved the yield from 825 cells to 859, which is the point at
which the model is fighting you. The automaton does the same job deterministically, in one pass, with
no dials to tune.

The early generations come out as cracked brick and the late ones as Dark Iron Bars, so the thing
thins into filaments the further it gets from where it started.

WHY IT IS IN A PLAIN ROOM
The chamber around it is deliberately ordinary - flat brick floor, flat walls, right angles. The
growth is only unsettling in contrast with something built. Hang it in a black void and it is an
abstract sculpture; hang it out of a ceiling somebody laid brick by brick and it is an infestation.
"""

import os
import random
from collections import deque

WORLD = "run/saves/DimDescentRoomBuilder"
PACK = f"{WORLD}/datapacks/dimdescent_build"
FUNC_DIR = f"{PACK}/data/build/function"
NAME = "bloom"

BRICK = "dimdescent:altar_stone_bricks"
CRACKED = "dimdescent:cracked_altar_stone_bricks"
BARS = "dimdescent:dark_iron_bars"
VOID = "dimdescent:nullstone"
DARK_BED = "dimdescent:dream_bed"
PALE_BED = "dimdescent:pale_dream_bed"

# ---------------------------------------------------------------------------
# THE CHAMBER
# ---------------------------------------------------------------------------
HALL = 15          # interior half-width -> 31x31 floor
TOP = 22           # ceiling; the growth hangs from here
HEADROOM = 4       # the growth may not descend below this, or it becomes an obstacle

# ---------------------------------------------------------------------------
# THE GROWTH. GENS is the only real dial: too few and it is a few sad stalactites, too many and it
# closes into a solid slab and stops looking grown. There is no randomness in the rule itself - the
# seed positions are the only thing chance touches.
# ---------------------------------------------------------------------------
SEEDS = 5          # starting cells on the ceiling. More seeds = more collisions = less symmetry.
GENS = 16          # generations to run. Growth radius is roughly one cell per generation.
HEADROOM = 4       # the growth may not descend below this, or it becomes an obstacle
TRUNK_FRACTION = 0.45   # the first this-much of the generations are trunk, the rest filament

MIN_CELLS = 900    # a growth thinner than this is not worth a room
MAX_FILL = 0.16    # ...and one thicker than this has closed up into a slab

random.seed(20260811)

DIRS = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))


def main():
    def inside(c):
        return (abs(c[0]) <= HALL - 1 and abs(c[2]) <= HALL - 1
                and HEADROOM < c[1] <= TOP)

    stuck = set()
    for _ in range(SEEDS):
        stuck.add((random.randint(-HALL + 4, HALL - 4), TOP,
                   random.randint(-HALL + 4, HALL - 4)))
    born = {c: 0 for c in stuck}

    # The automaton. One rule, no randomness: switch on if EXACTLY ONE neighbour is already on.
    # "Exactly" is what does the work - a cell with two live neighbours is refused, so the growth can
    # never fill in behind itself and is forced to keep splitting into thinner fronts.
    for gen in range(1, GENS + 1):
        counts = {}
        for c in stuck:
            for d in DIRS:
                n = (c[0] + d[0], c[1] + d[1], c[2] + d[2])
                if n in stuck or not inside(n):
                    continue
                counts[n] = counts.get(n, 0) + 1
        new = [n for n, k in counts.items() if k == 1]
        if not new:
            break
        for n in new:
            stuck.add(n)
            born[n] = gen

    lowest = min(c[1] for c in stuck)

    if len(stuck) < MIN_CELLS:
        raise AssertionError(f"only {len(stuck)} cells grew, under MIN_CELLS {MIN_CELLS} - raise "
                             f"GENS.")
    volume = (2 * HALL + 1) ** 2 * (TOP - HEADROOM)
    fill = len(stuck) / volume
    if fill > MAX_FILL:
        raise AssertionError(f"the growth fills {fill:.0%} of the room, over MAX_FILL - it has "
                             f"closed into a slab and stopped reading as grown.")

    # Everything must hang off the ceiling. A floating clump means a particle stuck to nothing, which
    # would be a bug in the walk rather than a feature of the growth.
    seen, queue = set(), deque()
    for c in stuck:
        if c[1] == TOP:
            seen.add(c)
            queue.append(c)
    while queue:
        c = queue.popleft()
        for d in DIRS:
            n = (c[0] + d[0], c[1] + d[1], c[2] + d[2])
            if n in stuck and n not in seen:
                seen.add(n)
                queue.append(n)
    if seen != stuck:
        raise AssertionError(f"{len(stuck - seen)} cells are not attached to the ceiling.")

    blocks = {}

    def put(c, block):
        blocks[c] = block

    skin, wall = HALL + 2, HALL + 1
    for x in range(-skin, skin + 1):
        for z in range(-skin, skin + 1):
            for y in range(-2, TOP + 3):
                d = max(abs(x), abs(z))
                if d == skin or y in (-2, TOP + 2):
                    put((x, y, z), VOID)
                elif d == wall or y in (-1, TOP + 1):
                    put((x, y, z), BRICK if random.random() > 0.18 else CRACKED)
    for x in range(-HALL, HALL + 1):
        for z in range(-HALL, HALL + 1):
            put((x, 0, z), BRICK if random.random() > 0.18 else CRACKED)
            put((x, TOP, z), BRICK if random.random() > 0.18 else CRACKED)

    # Trunk where the growth is thick, filament where it is not. A wandering particle is unlikely to
    # reach deep into the structure, so the outer tips are naturally the thinnest part.
    cutoff = max(born.values()) * TRUNK_FRACTION
    trunks = 0
    for c in stuck:
        if c[1] == TOP:
            continue
        if born[c] <= cutoff:
            put(c, CRACKED)
            trunks += 1
        else:
            put(c, BARS)

    pale_foot, pale_head = (0, 1, HALL - 2), (0, 1, HALL - 1)
    dark_foot, dark_head = (0, 1, -(HALL - 2)), (0, 1, -(HALL - 1))

    # ---- checks ------------------------------------------------------------
    solid = set(blocks)

    def standable(p):
        return ((p[0], p[1] - 1, p[2]) in solid
                and all((p[0], p[1] + k, p[2]) not in solid for k in (0, 1)))

    for label, p in (("pale", (0, 1, HALL - 2)), ("dark", (0, 1, -(HALL - 2)))):
        if not standable(p):
            raise AssertionError(f"the {label} Nexus at {p} is blocked.")

    reach, queue = {(0, 1, HALL - 2)}, deque([(0, 1, HALL - 2)])
    while queue:
        p = queue.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (p[0] + dx, 1, p[2] + dz)
            if max(abs(n[0]), abs(n[2])) <= HALL and n not in reach and standable(n):
                reach.add(n)
                queue.append(n)
    if (0, 1, -(HALL - 2)) not in reach:
        raise AssertionError("the dark Nexus is unreachable - the growth has reached the floor.")

    # ---- emit --------------------------------------------------------------
    shift = 2
    lines = ["# The Bloom. Stand at the CENTRE of the room, on the ground, in CLEAR AIR, and run:",
             f"#   /function build:{NAME}",
             "# Generated by tools/generate_bloom_room.py - do not hand-edit.", ""]
    for (x, y, z), block in blocks.items():
        lines.append(f"setblock ~{x} ~{y + shift} ~{z} {block}")
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
    print(f"  Ulam-Warburton 3D: {len(stuck)} cells from {SEEDS} seeds over "
          f"{max(born.values())} generations")
    print(f"  {trunks} trunk, {len(stuck) - trunks - SEEDS} filament; fills {fill:.1%} of the room")
    print(f"  reaches down to y={lowest} (floor is 0, walkway kept clear below {HEADROOM})")
    print(f"  all cells verified attached to the ceiling; both Nexus beds reachable")
    print(f"  capture size {span_xz} x {span_y} x {span_xz} "
          f"({'OK' if max(span_xz, span_y) <= 48 else 'TOO BIG'} for the 48 cap)")
    print()
    print("  structure blocks, relative to the room CENTRE P:")
    print(f"    SAVE   corner at  P + ({-skin - 1}, -1, {-skin - 1})")
    print(f"    CORNER corner at  P + ({skin + 1}, {span_y}, {skin + 1})")


if __name__ == "__main__":
    main()
