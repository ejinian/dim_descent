#!/usr/bin/env python3
"""Generate a .mcfunction that builds the Pyramid.

    python3 tools/generate_pyramid_room.py

THE IDEA
A hollow square pyramid, in altar brick, with a single lava source set in the underside of its
capstone. The room is one shape and one event: a vast empty stepped void, and a column of fire
falling forty-three blocks from the apex to a basin sunk into the middle of the floor.

The two Nexus beds sit on opposite sides of the base, so crossing the room means walking around the
fall. There is nothing else in here. It does not need anything else.

STEEPNESS IS THE WHOLE LOOK
`RISE` is how many blocks the wall climbs before stepping one block inward. At RISE 1 the interior is
a 45-degree staircase you can simply walk up to the apex, which makes the room a climbable ramp and
kills it. At RISE 2 every ledge is a two-block riser - too tall to step or jump onto - so the player
is held on the floor and the pyramid stays something you look up into. That single constant is the
difference between a stairwell and a tomb.

Height is `2 * BASE` at RISE 2, which is steeper than Giza and deliberately so; a real pyramid's
proportions read as squat once you are inside one.

THE LAVA CANNOT SPREAD, AND THAT IS ARITHMETIC
The topmost interior cell is the one directly under the capstone, and at that height the wall ring
has closed to a half-width of one - so the cell has brick on all four sides and brick above, with the
only opening downward. That is a guaranteed one-block fall, not a lucky one, and the generator
asserts it rather than hoping. It lands in a 5x5 basin two deep, which is wider than lava's
three-block spread, so it pools instead of running out across the floor.
"""

import os
import random
from collections import deque

WORLD = "run/saves/DimDescentRoomBuilder"
PACK = f"{WORLD}/datapacks/dimdescent_build"
FUNC_DIR = f"{PACK}/data/build/function"
NAME = "pyramid"

BRICK = "dimdescent:altar_stone_bricks"
CRACKED = "dimdescent:cracked_altar_stone_bricks"
LAVA = "minecraft:lava"
DARK_BED = "dimdescent:dream_bed"
PALE_BED = "dimdescent:pale_dream_bed"

# ---------------------------------------------------------------------------
# SHAPE. Capture is (2*BASE+1) wide by (2*BASE + FOUNDATION + 1) tall, so BASE 22 sits just inside
# the 48-block structure cap at 45 x 47 x 45.
# ---------------------------------------------------------------------------
BASE = 22             # half-width of the base course -> 45x45 footprint
RISE = 2              # wall blocks climbed per block stepped inward. 2 = unclimbable, on purpose.
FOUNDATION = 2        # solid brick courses under the floor, so the basin has something to sit in

BASIN = 2             # half-width of the lava basin -> 5x5, wider than lava's 3-block spread
BASIN_DEPTH = 2

BED_OFFSET = 3        # how far in from the base wall the beds sit
CRACK_CHANCE = 0.20
random.seed(20260806)


def brick():
    return CRACKED if random.random() < CRACK_CHANCE else BRICK


def ring_at(y):
    """Half-width of the wall course at height y. Zero at the apex."""
    return BASE - y // RISE


def main():
    apex = RISE * BASE                      # y of the capstone
    blocks = {}

    def put(x, y, z, block):
        blocks[(x, y, z)] = block

    # Foundation and floor, across the full footprint.
    for x in range(-BASE, BASE + 1):
        for z in range(-BASE, BASE + 1):
            for y in range(-FOUNDATION, 0):
                put(x, y, z, brick())
            put(x, 0, z, brick())

    # The pyramid itself: one square ring per course, shrinking inward.
    for y in range(0, apex + 1):
        w = ring_at(y)
        for x in range(-w, w + 1):
            for z in range(-w, w + 1):
                if max(abs(x), abs(z)) == w:
                    put(x, y, z, brick())

    # Sink the basin. The bottom course of the foundation is left intact underneath it.
    for x in range(-BASIN, BASIN + 1):
        for z in range(-BASIN, BASIN + 1):
            for y in range(-BASIN_DEPTH + 1, 1):
                blocks.pop((x, y, z), None)

    lava = (0, apex - 1, 0)

    # ---- checks ------------------------------------------------------------
    solid = set(blocks)

    if ring_at(lava[1]) != 1:
        raise AssertionError(
            f"the cell under the capstone has ring half-width {ring_at(lava[1])}, not 1 - the lava "
            f"would not be boxed in. Check RISE divides into BASE cleanly.")
    for d in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0)):
        n = (lava[0] + d[0], lava[1] + d[1], lava[2] + d[2])
        if n not in solid:
            raise AssertionError(f"lava source has an open side at {n} - it would spread.")
    if (lava[0], lava[1] - 1, lava[2]) in solid:
        raise AssertionError("nothing under the lava source - it cannot fall.")
    if BASIN < 2:
        raise AssertionError("basin is narrower than lava's 3-block spread; it would run out.")

    # The fall must be clear all the way down, or it puddles on a ledge halfway.
    for y in range(1, lava[1]):
        if (0, y, 0) in solid:
            raise AssertionError(f"the fall is obstructed at (0,{y},0).")

    def standable(p):
        x, y, z = p
        return ((x, y - 1, z) in solid
                and all((x, y + k, z) not in solid for k in (0, 1)))

    inner = BASE - 1
    pale, dark = (0, inner - BED_OFFSET), (0, -(inner - BED_OFFSET))
    start, goal = (pale[0], 1, pale[1]), (dark[0], 1, dark[1])
    for label, p in (("pale", start), ("dark", goal)):
        if not standable(p):
            raise AssertionError(f"the {label} Nexus at {p} is not standing on anything.")

    reach, queue = {start}, deque([start])
    while queue:
        x, y, z = queue.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, y, z + dz)
            if max(abs(n[0]), abs(n[2])) <= inner and n not in reach and standable(n):
                reach.add(n)
                queue.append(n)
    if goal not in reach:
        raise AssertionError("the dark Nexus is unreachable - the basin has cut the floor in two.")

    lo, hi = (-BASE - 1, -FOUNDATION, -BASE - 1), (BASE + 1, apex + 1, BASE + 1)
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
        raise AssertionError("room is NOT sealed - outside air reaches the floor. A 1-block sloped "
                             "shell should be airtight; check ring_at().")

    # ---- emit --------------------------------------------------------------
    shift = FOUNDATION
    lines = [f"# The Pyramid. Stand at the CENTRE of the room, on the ground, in CLEAR AIR, and run:",
             f"#   /function build:{NAME}",
             f"# Generated by tools/generate_pyramid_room.py - do not hand-edit.", ""]
    for (x, y, z), block in blocks.items():
        lines.append(f"setblock ~{x} ~{y + shift} ~{z} {block}")

    for block, cell, facing in ((PALE_BED, pale, "south"), (DARK_BED, dark, "north")):
        dz = 1 if facing == "south" else -1
        lines.append(f"setblock ~{cell[0]} ~{1 + shift} ~{cell[1]} {block}[facing={facing},part=foot]")
        lines.append(f"setblock ~{cell[0]} ~{1 + shift} ~{cell[1] + dz} "
                     f"{block}[facing={facing},part=head]")

    # Fluid last, so the shell is finished before anything can move.
    lines.append(f"setblock ~{lava[0]} ~{lava[1] + shift} ~{lava[2]} {LAVA}")

    os.makedirs(FUNC_DIR, exist_ok=True)
    with open(f"{PACK}/pack.mcmeta", "w") as f:
        f.write('{\n  "pack": {\n    "pack_format": 48,\n'
                '    "description": "dim_descent build helpers"\n  }\n}\n')
    with open(f"{FUNC_DIR}/{NAME}.mcfunction", "w") as f:
        f.write("\n".join(lines) + "\n")

    lo_y, hi_y = min(p[1] for p in blocks), max(p[1] for p in blocks)
    span_xz, span_y = 2 * BASE + 1, hi_y - lo_y + 1
    print(f"wrote {FUNC_DIR}/{NAME}.mcfunction")
    print(f"  {len(lines) - 4} setblock commands")
    print(f"  base {span_xz}x{span_xz}, apex at y={apex}, rise {RISE} "
          f"({'unclimbable' if RISE > 1 else 'WALKABLE RAMP - raise RISE'})")
    print(f"  lava boxed on 5 sides, {lava[1] - 1} block fall into a "
          f"{2 * BASIN + 1}x{2 * BASIN + 1} basin {BASIN_DEPTH} deep")
    print(f"  {len(reach)} floor cells reachable, both Nexus beds on them")
    print(f"  capture size {span_xz} x {span_y} x {span_xz} "
          f"({'OK' if max(span_xz, span_y) <= 48 else 'TOO BIG'} for the 48 cap)")
    print()
    print("  structure blocks, standing at the run position P:")
    print(f"    SAVE   corner at  P + ({-BASE - 1}, -1, {-BASE - 1})")
    print(f"    CORNER corner at  P + ({BASE + 1}, {span_y}, {BASE + 1})")


if __name__ == "__main__":
    main()
