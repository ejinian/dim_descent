#!/usr/bin/env python3
"""Generate a .mcfunction that builds the Knot.

    python3 tools/generate_knot_room.py

WHY THE LATTICE DID NOT FILL THE ROOM
A Cantor dust deletes the middle third at every level, so a void at the dead centre is not an
oversight in the build - it is the definition of the set. It also leaves nothing but scattered
eight-block clusters, all identically oriented, because that is all a totally disconnected set can
ever be. The right response is a different fractal, not a tweaked one.

THE IDEA
A **space-filling curve** is the exact opposite thing: a single connected path that passes within a
fixed distance of every point in the cube, centre included, by construction. This room is a
three-dimensional Hilbert curve rendered as a one-block pipe on a 3-block pitch - one continuous
line, 1534 blocks long, folded through the entire volume, weaving over and under and through itself
in all three axes.

It never branches, it never crosses itself, and it touches everywhere. Standing inside it you cannot
find a direction with nothing in it, and you cannot find the pattern's edge either, because there
isn't one - the same fold repeats at three scales in every axis.

THE BRIDGE
The knot is far too dense to walk through, so a three-wide brick bridge is cut straight through the
middle of it, from the pale Nexus to the dark one, with a three-block channel cleared above. The
severed pipe ends left hanging on both sides are the whole point: you are walking through a cut in
something that was continuous, and it is very obvious that the cut came second.

THE CURVE IS VERIFIED, NOT TRUSTED
Skilling's transform is easy to get subtly wrong and a wrong one still produces a plausible-looking
tangle. The generator asserts that the point sequence visits all 8^ORDER cells exactly once and that
every consecutive pair is one step apart on exactly one axis. Only a genuine Hilbert curve passes
both.
"""

import os
from collections import deque

WORLD = "run/saves/DimDescentRoomBuilder"
PACK = f"{WORLD}/datapacks/dimdescent_build"
FUNC_DIR = f"{PACK}/data/build/function"
NAME = "knot"

BRICK = "dimdescent:altar_stone_bricks"
CRACKED = "dimdescent:cracked_altar_stone_bricks"
VOID = "dimdescent:nullstone"
DARK_BED = "dimdescent:dream_bed"
PALE_BED = "dimdescent:pale_dream_bed"

ORDER = 3                 # 8x8x8 grid of curve points
PITCH = 3                 # blocks between consecutive points -> 22-block span
BASE_Y = 1                # knot starts at floor level, so you are inside it, not under it

HALL = 13                 # interior half-width -> 27x27
SKIN = HALL + 2           # two Nullstone layers -> 31x31
TOP = 27                  # interior headroom

BRIDGE_HALF = 1           # 3 wide
BRIDGE_Y = 1              # brick deck; the player walks on top of it at BRIDGE_Y + 1
BRIDGE_CLEAR = 3          # air blocks kept above the deck
BED_Z = 12


def transpose_to_axes(X, bits, dims):
    """Skilling's inverse Hilbert transform, in place."""
    N = 2 << (bits - 1)
    t = X[dims - 1] >> 1
    for i in range(dims - 1, 0, -1):
        X[i] ^= X[i - 1]
    X[0] ^= t
    Q = 2
    while Q != N:
        P = Q - 1
        for i in range(dims - 1, -1, -1):
            if X[i] & Q:
                X[0] ^= P
            else:
                t = (X[0] ^ X[i]) & P
                X[0] ^= t
                X[i] ^= t
        Q <<= 1
    return X


def hilbert3(order):
    dims, bits = 3, order
    pts = []
    for d in range(1 << (bits * dims)):
        X = [0] * dims
        for i in range(bits * dims):
            bit = (d >> (bits * dims - 1 - i)) & 1
            X[i % dims] = (X[i % dims] << 1) | bit
        pts.append(tuple(transpose_to_axes(X, bits, dims)))
    return pts


def main():
    side = 2 ** ORDER
    pts = hilbert3(ORDER)

    # A wrong transform still yields a plausible tangle, so prove it is the curve.
    if len(set(pts)) != side ** 3:
        raise AssertionError(f"curve visits {len(set(pts))} distinct cells, expected {side ** 3} - "
                             f"Skilling's transform is wrong.")
    for a, b in zip(pts, pts[1:]):
        d = [abs(a[i] - b[i]) for i in range(3)]
        if sorted(d) != [0, 0, 1]:
            raise AssertionError(f"curve jumps from {a} to {b} - not a continuous Hilbert path.")

    span = (side - 1) * PITCH
    off = -(span // 2)
    world = [(p[0] * PITCH + off, p[1] * PITCH + BASE_Y, p[2] * PITCH + off) for p in pts]

    knot = set()
    for a, b in zip(world, world[1:]):
        knot.add(a)
        step = [(b[i] - a[i]) // PITCH for i in range(3)]
        for k in range(1, PITCH):
            knot.add(tuple(a[i] + step[i] * k for i in range(3)))
    knot.add(world[-1])

    blocks = {}

    def put(x, y, z, block):
        blocks[(x, y, z)] = block

    for x in range(-SKIN, SKIN + 1):
        for z in range(-SKIN, SKIN + 1):
            for y in (-1, 0, TOP + 1, TOP + 2):
                put(x, y, z, VOID)
            if max(abs(x), abs(z)) > HALL:
                for y in range(-1, TOP + 3):
                    put(x, y, z, VOID)

    for p in knot:
        put(p[0], p[1], p[2], BRICK)

    # The cut. Deck first, then clear the channel above it - order matters, because the knot runs
    # straight through this volume and has to lose.
    bridge = set()
    for x in range(-BRIDGE_HALF, BRIDGE_HALF + 1):
        for z in range(-HALL, HALL + 1):
            put(x, BRIDGE_Y, z, CRACKED if (x + z) % 7 == 0 else BRICK)
            bridge.add((x, z))
            for y in range(BRIDGE_Y + 1, BRIDGE_Y + 1 + BRIDGE_CLEAR):
                blocks.pop((x, y, z), None)

    # ---- checks ------------------------------------------------------------
    solid = set(blocks)
    walk_y = BRIDGE_Y + 1

    for x, z in bridge:
        for y in range(walk_y, walk_y + 2):
            if (x, y, z) in solid:
                raise AssertionError(f"bridge is blocked at ({x},{y},{z}).")

    pale, dark = (0, BED_Z), (0, -BED_Z)
    start, goal = (pale[0], walk_y, pale[1]), (dark[0], walk_y, dark[1])
    reach, queue = {start}, deque([start])
    while queue:
        x, y, z = queue.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, y, z + dz)
            if (abs(n[0]) <= HALL and abs(n[2]) <= HALL and n not in reach
                    and (n[0], n[1] - 1, n[2]) in solid
                    and all((n[0], n[1] + k, n[2]) not in solid for k in (0, 1))):
                reach.add(n)
                queue.append(n)
    if goal not in reach:
        raise AssertionError("the dark Nexus is unreachable along the bridge.")

    # The knot must actually reach the middle - that is the whole reason it replaced a Cantor dust.
    middle = [p for p in knot if max(abs(p[0]), abs(p[2])) <= 4]
    if not middle:
        raise AssertionError("nothing within 4 blocks of the centre - the room has a hollow core again.")

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
        raise AssertionError("room is NOT sealed - outside air reaches the bridge.")

    # ---- emit --------------------------------------------------------------
    shift = 1
    lines = [f"# The Knot. Stand at the CENTRE of the room, on the ground, in CLEAR AIR, and run:",
             f"#   /function build:{NAME}",
             f"# Generated by tools/generate_knot_room.py - do not hand-edit.", ""]
    for (x, y, z), block in blocks.items():
        lines.append(f"setblock ~{x} ~{y + shift} ~{z} {block}")

    for block, cell, facing in ((PALE_BED, pale, "south"), (DARK_BED, dark, "north")):
        dz = 1 if facing == "south" else -1
        lines.append(f"setblock ~{cell[0]} ~{walk_y + shift} ~{cell[1]} "
                     f"{block}[facing={facing},part=foot]")
        lines.append(f"setblock ~{cell[0]} ~{walk_y + shift} ~{cell[1] + dz} "
                     f"{block}[facing={facing},part=head]")

    os.makedirs(FUNC_DIR, exist_ok=True)
    with open(f"{PACK}/pack.mcmeta", "w") as f:
        f.write('{\n  "pack": {\n    "pack_format": 48,\n'
                '    "description": "dim_descent build helpers"\n  }\n}\n')
    with open(f"{FUNC_DIR}/{NAME}.mcfunction", "w") as f:
        f.write("\n".join(lines) + "\n")

    lo_y = min(p[1] for p in blocks)
    hi_y = max(p[1] for p in blocks)
    span_xz, span_y = 2 * SKIN + 1, hi_y - lo_y + 1
    volume = (span + 1) ** 2 * (span + 1)
    print(f"wrote {FUNC_DIR}/{NAME}.mcfunction")
    print(f"  {len(lines) - 4} setblock commands")
    print(f"  order-{ORDER} 3D Hilbert curve verified: visits all {side ** 3} cells once, "
          f"every step adjacent")
    print(f"  {len(knot)} blocks of pipe in a {span + 1}-block cube ({100 * len(knot) / volume:.0f}% "
          f"density), {len(middle)} of them within 4 blocks of the centre")
    print(f"  bridge {2 * BRIDGE_HALF + 1} wide, {len(reach)} cells reachable, dark Nexus reached")
    print(f"  capture size {span_xz} x {span_y} x {span_xz} "
          f"({'OK' if max(span_xz, span_y) <= 48 else 'TOO BIG'} for the 48 cap)")
    print()
    print("  structure blocks, standing at the run position P:")
    print(f"    SAVE   corner at  P + ({-SKIN - 1}, -1, {-SKIN - 1})")
    print(f"    CORNER corner at  P + ({SKIN + 1}, {span_y}, {SKIN + 1})")


if __name__ == "__main__":
    main()
