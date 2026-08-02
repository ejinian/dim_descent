#!/usr/bin/env python3
"""Generate a .mcfunction that builds the Anechoic Chamber.

    python3 tools/generate_anechoic_room.py

THE IDEA
A real anechoic chamber is a room lined on every surface with foam wedges, built to absorb all
sound. They are the quietest places that exist, and people left alone in one for long enough start
hallucinating - the brain, starved of input, supplies its own. There is no better real-world object
for this mod to put in a dungeon whose whole subject is a hallucinating player.

So: a pure white room, wedged floor to ceiling, in a palette of exactly three blocks.

  Allstone    every interior surface. Flat white, no grain, nothing to judge distance by.
  Nullstone   the outer skin ONLY, one layer, so from outside the build reads as void rather
              than as a box someone built. Two-layer shell: white inside, black outside.
  lava        exactly one source block, flush in the dead centre of the ceiling.

That lava is the only colour, the only light and the only sound in a room designed to have none of
the three, and it falls twenty-one blocks down the middle of the room into a one-block well in the
floor. The two Nexus beds sit at either end, so the walk between them passes it.

The floor is deliberately the one FLAT surface - real chambers suspend a mesh walkway over the
lower wedges for exactly the same reason, and a flat white floor is what makes the wedges read.

WHY A GENERATOR
The wedge grid is ~330 pyramids across five surfaces, each one three blocks tapering to one. It is
per-block maths, it has to tile exactly into the interior dimensions, and getting the lava's four
neighbours right is a correctness question, not an aesthetic one. All of that is checked below.
"""

import os
from collections import deque

WORLD = "run/saves/DimDescentRoomBuilder"
PACK = f"{WORLD}/datapacks/dimdescent_build"
FUNC_DIR = f"{PACK}/data/build/function"
NAME = "anechoic"

WHITE = "dimdescent:allstone"
BLACK = "dimdescent:nullstone"
LAVA = "minecraft:lava"
AIR = "minecraft:air"
DARK_BED = "dimdescent:dream_bed"
PALE_BED = "dimdescent:pale_dream_bed"

# ---------------------------------------------------------------------------
# SHAPE
#
# INNER and TOP must both tile exactly into PITCH or the wedge grid runs off the edge of a surface
# and leaves a ragged strip. The assertions below enforce it rather than trusting the arithmetic.
# ---------------------------------------------------------------------------
INNER = 13     # interior half-width -> 27 wide, = 9 wedges
TOP = 22       # ceiling underside; interior rows 1..TOP-1 -> 21 tall, = 7 wedges
PITCH = 3      # wedge base is PITCH x PITCH
DEPTH = 2      # how far a wedge protrudes: PITCH x PITCH base, then a 1x1 tip

BED_Z = 9      # both beds this far out from centre, facing the near wall


def main():
    inner_shell = INNER + 1        # Allstone skin
    outer_shell = INNER + 2        # Nullstone skin
    floor_in, ceil_in = 0, TOP     # Allstone floor/ceiling planes
    floor_out, ceil_out = -1, TOP + 1

    span = 2 * INNER + 1
    rows = TOP - 1
    if span % PITCH or rows % PITCH:
        raise AssertionError(
            f"wedge grid does not tile: interior is {span} wide x {rows} tall, PITCH is {PITCH}. "
            f"Adjust INNER/TOP so both 2*INNER+1 and TOP-1 are multiples of PITCH.")

    half = PITCH // 2
    centres = list(range(-INNER + half, INNER, PITCH))          # -12, -9, ... 12
    rises = list(range(1 + half, TOP - 1, PITCH))               # 2, 5, ... 20

    blocks = {}

    def put(x, y, z, block):
        blocks[(x, y, z)] = block

    # Two-layer shell. Outer box in Nullstone, inner box in Allstone.
    for box, block in ((outer_shell, BLACK), (inner_shell, WHITE)):
        lo_y = floor_out if block is BLACK else floor_in
        hi_y = ceil_out if block is BLACK else ceil_in
        for x in range(-box, box + 1):
            for y in range(lo_y, hi_y + 1):
                for z in range(-box, box + 1):
                    on_face = abs(x) == box or abs(z) == box or y in (lo_y, hi_y)
                    if on_face:
                        put(x, y, z, block)

    # A wedge: a PITCH x PITCH pad on the surface, then a 1x1 tip one step further in.
    # `axis` is the direction it grows towards the middle of the room.
    def wedge(cx, cy, cz, axis):
        for step in range(DEPTH):
            reach = half if step == 0 else 0
            for a in range(-reach, reach + 1):
                for b in range(-reach, reach + 1):
                    if axis[0]:
                        put(cx + axis[0] * step, cy + a, cz + b, WHITE)
                    elif axis[1]:
                        put(cx + a, cy + axis[1] * step, cz + b, WHITE)
                    else:
                        put(cx + a, cy + b, cz + axis[2] * step, WHITE)

    skipped = 0
    for cx in centres:
        for cz in centres:
            if cx == 0 and cz == 0:
                skipped += 1       # the lava drops through here
                continue
            wedge(cx, TOP - 1, cz, (0, -1, 0))
    for cy in rises:
        for c in centres:
            wedge(INNER, cy, c, (-1, 0, 0))
            wedge(-INNER, cy, c, (1, 0, 0))
            wedge(c, cy, INNER, (0, 0, -1))
            wedge(c, cy, -INNER, (0, 0, 1))

    # The well the lava lands in - one block deep, floored by the Nullstone skin so the room stays
    # sealed. Anything deeper would punch through the shell.
    put(0, floor_in, 0, AIR)

    # ---- checks ------------------------------------------------------------
    solid = {p for p, b in blocks.items() if b != AIR}

    for d in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1)):
        n = (d[0], ceil_in + d[1], d[2])
        if n not in solid:
            raise AssertionError(
                f"lava source at (0,{ceil_in},0) has an open side at {n} - it would spread "
                f"sideways across the ceiling instead of falling.")
    if (0, ceil_in - 1, 0) in solid:
        raise AssertionError("nothing under the lava source - it cannot fall.")

    interior = [(x, y, z)
                for x in range(-INNER, INNER + 1)
                for y in range(1, TOP)
                for z in range(-INNER, INNER + 1)
                if (x, y, z) not in solid]

    for label, (bx, bz) in (("pale", (0, -BED_Z)), ("dark", (0, BED_Z))):
        for z in (bz, bz - 1 if bz < 0 else bz + 1):
            if (bx, 1, z) in solid or (bx, floor_in, z) not in solid:
                raise AssertionError(f"{label} bed at ({bx},{z}) is blocked or unsupported.")

    lo = (-outer_shell - 1, floor_out - 1, -outer_shell - 1)
    hi = (outer_shell + 1, ceil_out + 1, outer_shell + 1)
    seen, queue = set(), deque()
    for x in range(lo[0], hi[0] + 1):
        for y in range(lo[1], hi[1] + 1):
            for z in range(lo[2], hi[2] + 1):
                if (x in (lo[0], hi[0]) or y in (lo[1], hi[1]) or z in (lo[2], hi[2])) \
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
    leaks = [c for c in interior if c in seen]
    if leaks:
        raise AssertionError(
            f"room is NOT sealed - outside air reaches {len(leaks)} interior cells, first {leaks[0]}. "
            f"RoomContainment would Nullstone-coat the interior.")

    # ---- emit --------------------------------------------------------------
    shift = -floor_out
    lines = [f"# The Anechoic Chamber. Stand at the CENTRE of the room, on the ground, and run:",
             f"#   /function build:{NAME}",
             f"# Generated by tools/generate_anechoic_room.py - do not hand-edit.", ""]
    for (x, y, z), block in blocks.items():
        lines.append(f"setblock ~{x} ~{y + shift} ~{z} {block}")

    def bed(block, fz, facing):
        hz = fz - 1 if facing == "north" else fz + 1
        lines.append(f"setblock ~0 ~{1 + shift} ~{fz} {block}[facing={facing},part=foot]")
        lines.append(f"setblock ~0 ~{1 + shift} ~{hz} {block}[facing={facing},part=head]")

    bed(PALE_BED, -BED_Z, "north")
    bed(DARK_BED, BED_Z, "south")

    # Last of all, so the room is already sealed when it starts to fall.
    lines.append(f"setblock ~0 ~{ceil_in + shift} ~0 {LAVA}")

    os.makedirs(FUNC_DIR, exist_ok=True)
    with open(f"{PACK}/pack.mcmeta", "w") as f:
        f.write('{\n  "pack": {\n    "pack_format": 48,\n'
                '    "description": "dim_descent build helpers"\n  }\n}\n')
    with open(f"{FUNC_DIR}/{NAME}.mcfunction", "w") as f:
        f.write("\n".join(lines) + "\n")

    span_xz = 2 * outer_shell + 1
    span_y = ceil_out - floor_out + 1
    wedges = len(centres) ** 2 - skipped + 4 * len(rises) * len(centres)
    print(f"wrote {FUNC_DIR}/{NAME}.mcfunction")
    print(f"  {len(lines) - 4} setblock commands, {wedges} wedges, 1 lava source")
    print(f"  interior {span}x{rows}x{span}, wedge grid tiles exactly at pitch {PITCH}")
    print(f"  capture size {span_xz} x {span_y} x {span_xz} "
          f"({'OK' if max(span_xz, span_y) <= 48 else 'TOO BIG'} for the 48 cap)")
    print(f"  lava containment, bed clearance and seal checks all passed")
    print()
    print("  structure blocks, standing at the run position P:")
    print(f"    SAVE   corner at  P + ({-outer_shell - 1}, -1, {-outer_shell - 1})")
    print(f"    CORNER corner at  P + ({outer_shell + 1}, {span_y}, {outer_shell + 1})")


if __name__ == "__main__":
    main()
