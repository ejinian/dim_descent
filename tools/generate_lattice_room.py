#!/usr/bin/env python3
"""Generate a .mcfunction that builds the Lattice.

    python3 tools/generate_lattice_room.py

WHAT WENT WRONG LAST TIME
The Hypostyle's fractal was a floor PLAN, extruded straight up into columns. You cannot see a floor
plan while standing on it, so from inside it read as a big room full of pillars and nothing more. The
lesson generalises: an extruded 2D fractal is invisible from the one place the player will ever be.
A fractal room has to recurse in all three axes, or it is decoration on a map nobody looks at.

THE IDEA
A three-dimensional Cantor dust. Take a 27-block cube, cut it into 27, keep only the 8 corner pieces,
and repeat twice more. What survives is 512 single blocks - and *nothing else*. No floor to them, no
support, no connection. They hang in a room whose every surface is Nullstone, which is pure flat
black with no shading at all, so the walls and floor and ceiling read as absence. The blocks appear
to be suspended in nothing.

It is legible instantly, and it is legible as maths rather than as architecture, because of how the
survivors sit: eight blocks at the corners of a 3x3x3 cube; eight of THOSE clusters at the corners of
a 9x9x9; eight of those at the corners of the whole. Cubes of cubes of cubes, three deep, with the
gaps between them 1, 3 and 9 blocks. You are not looking at something built. You are looking at an
index.

WHY A CANTOR DUST AND NOT A SIERPINSKI TETRAHEDRON
The tetrix is the obvious 3D pick and it is the wrong one here. Its projection along every coordinate
axis is a *filled square*, so viewed straight down any wall of a rectangular room it collapses into a
solid slab and the recursion vanishes. A Cantor dust projects to a Cantor dust on all three axes.
There is no angle from which this resolves into something simple, which is exactly the property the
room is trading on.

THE ROOM SHOULD NOT EXIST
Nothing here is load-bearing, nothing is connected, nothing is a surface. Every rule the rest of the
mod's architecture follows - rooms are built, walls hold things up, floors are floors - is simply
absent. It is a coordinate set that got rendered.
"""

import os
from collections import deque

WORLD = "run/saves/DimDescentRoomBuilder"
PACK = f"{WORLD}/datapacks/dimdescent_build"
FUNC_DIR = f"{PACK}/data/build/function"
NAME = "lattice"

BRICK = "dimdescent:altar_stone_bricks"
VOID = "dimdescent:nullstone"
DARK_BED = "dimdescent:dream_bed"
PALE_BED = "dimdescent:pale_dream_bed"

# ---------------------------------------------------------------------------
# SHAPE
#
# LEVELS is what makes it a fractal rather than a grid; 3 is the most that fits, since a level-N
# Cantor dust spans 3^N and 81 is over the structure cap.
# ---------------------------------------------------------------------------
LEVELS = 3
SPAN = 3 ** LEVELS         # 27
HALF = SPAN // 2           # 13 -> dust occupies -13..13 on x and z

HALL = 15                  # interior half-width -> 31x31 of floor
SKIN = HALL + 2            # TWO layers of Nullstone, so the inner face is black too -> 35x35
BASE_Y = 1                 # lowest dust layer, one block proud of the floor
TOP = 31                   # interior headroom

MIN_SCALES = 3             # the gaps must come in three distinct widths or it is just a grid


def cantor_axis():
    """Indices of 0..SPAN-1 whose base-3 digits are all 0 or 2 - the discrete Cantor set."""
    keep = []
    for i in range(SPAN):
        d, ok = i, True
        for _ in range(LEVELS):
            if d % 3 == 1:
                ok = False
                break
            d //= 3
        if ok:
            keep.append(i)
    return keep


def main():
    keep = cantor_axis()
    gaps = sorted({b - a - 1 for a, b in zip(keep, keep[1:]) if b - a > 1})
    if len(gaps) < MIN_SCALES:
        raise AssertionError(f"only {len(gaps)} gap widths ({gaps}) - this is a grid, not a fractal.")
    if len(keep) != 2 ** LEVELS:
        raise AssertionError(f"kept {len(keep)} indices, expected {2 ** LEVELS}.")

    dust = {(x - HALF, y + BASE_Y, z - HALF) for x in keep for y in keep for z in keep}

    blocks = {}

    def put(x, y, z, block):
        blocks[(x, y, z)] = block

    # The box. Every face two layers thick and Nullstone throughout, so the surface the player looks
    # at is as black as the one behind it. There is nothing in here to judge distance against.
    for x in range(-SKIN, SKIN + 1):
        for z in range(-SKIN, SKIN + 1):
            for y in (-1, 0, TOP + 1, TOP + 2):
                put(x, y, z, VOID)
            if max(abs(x), abs(z)) > HALL:
                for y in range(-1, TOP + 3):
                    put(x, y, z, VOID)

    for p in dust:
        put(p[0], p[1], p[2], BRICK)

    # ---- checks ------------------------------------------------------------
    solid = set(blocks)

    # Every block must be isolated - a Cantor dust is totally disconnected, and if any two of these
    # touch, the recursion has collapsed and they would read as clumps instead of an index.
    for p in dust:
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            if (p[0] + d[0], p[1] + d[1], p[2] + d[2]) in dust:
                raise AssertionError(f"dust blocks touch at {p} - the set is not disconnected.")

    def standable(p):
        x, y, z = p
        return ((x, y - 1, z) in solid
                and (x, y, z) not in solid and (x, y + 1, z) not in solid)

    pale, dark = (0, HALL - 1), (0, -(HALL - 1))
    start, goal = (pale[0], 1, pale[1]), (dark[0], 1, dark[1])
    for label, cell in (("pale", start), ("dark", goal)):
        if not standable(cell):
            raise AssertionError(f"the {label} Nexus cell {cell} is blocked.")

    reach, queue = {start}, deque([start])
    while queue:
        x, y, z = queue.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if max(abs(nx), abs(nz)) > HALL:
                continue
            for ny in (y + 1, y, y - 1):          # dust blocks on the floor are stepped over
                if standable((nx, ny, nz)):
                    if (nx, ny, nz) not in reach:
                        reach.add((nx, ny, nz))
                        queue.append((nx, ny, nz))
                    break
    if goal not in reach:
        raise AssertionError("the dark Nexus is unreachable on foot.")

    lo, hi = (-SKIN - 1, 0, -SKIN - 1), (SKIN + 1, TOP + 3, SKIN + 1)
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
    if start in seen:
        raise AssertionError("room is NOT sealed - outside air reaches the hall.")

    # ---- emit --------------------------------------------------------------
    shift = 1
    lines = [f"# The Lattice. Stand at the CENTRE of the room, on the ground, in CLEAR AIR, and run:",
             f"#   /function build:{NAME}",
             f"# Generated by tools/generate_lattice_room.py - do not hand-edit.", ""]
    for (x, y, z), block in blocks.items():
        lines.append(f"setblock ~{x} ~{y + shift} ~{z} {block}")

    for block, cell, facing in ((PALE_BED, pale, "south"), (DARK_BED, dark, "north")):
        dz = 1 if facing == "south" else -1
        lines.append(f"setblock ~{cell[0]} ~{1 + shift} ~{cell[1]} {block}[facing={facing},part=foot]")
        lines.append(f"setblock ~{cell[0]} ~{1 + shift} ~{cell[1] + dz} {block}[facing={facing},part=head]")

    os.makedirs(FUNC_DIR, exist_ok=True)
    with open(f"{PACK}/pack.mcmeta", "w") as f:
        f.write('{\n  "pack": {\n    "pack_format": 48,\n'
                '    "description": "dim_descent build helpers"\n  }\n}\n')
    with open(f"{FUNC_DIR}/{NAME}.mcfunction", "w") as f:
        f.write("\n".join(lines) + "\n")

    # Build runs y = -1 (outer floor) .. TOP + 2 (outer ceiling), inclusive.
    span_xz, span_y = 2 * SKIN + 1, (TOP + 2) - (-1) + 1
    lo_y = min(p[1] for p in blocks)
    hi_y = max(p[1] for p in blocks)
    if hi_y - lo_y + 1 != span_y:
        raise AssertionError(f"height bookkeeping is wrong: blocks span {hi_y - lo_y + 1}, "
                             f"reporting {span_y}. The capture box would be the wrong size.")
    print(f"wrote {FUNC_DIR}/{NAME}.mcfunction")
    print(f"  {len(lines) - 4} setblock commands")
    print(f"  {len(dust)} suspended blocks, level-{LEVELS} 3D Cantor dust, all isolated")
    print(f"  gaps {gaps} - {len(gaps)} distinct scales, on all three axes")
    print(f"  capture size {span_xz} x {span_y} x {span_xz} "
          f"({'OK' if max(span_xz, span_y) <= 48 else 'TOO BIG'} for the 48 cap)")
    print()
    print("  structure blocks, standing at the run position P:")
    print(f"    SAVE   corner at  P + ({-SKIN - 1}, -1, {-SKIN - 1})")
    print(f"    CORNER corner at  P + ({SKIN + 1}, {span_y}, {SKIN + 1})")


if __name__ == "__main__":
    main()
