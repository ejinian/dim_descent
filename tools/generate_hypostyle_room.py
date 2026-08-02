#!/usr/bin/env python3
"""Generate a .mcfunction that builds the Hypostyle.

    python3 tools/generate_hypostyle_room.py

THE IDEA
A hypostyle hall is a real thing - Karnak, the Mezquita - a roof carried entirely on columns, packed
so densely you can never see across the room. They are famously disorienting for one reason: there is
no vantage point. Wherever you stand, you see columns, and beyond them more columns.

This one is 47 x 47 x 47, the largest room vanilla structure blocks can hold, and the columns stand on
a **Cantor dust**: the two-dimensional product of a Cantor set with itself. Take the span, delete the
middle third, repeat three times, and do it on both axes. What comes out is sixty-four columns in a
pattern that is *self-similar* - pairs of columns one block apart, those pairs grouped four apart,
those groups fifteen apart. Three scales of aisle.

That is what a Cantor dust buys over a grid. In a regular colonnade you can count your way across. In
this one every view is a scaled copy of every other view, so the aisle you are standing in tells you
nothing about how far you have come or how much is left. It also has the property the room needs:
a Cantor dust is totally disconnected, so its **complement is connected** - the floor is walkable
everywhere, with no cell walled off, without having to design a single route.

The columns are 43 blocks tall and two blocks square, which is a ratio of 21:1. They should not stand
up and they read as though they only just do.

WHY THREE DARK NEXUS BEDS
The Domain is a tree the whole server explores, and until now every authored room has been a corridor
with one way on. A room this size earns being a junction: three dark Nexus beds on three sides, each
its own branch, and the walk between them long enough that choosing one means giving up the others
for this trip.
"""

import os
import random
from collections import deque

WORLD = "run/saves/DimDescentRoomBuilder"
PACK = f"{WORLD}/datapacks/dimdescent_build"
FUNC_DIR = f"{PACK}/data/build/function"
NAME = "hypostyle"

BRICK = "dimdescent:altar_stone_bricks"
CRACKED = "dimdescent:cracked_altar_stone_bricks"
VOID = "dimdescent:nullstone"
DARK_BED = "dimdescent:dream_bed"
PALE_BED = "dimdescent:pale_dream_bed"

# ---------------------------------------------------------------------------
# SHAPE. Sized to the 48-block structure-block cap: 47 in every axis is the ceiling, so this is the
# largest room the format can hold. Do not raise anything here.
# ---------------------------------------------------------------------------
HALL = 21             # interior half-width -> 43x43 of floor
WALL = HALL + 1       # brick outer wall
SKIN = WALL + 1       # Nullstone shell -> 47x47
TOP = 43              # interior headroom -> 47 tall overall

DEPTH = 3             # Cantor iterations. Three gives three scales of aisle.
MIN_SCALES = 3        # ...and this asserts they survived integer rounding

BROKEN_CHANCE = 0.20  # columns that gave up partway
BROKEN_MIN = 6

CRACK_CHANCE = 0.20
random.seed(20260805)


def brick():
    return CRACKED if random.random() < CRACK_CHANCE else BRICK


def cantor(lo, hi, depth):
    """Indices surviving `depth` middle-third deletions on the inclusive span lo..hi."""
    span = hi - lo + 1
    if depth == 0 or span < 3:
        return set(range(lo, hi + 1))
    third = span / 3
    return (cantor(lo, round(lo + third) - 1, depth - 1)
            | cantor(round(lo + 2 * third), hi, depth - 1))


def main():
    span = 2 * HALL + 1
    keep = sorted(cantor(0, span - 1, DEPTH))
    axis = {i - HALL for i in keep}

    # The aisles between the bands must come in at least MIN_SCALES distinct widths, or integer
    # rounding has flattened the recursion into a plain grid and none of this was worth doing.
    gaps = sorted({b - a - 1 for a, b in zip(keep, keep[1:]) if b - a > 1})
    if len(gaps) < MIN_SCALES:
        raise AssertionError(
            f"only {len(gaps)} aisle widths ({gaps}) - the Cantor recursion collapsed under rounding. "
            f"Lower DEPTH or widen HALL.")

    # The surviving indices come in consecutive runs - those runs are the columns. Group them, so a
    # column is one object that stands or snaps as a whole rather than four cells deciding separately.
    bands = []
    for i in keep:
        if bands and i == bands[-1][-1] + 1:
            bands[-1].append(i)
        else:
            bands.append([i])
    bands = [[i - HALL for i in b] for b in bands]
    columns = [(bx, bz) for bx in bands for bz in bands]
    blocks = {}

    def put(x, y, z, block):
        blocks[(x, y, z)] = block

    for x in range(-SKIN, SKIN + 1):
        for z in range(-SKIN, SKIN + 1):
            put(x, -1, z, VOID)
            put(x, TOP + 2, z, VOID)
            d = max(abs(x), abs(z))
            if d == SKIN:
                for y in range(0, TOP + 2):
                    put(x, y, z, VOID)
            elif d == WALL:
                for y in range(0, TOP + 2):
                    put(x, y, z, brick())
            else:
                put(x, 0, z, brick())
                put(x, TOP + 1, z, brick())

    standing = 0
    for bx, bz in columns:
        broken = random.random() < BROKEN_CHANCE
        base = random.randint(BROKEN_MIN, TOP - 4) if broken else TOP
        if not broken:
            standing += 1
        for x in bx:
            for z in bz:
                # A snapped column shears unevenly, so each of its cells stops a block or two apart.
                top = base if not broken else max(1, base - random.randint(0, 2))
                for y in range(1, top + 1):
                    put(x, y, z, brick())

    # ---- checks ------------------------------------------------------------
    solid = set(blocks)

    def open_cell(x, z):
        return all((x, y, z) not in solid for y in (1, 2))

    pale = (0, HALL - 1)
    darks = [(0, -(HALL - 1)), (-(HALL - 1), 0), (HALL - 1, 0)]
    beds = [("pale", pale, "south"), ("dark 1", darks[0], "north"),
            ("dark 2", darks[1], "west"), ("dark 3", darks[2], "east")]
    step = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
    for label, cell, facing in beds:
        head = (cell[0] + step[facing][0], cell[1] + step[facing][1])
        for p in (cell, head):
            if not open_cell(*p):
                raise AssertionError(f"the {label} Nexus cell {p} is inside a column.")

    seen, queue = {pale}, deque([pale])
    while queue:
        x, z = queue.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, z + dz)
            if abs(n[0]) <= HALL and abs(n[1]) <= HALL and n not in seen and open_cell(*n):
                seen.add(n)
                queue.append(n)
    for label, cell, _ in beds[1:]:
        if cell not in seen:
            raise AssertionError(f"the {label} Nexus at {cell} is not reachable on foot.")
    floor_cells = sum(1 for x in range(-HALL, HALL + 1) for z in range(-HALL, HALL + 1)
                      if open_cell(x, z))
    if len(seen) != floor_cells:
        raise AssertionError(
            f"{floor_cells - len(seen)} floor cells are walled off. A Cantor dust is totally "
            f"disconnected, so its complement must be connected - the column set is wrong.")

    lo, hi = (-SKIN - 1, 0, -SKIN - 1), (SKIN + 1, TOP + 3, SKIN + 1)
    seen3, queue = set(), deque()
    for x in range(lo[0], hi[0] + 1):
        for y in range(lo[1], hi[1] + 1):
            for z in range(lo[2], hi[2] + 1):
                if (x in (lo[0], hi[0]) or y == hi[1] or z in (lo[2], hi[2])) \
                        and (x, y, z) not in solid:
                    seen3.add((x, y, z))
                    queue.append((x, y, z))
    while queue:
        x, y, z = queue.popleft()
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + d[0], y + d[1], z + d[2])
            if all(lo[i] <= n[i] <= hi[i] for i in range(3)) and n not in seen3 and n not in solid:
                seen3.add(n)
                queue.append(n)
    if (pale[0], 1, pale[1]) in seen3:
        raise AssertionError("room is NOT sealed - outside air reaches the hall.")

    # ---- emit --------------------------------------------------------------
    shift = 1
    lines = [f"# The Hypostyle. Stand at the CENTRE of the room, on the ground, in CLEAR AIR, and run:",
             f"#   /function build:{NAME}",
             f"# ~{len(blocks) // 1000}k setblocks in one tick - expect a pause of a second or two.",
             f"# Generated by tools/generate_hypostyle_room.py - do not hand-edit.", ""]
    for (x, y, z), block in blocks.items():
        lines.append(f"setblock ~{x} ~{y + shift} ~{z} {block}")

    for _, cell, facing in beds:
        block = PALE_BED if cell == pale else DARK_BED
        hx, hz = cell[0] + step[facing][0], cell[1] + step[facing][1]
        lines.append(f"setblock ~{cell[0]} ~{1 + shift} ~{cell[1]} {block}[facing={facing},part=foot]")
        lines.append(f"setblock ~{hx} ~{1 + shift} ~{hz} {block}[facing={facing},part=head]")

    os.makedirs(FUNC_DIR, exist_ok=True)
    with open(f"{PACK}/pack.mcmeta", "w") as f:
        f.write('{\n  "pack": {\n    "pack_format": 48,\n'
                '    "description": "dim_descent build helpers"\n  }\n}\n')
    with open(f"{FUNC_DIR}/{NAME}.mcfunction", "w") as f:
        f.write("\n".join(lines) + "\n")

    span_xz, span_y = 2 * SKIN + 1, TOP + 4
    print(f"wrote {FUNC_DIR}/{NAME}.mcfunction")
    print(f"  {len(lines) - 5} setblock commands")
    print(f"  {len(columns)} columns of {len(bands[0])}x{len(bands[0])}, {standing} full height, "
          f"{len(columns) - standing} snapped")
    print(f"  aisle widths {gaps} - {len(gaps)} distinct scales")
    print(f"  1 pale + 3 dark Nexus beds, all reachable; {floor_cells} floor cells, all connected")
    print(f"  capture size {span_xz} x {span_y} x {span_xz} "
          f"({'OK' if max(span_xz, span_y) <= 48 else 'TOO BIG'} for the 48 cap)")
    print()
    print("  structure blocks, standing at the run position P:")
    print(f"    SAVE   corner at  P + ({-SKIN - 1}, -1, {-SKIN - 1})")
    print(f"    CORNER corner at  P + ({SKIN + 1}, {span_y}, {SKIN + 1})")


if __name__ == "__main__":
    main()
