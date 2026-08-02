#!/usr/bin/env python3
"""Generate a .mcfunction that builds the Unicursal.

    python3 tools/generate_unicursal_room.py

THE IDEA
A unicursal labyrinth is the classical kind - Chartres, Knossos - a single path with no branches and
no choices. You cannot get lost in one and you cannot take a shortcut either. You just walk it.

This room's corridor is a **Hilbert curve**, the space-filling fractal. That gives three things no
hand-drawn maze can:

  IT IS ONE PATH.       A Hilbert curve never branches and never crosses itself, so the corridor is
                        unicursal by construction. Every junction you think you see is a turn.
  IT IS SELF-SIMILAR.   Every stretch of it is a scaled copy of every other stretch. There is no
                        landmark anywhere, because the fractal guarantees there cannot be one.
  IT IS SPACE-FILLING.  That is the entire point. The curve packs ~500 blocks of corridor into a
                        31-block square, so the two Nexus beds sit about thirty blocks apart in a
                        straight line and about five hundred apart on foot.

THE WINDOWS
Every so often a one-block slot is cut through a wall at chest height - too low to climb through,
just big enough to see the corridor on the other side. It looks exactly like the one you are in,
because it is the same corridor, either two minutes ahead of you or two minutes behind, and there is
nothing anywhere to tell you which.

Those are not placed by hand or at random. Every cell knows its own index along the curve, so a slot
is only cut between two neighbouring corridors whose indices are at least WINDOW_MIN_GAP apart. The
fractal decides where the cruelty goes.
"""

import os
import random
from collections import deque

WORLD = "run/saves/DimDescentRoomBuilder"
PACK = f"{WORLD}/datapacks/dimdescent_build"
FUNC_DIR = f"{PACK}/data/build/function"
NAME = "unicursal"

BRICK = "dimdescent:altar_stone_bricks"
CRACKED = "dimdescent:cracked_altar_stone_bricks"
VOID = "dimdescent:nullstone"
DARK_BED = "dimdescent:dream_bed"
PALE_BED = "dimdescent:pale_dream_bed"

# ---------------------------------------------------------------------------
# SHAPE
#
# ORDER is capped by the 48-block limit: the curve is 2^ORDER cells across and PITCH blocks per cell,
# so order 4 at pitch 2 is 31 wide, plus a brick wall and a Nullstone skin, for 35.
# PITCH 2 means a one-block corridor separated by a one-block wall - which is what makes a window a
# window rather than a tunnel.
# ---------------------------------------------------------------------------
ORDER = 4
PITCH = 2
HEIGHT = 3               # corridor headroom. Three, uniform, forever.

WINDOW_Y = 2             # chest height: you can see through, you cannot climb through
WINDOW_MIN_GAP = 60      # only look at corridor you are far from, in curve-distance
WINDOW_CHANCE = 0.20

MIN_WALK = 400           # a shortcut through an end chamber would show up here

CRACK_CHANCE = 0.16
random.seed(20260804)


def brick():
    return CRACKED if random.random() < CRACK_CHANCE else BRICK


def hilbert(n):
    """The 2^ORDER x 2^ORDER Hilbert curve, in order, as grid coordinates."""
    def rot(s, x, y, rx, ry):
        if ry == 0:
            if rx == 1:
                x, y = s - 1 - x, s - 1 - y
            x, y = y, x
        return x, y

    out = []
    for d in range(n * n):
        x = y = 0
        t, s = d, 1
        while s < n:
            rx = 1 & (t // 2)
            ry = 1 & (t ^ rx)
            x, y = rot(s, x, y, rx, ry)
            x, y = x + s * rx, y + s * ry
            t //= 4
            s *= 2
        out.append((x, y))
    return out


def main():
    n = 2 ** ORDER
    half = (n - 1) * PITCH // 2
    pts = [(gx * PITCH - half, gz * PITCH - half) for gx, gz in hilbert(n)]

    # Walk the curve, recording every cell it passes through and how far along it that cell is.
    order_of = {}
    for i, p in enumerate(pts):
        order_of.setdefault(p, len(order_of))
        if i + 1 < len(pts):
            q = pts[i + 1]
            mid = ((p[0] + q[0]) // 2, (p[1] + q[1]) // 2)
            order_of.setdefault(mid, len(order_of))
    corridor = set(order_of)

    # A Hilbert curve does not branch, so every cell must have exactly two corridor neighbours except
    # the two ends. If that ever fails the curve code is wrong and the room is not unicursal.
    for c in corridor:
        adj = sum(1 for d in ((1, 0), (-1, 0), (0, 1), (0, -1))
                  if (c[0] + d[0], c[1] + d[1]) in corridor)
        expected = 1 if c in (pts[0], pts[-1]) else 2
        if adj != expected:
            raise AssertionError(f"cell {c} has {adj} corridor neighbours, expected {expected} - "
                                 f"the path branches, so it is not unicursal.")

    inner = half + PITCH // 2 + 1       # first solid ring outside the corridor grid
    wall = inner                        # brick outer wall
    skin = wall + 1                     # Nullstone shell

    # Arrival needs more than a one-wide dead end, so each end of the curve opens into a 3x3 pocket,
    # anchored at the corner so it cannot eat the wall.
    for end in (pts[0], pts[-1]):
        sx = -1 if end[0] > 0 else 1
        sz = -1 if end[1] > 0 else 1
        for i in range(3):
            for j in range(3):
                corridor.add((end[0] + sx * i, end[1] + sz * j))

    blocks = {}

    def put(x, y, z, block):
        blocks[(x, y, z)] = block

    for x in range(-skin, skin + 1):
        for z in range(-skin, skin + 1):
            put(x, -1, z, VOID)
            put(x, HEIGHT + 2, z, VOID)
            if max(abs(x), abs(z)) == skin:
                for y in range(0, HEIGHT + 2):
                    put(x, y, z, VOID)
                continue
            put(x, 0, z, brick())
            put(x, HEIGHT + 1, z, brick())
            if (x, z) not in corridor:
                for y in range(1, HEIGHT + 1):
                    put(x, y, z, brick())

    # Slots. A wall cell with corridor on opposite sides, where those two corridors are a long way
    # apart along the curve.
    windows = 0
    for x in range(-wall, wall + 1):
        for z in range(-wall, wall + 1):
            if (x, z) in corridor:
                continue
            for dx, dz in ((1, 0), (0, 1)):
                a, b = (x - dx, z - dz), (x + dx, z + dz)
                if a in order_of and b in order_of:
                    if abs(order_of[a] - order_of[b]) >= WINDOW_MIN_GAP \
                            and random.random() < WINDOW_CHANCE:
                        blocks.pop((x, WINDOW_Y, z), None)
                        windows += 1
                        break

    # ---- checks ------------------------------------------------------------
    solid = set(blocks)

    def standable(p):
        x, y, z = p
        return ((x, y - 1, z) in solid
                and all((x, y + k, z) not in solid for k in range(0, 2)))

    start = (pts[0][0], 1, pts[0][1])
    goal = (pts[-1][0], 1, pts[-1][1])
    for label, p in (("pale", start), ("dark", goal)):
        if not standable(p):
            raise AssertionError(f"the {label} Nexus at {p} is not standing on anything.")

    steps = {start: 0}
    queue = deque([start])
    while queue:
        p = queue.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nb = (p[0] + dx, 1, p[2] + dz)
            if abs(nb[0]) <= wall and abs(nb[2]) <= wall and nb not in steps and standable(nb):
                steps[nb] = steps[p] + 1
                queue.append(nb)
    if goal not in steps:
        raise AssertionError("the dark Nexus is unreachable on foot.")
    if steps[goal] < MIN_WALK:
        raise AssertionError(
            f"shortest walk is only {steps[goal]} blocks, under MIN_WALK {MIN_WALK} - an end pocket "
            f"has probably opened a shortcut between two arms of the curve.")

    lo, hi = (-skin - 1, 0, -skin - 1), (skin + 1, HEIGHT + 3, skin + 1)
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
            nb = (x + d[0], y + d[1], z + d[2])
            if all(lo[i] <= nb[i] <= hi[i] for i in range(3)) and nb not in seen and nb not in solid:
                seen.add(nb)
                queue.append(nb)
    if start in seen or goal in seen:
        raise AssertionError("room is NOT sealed - outside air reaches the corridor.")

    # ---- emit --------------------------------------------------------------
    shift = 1
    lines = [f"# The Unicursal. Stand at the CENTRE of the room, on the ground, in CLEAR AIR, and run:",
             f"#   /function build:{NAME}",
             f"# Only solid blocks are emitted, so the target volume must already be empty.",
             f"# Generated by tools/generate_unicursal_room.py - do not hand-edit.", ""]
    for (x, y, z), block in blocks.items():
        lines.append(f"setblock ~{x} ~{y + shift} ~{z} {block}")

    def bed(block, cell, nxt, name):
        dx = max(-1, min(1, nxt[0] - cell[0]))
        dz = max(-1, min(1, nxt[1] - cell[1]))
        facing = {(0, -1): "north", (0, 1): "south", (-1, 0): "west", (1, 0): "east"}[(dx, dz)]
        lines.append(f"setblock ~{cell[0]} ~{1 + shift} ~{cell[1]} {block}[facing={facing},part=foot]")
        lines.append(f"setblock ~{cell[0] + dx} ~{1 + shift} ~{cell[1] + dz} "
                     f"{block}[facing={facing},part=head]")

    bed(PALE_BED, pts[0], pts[1], "pale")
    bed(DARK_BED, pts[-1], pts[-2], "dark")

    os.makedirs(FUNC_DIR, exist_ok=True)
    with open(f"{PACK}/pack.mcmeta", "w") as f:
        f.write('{\n  "pack": {\n    "pack_format": 48,\n'
                '    "description": "dim_descent build helpers"\n  }\n}\n')
    with open(f"{FUNC_DIR}/{NAME}.mcfunction", "w") as f:
        f.write("\n".join(lines) + "\n")

    span_xz, span_y = 2 * skin + 1, HEIGHT + 4
    straight = max(abs(pts[0][0] - pts[-1][0]), abs(pts[0][1] - pts[-1][1]))
    print(f"wrote {FUNC_DIR}/{NAME}.mcfunction")
    print(f"  {len(lines) - 5} setblock commands, {windows} windows")
    print(f"  order-{ORDER} Hilbert curve, {len(order_of)} corridor cells, path verified unicursal")
    print(f"  Nexus to Nexus: {straight} blocks apart in a straight line, "
          f"{steps[goal]} on foot ({steps[goal] / max(1, straight):.0f}x)")
    print(f"  capture size {span_xz} x {span_y} x {span_xz} "
          f"({'OK' if max(span_xz, span_y) <= 48 else 'TOO BIG'} for the 48 cap)")
    print(f"  branch, walk-length and seal checks all passed")
    print()
    print("  structure blocks, standing at the run position P:")
    print(f"    SAVE   corner at  P + ({-skin - 1}, -1, {-skin - 1})")
    print(f"    CORNER corner at  P + ({skin + 1}, {span_y}, {skin + 1})")


if __name__ == "__main__":
    main()
