#!/usr/bin/env python3
"""Generate a .mcfunction that builds the Causeway.

    python3 tools/generate_causeway_room.py

THE IDEA
A room that is entirely Nullstone, with a narrow raised walkway of altar brick running through it.

Nullstone is pure black and, because black survives every shading term Minecraft applies, it renders
perfectly flat - no directional face shading, no ambient occlusion, no visible lightmap falloff. A
Nullstone floor is therefore indistinguishable from a hole in the world. So a player standing on a
lit brick walkway one block above it sees a bridge over void, and stepping off the edge is a
one-block drop onto a floor they were certain was not there.

That is the whole room. The lie costs one block of elevation.

  Nullstone            everything: shell, floor, walls, ceiling. Two layers, so a breached wall
                       shows more of the same rather than the back of a brick.
  altar stone bricks   the walkway only, weathered with cracked variants.
  lava                 exactly one source, flush in the pad at the end of the east branch.

The branches are deliberately asymmetric: east dead-ends at a small pad with the room's only light
sitting in the middle of it, west dead-ends at an identical pad with nothing on it. One of them
rewards the walk and one does not, and there is no way to tell which from the middle.

NOTE this replaces the Anechoic Chamber (white Allstone wedges), which is one commit back if the
wedge geometry is ever wanted again. Dimensions and capture box are unchanged from it.
"""

import os
import random
from collections import deque

WORLD = "run/saves/DimDescentRoomBuilder"
PACK = f"{WORLD}/datapacks/dimdescent_build"
FUNC_DIR = f"{PACK}/data/build/function"
NAME = "causeway"

VOID = "dimdescent:nullstone"
BRICK = "dimdescent:altar_stone_bricks"
CRACKED = "dimdescent:cracked_altar_stone_bricks"
LAVA = "minecraft:lava"
AIR = "minecraft:air"
DARK_BED = "dimdescent:dream_bed"
PALE_BED = "dimdescent:pale_dream_bed"

INNER = 13          # interior half-width -> 27 x 27
TOP = 22            # ceiling underside
WALK_Y = 1          # walkway sits one block proud of the floor. This is the entire illusion.

SPAN_HALF = 1       # main walkway is 2*SPAN_HALF+1 wide
BRANCH_Z = 5        # the two side branches leave the span at +/- this
PAD_X = 10          # centre of each dead-end pad
BED_Z = 9

CRACK_CHANCE = 0.20
random.seed(0xC0DA)


def brick():
    return CRACKED if random.random() < CRACK_CHANCE else BRICK


def main():
    inner_shell, outer_shell = INNER + 1, INNER + 2
    floor_in, ceil_in = 0, TOP
    floor_out, ceil_out = -1, TOP + 1

    blocks = {}

    def put(x, y, z, block):
        blocks[(x, y, z)] = block

    # Shell. Both layers Nullstone - there is no second material in this room.
    for box, lo_y, hi_y in ((outer_shell, floor_out, ceil_out), (inner_shell, floor_in, ceil_in)):
        for x in range(-box, box + 1):
            for y in range(lo_y, hi_y + 1):
                for z in range(-box, box + 1):
                    if abs(x) == box or abs(z) == box or y in (lo_y, hi_y):
                        put(x, y, z, VOID)

    # The walkway, as a set of (x, z) so it can be checked for connectivity before it is emitted.
    walk = set()
    for z in range(-INNER + 1, INNER):
        for x in range(-SPAN_HALF, SPAN_HALF + 1):
            walk.add((x, z))
    for sign in (1, -1):
        z = BRANCH_Z * sign
        for x in range(SPAN_HALF + 1, PAD_X - 1):
            walk.add((x * sign, z))
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                walk.add((PAD_X * sign + dx * sign, z + dz))

    for x, z in walk:
        put(x, WALK_Y, z, brick())

    lava = (PAD_X, WALK_Y, BRANCH_Z)
    put(*lava, LAVA)

    # ---- checks ------------------------------------------------------------
    solid = {p for p, b in blocks.items() if b != AIR}

    for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        n = (lava[0] + d[0], WALK_Y, lava[2] + d[1])
        if n not in solid:
            raise AssertionError(
                f"lava at {lava} has an open side at {n} - it would spread across the walkway. "
                f"The pad must fully surround it.")

    for x, z in walk:
        if abs(x) > INNER or abs(z) > INNER:
            raise AssertionError(f"walkway cell ({x},{z}) is outside the interior.")

    # Every walkway cell must be reachable on foot from the entrance, or part of the room is
    # decoration the player can only look at.
    start = (0, -BED_Z)
    seen, queue = {start}, deque([start])
    while queue:
        x, z = queue.popleft()
        for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + d[0], z + d[1])
            if n in walk and n not in seen:
                seen.add(n)
                queue.append(n)
    if start not in walk:
        raise AssertionError("the pale bed is not standing on the walkway.")
    stranded = walk - seen
    if stranded:
        raise AssertionError(
            f"{len(stranded)} walkway cells are unreachable from the entrance, "
            f"first {sorted(stranded)[0]} - the walkway is not one connected path.")
    for label, cell in (("dark bed", (0, BED_Z)), ("lava pad", (lava[0], lava[2]))):
        if cell not in seen:
            raise AssertionError(f"cannot walk from the pale bed to the {label} at {cell}.")

    for label, fz in (("pale", -BED_Z), ("dark", BED_Z)):
        for z in (fz, fz - 1 if fz < 0 else fz + 1):
            if (0, z) not in walk:
                raise AssertionError(f"{label} bed at (0,{z}) is not on the walkway.")
            if (0, WALK_Y + 1, z) in solid:
                raise AssertionError(f"{label} bed at (0,{z}) is blocked.")

    interior = [(x, y, z)
                for x in range(-INNER, INNER + 1)
                for y in range(1, TOP)
                for z in range(-INNER, INNER + 1)
                if (x, y, z) not in solid]
    lo = (-outer_shell - 1, floor_out - 1, -outer_shell - 1)
    hi = (outer_shell + 1, ceil_out + 1, outer_shell + 1)
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
    leaks = [c for c in interior if c in seen3]
    if leaks:
        raise AssertionError(
            f"room is NOT sealed - outside air reaches {len(leaks)} interior cells, first {leaks[0]}.")

    # ---- emit --------------------------------------------------------------
    shift = -floor_out
    lines = [f"# The Causeway. Stand at the CENTRE of the room, on the ground, and run:",
             f"#   /function build:{NAME}",
             f"# Generated by tools/generate_causeway_room.py - do not hand-edit.", ""]
    for (x, y, z), block in blocks.items():
        if block is not LAVA:
            lines.append(f"setblock ~{x} ~{y + shift} ~{z} {block}")

    def bed(block, fz, facing):
        hz = fz - 1 if facing == "north" else fz + 1
        y = WALK_Y + 1 + shift
        lines.append(f"setblock ~0 ~{y} ~{fz} {block}[facing={facing},part=foot]")
        lines.append(f"setblock ~0 ~{y} ~{hz} {block}[facing={facing},part=head]")

    bed(PALE_BED, -BED_Z, "north")
    bed(DARK_BED, BED_Z, "south")

    # Fluids last, so the room is finished before anything can flow.
    lines.append(f"setblock ~{lava[0]} ~{lava[1] + shift} ~{lava[2]} {LAVA}")

    os.makedirs(FUNC_DIR, exist_ok=True)
    with open(f"{PACK}/pack.mcmeta", "w") as f:
        f.write('{\n  "pack": {\n    "pack_format": 48,\n'
                '    "description": "dim_descent build helpers"\n  }\n}\n')
    with open(f"{FUNC_DIR}/{NAME}.mcfunction", "w") as f:
        f.write("\n".join(lines) + "\n")

    span_xz, span_y = 2 * outer_shell + 1, ceil_out - floor_out + 1
    print(f"wrote {FUNC_DIR}/{NAME}.mcfunction")
    print(f"  {len(lines) - 4} setblock commands, {len(walk)} walkway blocks, 1 lava source")
    print(f"  interior {2 * INNER + 1} x {TOP - 1} x {2 * INNER + 1}, walkway at y={WALK_Y}")
    print(f"  capture size {span_xz} x {span_y} x {span_xz} "
          f"({'OK' if max(span_xz, span_y) <= 48 else 'TOO BIG'} for the 48 cap)")
    print(f"  lava containment, walkway connectivity, bed clearance and seal checks all passed")
    print()
    print("  structure blocks, standing at the run position P:")
    print(f"    SAVE   corner at  P + ({-outer_shell - 1}, -1, {-outer_shell - 1})")
    print(f"    CORNER corner at  P + ({outer_shell + 1}, {span_y}, {outer_shell + 1})")


if __name__ == "__main__":
    main()
