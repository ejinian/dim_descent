#!/usr/bin/env python3
"""Generate a .mcfunction that builds the Carpet.

    python3 tools/generate_carpet_room.py

THE IDEA
A hall whose floor is a Sierpinski carpet, three levels deep, suspended over a drop - and whose
ceiling is the same carpet again, with the holes opening into shafts that recede out of sight.

The Sierpinski carpet is the right fractal for this and the Menger sponge is not, for one specific
reason: **the carpet is a connected set and its complement is not.** Filled cells always touch, so
the floor is walkable everywhere, at every scale, with no islands - while the holes are simply holes.
Try it the other way round (walls on the carpet, walk in the gaps) and the walkable space shatters
into eight sealed chambers. That property is the whole room.

Holes come at three sizes: one 9x9, eight 3x3, sixty-four 1x1. Because they are self-similar, a
photograph of one corner is indistinguishable from a photograph of the whole floor, and a player has
no way to judge how far across it is or how far they have come. That is as liminal as geometry gets,
and it is free - the fractal does it, not the decoration.

  altar stone bricks (+ cracked)   absolutely everything, including the shell
  no Nullstone, no lava, no bars

THE DROP IS NOT A DEATH
Falling through a hole lands you on a sub-floor five blocks down - three points of damage, and a low
dark crawl under the carpet looking up at the light coming through the holes you did not fall into.
That matters: a soft-locking pit in a mod whose only escape is waiting for a drug to wear off would
be miserable. Better, it makes falling a SHORTCUT. The dark Nexus sits on the sub-floor at the bottom
of the central shaft, so the safe route (cross the carpet, find the stair in the 9x9 hole, walk down)
is strictly longer than just stepping into a hole and walking. The room rewards the mistake.
"""

import os
import random
from collections import deque

WORLD = "run/saves/DimDescentRoomBuilder"
PACK = f"{WORLD}/datapacks/dimdescent_build"
FUNC_DIR = f"{PACK}/data/build/function"
NAME = "carpet"

BRICK = "dimdescent:altar_stone_bricks"
CRACKED = "dimdescent:cracked_altar_stone_bricks"
DARK_BED = "dimdescent:dream_bed"
PALE_BED = "dimdescent:pale_dream_bed"

# ---------------------------------------------------------------------------
# SHAPE
#
# LEVELS is capped by the 48-block structure limit: a level-N carpet is 3^N across, so 3 levels is
# 27 and 4 would be 81. 27 plus a two-wide ledge plus the shell is 33.
# ---------------------------------------------------------------------------
LEVELS = 3
CARPET = 3 ** LEVELS // 2        # 13 -> carpet spans -13..13
LEDGE = CARPET + 2               # 15 -> two-wide solid ring for arrival
SKIN = LEDGE + 1                 # 16 -> shell, 33x33 footprint

SUB_FLOOR = -6                   # top of the lower level's floor
FLOOR = 0                        # the carpet itself
CEIL = 13                        # the mirrored carpet overhead
SHAFT = 6                        # how far the ceiling holes recede before the lid
LID = CEIL + SHAFT + 1

CENTRE_HOLE = 3 ** (LEVELS - 1) // 2   # 4 -> the big central hole spans -4..4

CRACK_CHANCE = 0.18
random.seed(20260803)


def brick():
    return CRACKED if random.random() < CRACK_CHANCE else BRICK


def in_carpet(x, z):
    """Sierpinski carpet membership, finest level first."""
    gx, gz = x + CARPET, z + CARPET
    for _ in range(LEVELS):
        if gx % 3 == 1 and gz % 3 == 1:
            return False
        gx //= 3
        gz //= 3
    return True


def main():
    interior = [(x, z) for x in range(-LEDGE, LEDGE + 1) for z in range(-LEDGE, LEDGE + 1)]
    floor_cells = {c for c in interior
                   if max(abs(c[0]), abs(c[1])) > CARPET or in_carpet(*c)}

    blocks = {}

    def put(x, y, z, block):
        blocks[(x, y, z)] = block

    for x in range(-SKIN, SKIN + 1):
        for z in range(-SKIN, SKIN + 1):
            if max(abs(x), abs(z)) == SKIN:
                for y in range(SUB_FLOOR, LID + 1):
                    put(x, y, z, brick())            # shell wall
                continue
            put(x, SUB_FLOOR, z, brick())            # the lower level's floor
            put(x, LID, z, brick())                  # the lid
            if (x, z) in floor_cells:
                put(x, FLOOR, z, brick())            # carpet + ledge
                put(x, CEIL, z, brick())             # the same again, overhead
                for y in range(CEIL + 1, LID):
                    put(x, y, z, brick())            # solid mass, so holes read as shafts
            # a hole leaves FLOOR..CEIL open below and CEIL+1..LID open above: a shaft either way

    # The one way back up, in the big central hole. Six steps from the sub-floor to the carpet.
    stair = [(x, FLOOR - (CENTRE_HOLE - x), CENTRE_HOLE) for x in range(-1, CENTRE_HOLE + 1)]
    for x, y, z in stair:
        put(x, y, z, brick())

    # ---- checks ------------------------------------------------------------
    solid = set(blocks)

    # The carpet must be one connected surface. This is the property the room is built on, so it is
    # worth proving rather than trusting: if it ever fails, half the floor is an island.
    seed = (CARPET, CARPET)
    seen, queue = {seed}, deque([seed])
    while queue:
        x, z = queue.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, z + dz)
            if abs(n[0]) <= CARPET and abs(n[1]) <= CARPET and n not in seen and in_carpet(*n):
                seen.add(n)
                queue.append(n)
    carpet_cells = {c for c in interior if max(abs(c[0]), abs(c[1])) <= CARPET and in_carpet(*c)}
    if seen != carpet_cells:
        raise AssertionError(
            f"the carpet is not connected: {len(carpet_cells - seen)} cells are islands. "
            f"in_carpet() is wrong.")

    def standable(p):
        x, y, z = p
        return ((x, y - 1, z) in solid
                and (x, y, z) not in solid and (x, y + 1, z) not in solid)

    def moves(p):
        x, y, z = p
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if standable((nx, y + 1, nz)) and (x, y + 2, z) not in solid:
                yield (nx, y + 1, nz)
                continue
            if standable((nx, y, nz)):
                yield (nx, y, nz)
                continue
            if (nx, y, nz) in solid or (nx, y + 1, nz) in solid:
                continue
            ny = y - 1                                   # step off the edge and fall
            while ny > SUB_FLOOR:
                if standable((nx, ny, nz)):
                    yield (nx, ny, nz)
                    break
                if (nx, ny, nz) in solid:
                    break
                ny -= 1

    start = (0, FLOOR + 1, LEDGE)
    goal = (0, SUB_FLOOR + 1, 0)
    for label, p in (("pale", start), ("dark", goal)):
        if not standable(p):
            raise AssertionError(f"the {label} Nexus at {p} is not standing on anything.")
    reach, queue = {start}, deque([start])
    while queue:
        p = queue.popleft()
        for n in moves(p):
            if n not in reach and max(abs(n[0]), abs(n[2])) <= LEDGE:
                reach.add(n)
                queue.append(n)
    if goal not in reach:
        raise AssertionError(
            "the dark Nexus is unreachable on foot from the pale one - check the central stair.")

    lo, hi = (-SKIN - 1, SUB_FLOOR - 1, -SKIN - 1), (SKIN + 1, LID + 1, SKIN + 1)
    seen3, queue = set(), deque()
    for x in range(lo[0], hi[0] + 1):
        for y in range(lo[1], hi[1] + 1):
            for z in range(lo[2], hi[2] + 1):
                if (x in (lo[0], hi[0]) or y in (lo[1], hi[1]) or z in (lo[2], hi[2])) \
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
    if start in seen3 or goal in seen3:
        raise AssertionError("room is NOT sealed - outside air reaches the playable space.")

    # ---- emit --------------------------------------------------------------
    shift = -SUB_FLOOR
    lines = [f"# The Carpet. Stand at the CENTRE of the room, on the ground, in CLEAR AIR, and run:",
             f"#   /function build:{NAME}",
             f"# Only solid blocks are emitted, so the target volume must already be empty.",
             f"# Generated by tools/generate_carpet_room.py - do not hand-edit.", ""]
    for (x, y, z), block in blocks.items():
        lines.append(f"setblock ~{x} ~{y + shift} ~{z} {block}")

    def bed(block, fz, y, facing):
        hz = fz - 1 if facing == "north" else fz + 1
        lines.append(f"setblock ~0 ~{y + shift} ~{fz} {block}[facing={facing},part=foot]")
        lines.append(f"setblock ~0 ~{y + shift} ~{hz} {block}[facing={facing},part=head]")

    bed(PALE_BED, LEDGE, FLOOR + 1, "north")
    bed(DARK_BED, 0, SUB_FLOOR + 1, "north")

    os.makedirs(FUNC_DIR, exist_ok=True)
    with open(f"{PACK}/pack.mcmeta", "w") as f:
        f.write('{\n  "pack": {\n    "pack_format": 48,\n'
                '    "description": "dim_descent build helpers"\n  }\n}\n')
    with open(f"{FUNC_DIR}/{NAME}.mcfunction", "w") as f:
        f.write("\n".join(lines) + "\n")

    holes = (2 * CARPET + 1) ** 2 - len(carpet_cells)
    span_xz, span_y = 2 * SKIN + 1, LID - SUB_FLOOR + 1
    print(f"wrote {FUNC_DIR}/{NAME}.mcfunction")
    print(f"  {len(lines) - 5} setblock commands")
    print(f"  level-{LEVELS} carpet, {2 * CARPET + 1}x{2 * CARPET + 1}, "
          f"{len(carpet_cells)} solid / {holes} hole cells")
    print(f"  drop through a hole: {FLOOR - SUB_FLOOR} blocks "
          f"({max(0, FLOOR - SUB_FLOOR - 3)} damage), {len(reach)} cells reachable on foot")
    print(f"  capture size {span_xz} x {span_y} x {span_xz} "
          f"({'OK' if max(span_xz, span_y) <= 48 else 'TOO BIG'} for the 48 cap)")
    print(f"  connectivity, reachability and seal checks all passed")
    print()
    print("  structure blocks, standing at the run position P:")
    print(f"    SAVE   corner at  P + ({-SKIN - 1}, -1, {-SKIN - 1})")
    print(f"    CORNER corner at  P + ({SKIN + 1}, {span_y}, {SKIN + 1})")


if __name__ == "__main__":
    main()
