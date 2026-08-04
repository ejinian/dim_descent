#!/usr/bin/env python3
"""Generate a .mcfunction that builds the Nave.

    python3 tools/generate_nave_room.py

THE IDEA
A processional way between two ridges of stone, with two enormous arcs crossing in the dark far above
it and a pendant hanging from where they meet. Cathedral geometry, in a room with no cathedral around
it.

  THE CAUSEWAY.  A nine-wide brick plaza running the length of the room. Its edges have gone in
                 places, and what shows through the gaps is Nullstone - flat black, no shading - so
                 the holes read as open void rather than as a floor a shade darker.
  THE RIDGES.    The ground either side rises away from the causeway in one-block terraces, with a
                 ridgeline that undulates rather than running straight. Blocks are integers, so the
                 terracing is free; the undulation is what stops it reading as a machined ramp.
  THE ARCS.      Two ribbons of brick springing from the four corners, diagonally, each rising in a
                 sine arch to cross directly over the middle of the causeway. They are two blocks
                 deep and nothing holds them up. They are also the only thing in the room that is
                 not made of steps, which is exactly why they read as deliberate.
  THE PENDANT.   A tapering column of brick hanging out of the crossing. It is a real cathedral
                 feature - a pendant boss - and it does the same job here: it tells you the arcs are
                 a structure and not two lines that happen to intersect.

EVERYTHING VISIBLE IS ALTAR STONE BRICK. The only other block in the room is the Nullstone shell and
the void showing through the causeway's broken edges, which is the point of contrast: one material,
one colour, and then nothing at all.

THE ARCS ARE SAMPLED, NOT DRAWN
A diagonal sine arch crossing thirty-seven blocks does not land on integers, so it is walked at fine
parameter steps and every cell the curve passes through is filled. Sampling too coarsely leaves a
dotted line rather than a ribbon, which is why the generator checks each arc is fully connected
before it writes anything.
"""

import math
import os
import random
from collections import deque

WORLD = "run/saves/DimDescentRoomBuilder"
PACK = f"{WORLD}/datapacks/dimdescent_build"
FUNC_DIR = f"{PACK}/data/build/function"
NAME = "nave"

BRICK = "dimdescent:altar_stone_bricks"
CRACKED = "dimdescent:cracked_altar_stone_bricks"
VOID = "dimdescent:nullstone"
DARK_BED = "dimdescent:dream_bed"
PALE_BED = "dimdescent:pale_dream_bed"

HALL = 18          # interior half-width -> 37x37
TOP = 38           # interior height. The arcs spring off the ridges, so this has to clear
                   # ridge height + ARC_RISE, not just ARC_RISE - the assertion caught it at 29.

WALK_HALF = 4      # nine-wide causeway
MOUND_START = 5    # the ridges begin one block off the causeway
MOUND_SLOPE = 0.62
MOUND_NOISE = ((1.6, 0.21, 0.0), (0.9, 0.47, 2.2))   # (amplitude, frequency in z, phase)
MOUND_MAX = 11

ARC_RISE = 24      # height of the crossing above the causeway
ARC_STEPS = 900    # parameter samples per arc; too few and the ribbon comes out dotted
PENDANT = 7        # how far the boss hangs out of the crossing

EDGE_BREAK = 0.30  # chance a causeway edge block has gone
BED_INSET = 3

CRACK_CHANCE = 0.20
random.seed(20260815)


def brick():
    return CRACKED if random.random() < CRACK_CHANCE else BRICK


def main():
    blocks = {}

    def put(x, y, z, block):
        blocks[(x, y, z)] = block

    interior = [(x, z) for x in range(-HALL, HALL + 1) for z in range(-HALL, HALL + 1)]

    # Shell, and the black floor the whole thing stands on.
    skin = HALL + 2
    for x in range(-skin, skin + 1):
        for z in range(-skin, skin + 1):
            for y in range(-2, TOP + 3):
                if max(abs(x), abs(z)) > HALL or y in (-2, -1, TOP + 1, TOP + 2):
                    put(x, y, z, VOID)

    # Causeway, with its edges eaten away.
    walk = set()
    for x, z in interior:
        if abs(x) > WALK_HALF:
            continue
        if abs(x) == WALK_HALF and random.random() < EDGE_BREAK:
            continue                       # a hole, showing the Nullstone underneath
        put(x, 0, z, brick())
        walk.add((x, z))

    # Ridges: one-block terraces climbing away from the causeway, ridgeline undulating along z.
    def mound_height(x, z):
        d = abs(x) - MOUND_START
        if d < 0:
            return 0
        wobble = sum(a * math.sin(f * z + p) for a, f, p in MOUND_NOISE)
        return max(0, min(MOUND_MAX, int(d * MOUND_SLOPE + wobble + 0.5)))

    for x, z in interior:
        h = mound_height(x, z)
        for y in range(0, h + 1):
            put(x, y, z, brick())

    # The two arcs, corner to corner, each a sine arch. Sampled finely and thickened to two blocks,
    # because a curve this shallow crosses a lot of cells per unit of parameter.
    arcs = []
    corners = (((-HALL, -HALL), (HALL, HALL)), ((-HALL, HALL), (HALL, -HALL)))
    for (ax, az), (bx, bz) in corners:
        cells = set()
        base = mound_height(ax, az)
        for i in range(ARC_STEPS + 1):
            t = i / ARC_STEPS
            px = ax + (bx - ax) * t
            pz = az + (bz - az) * t
            py = base + ARC_RISE * math.sin(math.pi * t)
            c = (int(round(px)), int(round(py)), int(round(pz)))
            cells.add(c)
            cells.add((c[0], c[1] + 1, c[2]))
        arcs.append(cells)
        for c in cells:
            put(c[0], c[1], c[2], brick())

    # The pendant, hanging out of the crossing.
    peak = max(c[1] for c in arcs[0] if abs(c[0]) <= 1 and abs(c[2]) <= 1)
    for i in range(PENDANT):
        r = 1 if i < PENDANT - 3 else 0
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                put(dx, peak - 1 - i, dz, brick())

    pale_foot, pale_head = (0, 1, HALL - BED_INSET), (0, 1, HALL - BED_INSET + 1)
    dark_foot, dark_head = (0, 1, -(HALL - BED_INSET)), (0, 1, -(HALL - BED_INSET) - 1)

    # ---- checks ------------------------------------------------------------
    solid = set(blocks)

    for n, cells in enumerate(arcs):
        seen, queue = set(), deque()
        start = next(iter(cells))
        seen.add(start)
        queue.append(start)
        while queue:
            c = queue.popleft()
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    for dz in (-1, 0, 1):
                        nb = (c[0] + dx, c[1] + dy, c[2] + dz)
                        if nb in cells and nb not in seen:
                            seen.add(nb)
                            queue.append(nb)
        if len(seen) != len(cells):
            raise AssertionError(f"arc {n} is in {len(cells) - len(seen)} disconnected pieces - "
                                 f"raise ARC_STEPS.")

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
        raise AssertionError("the causeway is impassable - the broken edges have cut it in two.")

    # Nothing may hang into the walking space over the causeway.
    for x, z in walk:
        for y in (1, 2):
            if (x, y, z) in solid:
                raise AssertionError(f"something is blocking the causeway at ({x},{y},{z}).")
    if peak - PENDANT <= 4:
        raise AssertionError(f"the pendant reaches y={peak - PENDANT}, too close to the floor.")
    if peak + 1 >= TOP:
        raise AssertionError(f"the arcs reach y={peak + 1}, into the ceiling at {TOP}.")

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
        raise AssertionError("room is NOT sealed - outside air reaches the causeway.")

    # ---- emit --------------------------------------------------------------
    shift = 2
    lines = ["# The Nave. Stand at the CENTRE of the room, in CLEAR AIR, and run:",
             f"#   /function build:{NAME}",
             "# Generated by tools/generate_nave_room.py - do not hand-edit.", ""]
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
    ridge = max(mound_height(x, z) for x, z in interior)
    print(f"wrote {FUNC_DIR}/{NAME}.mcfunction")
    print(f"  {len(lines) - 4} setblock commands")
    print(f"  causeway {2 * WALK_HALF + 1} wide, {len(walk)} blocks, crossable end to end")
    print(f"  ridges terrace up to y={ridge} either side, ridgeline undulating")
    print(f"  2 arcs, {len(arcs[0])} + {len(arcs[1])} blocks, both verified continuous, "
          f"crossing at y={peak}")
    print(f"  pendant {PENDANT} long, hanging to y={peak - PENDANT}")
    print(f"  capture size {span_xz} x {span_y} x {span_xz} "
          f"({'OK' if max(span_xz, span_y) <= 48 else 'TOO BIG'} for the 48 cap)")
    print()
    print("  structure blocks, relative to the room CENTRE P:")
    print(f"    SAVE   corner at  P + ({-skin - 1}, -1, {-skin - 1})")
    print(f"    CORNER corner at  P + ({skin + 1}, {span_y}, {skin + 1})")


if __name__ == "__main__":
    main()
