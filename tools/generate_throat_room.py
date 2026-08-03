#!/usr/bin/env python3
"""Generate a .mcfunction that builds the Throat.

    python3 tools/generate_throat_room.py

WHAT WAS WRONG WITH THE SQUARE VERSION
A rectangular tunnel that tapers is one idea executed once. It reads as a corridor with a trick in
it, you see the trick immediately, and then there is nothing left to look at. Every surface is flat
and every line is straight, so the eye resolves the whole room in about a second.

THE IDEA
Same lie about distance, told by a shape with no flat surfaces in it.

  CIRCULAR SECTION.  The tunnel is a circle, cut off by a flat walkway near the bottom. There is no
                     wall/ceiling join anywhere, so there is no edge for the eye to measure along -
                     which makes the taper much harder to read as a taper.
  IT TAPERS.         Radius falls from 5.5 to 2.0 over 35 blocks while the floor rises to meet it.
                     The eye assumes a constant bore and puts the far end nearly three times further
                     away than it is.
  IT IS RIFLED.      Three helical ribs wind down the inside of the bore, like the rifling in a
                     barrel. Walking it, they appear to rotate around you, and because you are moving
                     along the axis of a helix the rotation reads as *your own* rotation.
  THE TWIST ACCELERATES.  The helix winds faster as the bore narrows (t**1.6), so the apparent spin
                     speeds up as you walk in and slows as you walk out. Nothing in Minecraft moves,
                     and it still feels like the room is turning.

The ribs stop three blocks above the floor, so the walkway stays clear and the spin happens entirely
in peripheral vision. That is deliberate - it is far more unpleasant than something you can look at
directly.

WHAT WOULD BREAK IT
Anything of known size at the far end, so the dark Nexus is offset off the sightline in the end
chamber. Asserted, along with the bore never widening - a taper that reverses anywhere inverts the
illusion and the tunnel reads shorter than it is.
"""

import math
import os
import random
from collections import deque

WORLD = "run/saves/DimDescentRoomBuilder"
PACK = f"{WORLD}/datapacks/dimdescent_build"
FUNC_DIR = f"{PACK}/data/build/function"
NAME = "throat"

BRICK = "dimdescent:altar_stone_bricks"
CRACKED = "dimdescent:cracked_altar_stone_bricks"
VOID = "dimdescent:nullstone"
DARK_BED = "dimdescent:dream_bed"
PALE_BED = "dimdescent:pale_dream_bed"

# ---------------------------------------------------------------------------
# SHAPE
# ---------------------------------------------------------------------------
BORE_Z = 17            # tunnel runs -BORE_Z .. +BORE_Z
CHAMBER = 4            # round chamber depth at each end

R_NEAR, R_FAR = 5.5, 2.0     # bore radius. The ratio is the perceived-length multiplier.
F_NEAR, F_FAR = 0, 3         # floor height, rising toward the far end
AXIS_FRAC = 0.55             # how far the bore's centre sits above the floor, as a fraction of r

R_NEAR_CH, R_FAR_CH = 6.0, 3.0

TURNS = 2.5            # revolutions of the rifling over the length of the bore
TWIST_POWER = 1.6      # >1 makes the helix wind faster as the bore narrows
RIBS = 3
RIB_DEPTH = 1.7        # how far a rib stands proud of the bore wall
RIB_ARC = 0.34         # angular half-width of a rib, radians
RIB_CLEAR = 3          # ribs never come below floor + this, so the walkway stays open

BED_INSET = 2
CRACK_CHANCE = 0.16
random.seed(20260808)


def brick():
    return CRACKED if random.random() < CRACK_CHANCE else BRICK


def profile(z):
    """(radius, floor y) at this z."""
    if z < -BORE_Z:
        return R_NEAR_CH, F_NEAR
    if z > BORE_Z:
        return R_FAR_CH, F_FAR
    t = (z + BORE_Z) / (2 * BORE_Z)
    return R_NEAR + (R_FAR - R_NEAR) * t, F_NEAR + (F_FAR - F_NEAR) * t


def twist(z):
    """Rifling angle at this z. Accelerates, so the apparent spin is not constant."""
    t = min(1.0, max(0.0, (z + BORE_Z) / (2 * BORE_Z)))
    return 2 * math.pi * TURNS * (t ** TWIST_POWER)


def main():
    end_z = BORE_Z + CHAMBER
    max_r = max(R_NEAR_CH, R_FAR_CH, R_NEAR)
    max_x = int(max_r) + 1
    top_y = int(max(profile(z)[1] + (1 + AXIS_FRAC) * profile(z)[0]
                    for z in range(-end_z, end_z + 1))) + 1

    blocks = {}

    def put(x, y, z, block):
        blocks[(x, y, z)] = block

    # Solid brick, then bore the tunnel out of it - the shell can never drift out of alignment with
    # a void it was carved from.
    for x in range(-max_x - 1, max_x + 2):
        for y in range(-1, top_y + 2):
            for z in range(-end_z - 1, end_z + 2):
                put(x, y, z, brick())

    interior = set()
    for z in range(-end_z, end_z + 1):
        r, fz = profile(z)
        f = int(fz + 0.5)
        axis = fz + AXIS_FRAC * r
        base = twist(z)
        for x in range(-max_x, max_x + 1):
            for y in range(f + 1, top_y + 1):
                dx, dy = x, y - axis
                d = math.hypot(dx, dy)
                if d > r:
                    continue
                if d > r - RIB_DEPTH and y >= f + RIB_CLEAR:
                    phi = math.atan2(dy, dx)
                    if any(abs((phi - base - k * 2 * math.pi / RIBS + math.pi)
                               % (2 * math.pi) - math.pi) < RIB_ARC for k in range(RIBS)):
                        continue          # this cell is a rib - leave it solid
                blocks.pop((x, y, z), None)
                interior.add((x, y, z))

    lo = (-max_x - 2, -2, -end_z - 2)
    hi = (max_x + 2, top_y + 2, end_z + 2)
    for x in range(lo[0], hi[0] + 1):
        for y in range(lo[1], hi[1] + 1):
            for z in range(lo[2], hi[2] + 1):
                if x in (lo[0], hi[0]) or y in (lo[1], hi[1]) or z in (lo[2], hi[2]):
                    put(x, y, z, VOID)

    # ---- checks ------------------------------------------------------------
    solid = set(blocks)

    prev_r = None
    for z in range(-BORE_Z, BORE_Z + 1):
        r, fz = profile(z)
        f = int(fz + 0.5)
        head = 0
        while (0, f + 1 + head, z) in interior:
            head += 1
        if head < 2:
            raise AssertionError(f"headroom {head} on the axis at z={z} - not walkable. Raise R_FAR.")
        width = sum(1 for x in range(-max_x, max_x + 1) if (x, f + 1, z) in interior)
        if width < 2:
            raise AssertionError(f"walkway is {width} wide at z={z}.")
        if prev_r is not None and r > prev_r + 1e-9:
            raise AssertionError(f"the bore widens at z={z} - the illusion inverts and the tunnel "
                                 f"reads SHORTER than it is on the way in.")
        prev_r = r

    def standable(p):
        x, y, z = p
        return ((x, y - 1, z) in solid
                and all((x, y + k, z) not in solid for k in (0, 1)))

    pale = (0, F_NEAR + 1, -end_z + BED_INSET)
    dark = (BED_INSET, F_FAR + 1, end_z - BED_INSET)
    if dark[0] == 0:
        raise AssertionError("the dark Nexus is on the sightline - it hands the player a scale "
                             "reference at the far end and kills the illusion from the door.")
    for label, p in (("pale", pale), ("dark", dark)):
        if not standable(p):
            raise AssertionError(f"the {label} Nexus at {p} is not standing on anything.")

    reach, queue = {pale}, deque([pale])
    while queue:
        x, y, z = queue.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for ny in (y + 1, y, y - 1):
                n = (x + dx, ny, z + dz)
                if n not in reach and standable(n):
                    reach.add(n)
                    queue.append(n)
                    break
    if dark not in reach:
        raise AssertionError("the dark Nexus is unreachable on foot - a rib may be blocking the bore.")

    seen, queue = set(), deque()
    for x in range(lo[0] - 1, hi[0] + 2):
        for y in range(lo[1], hi[1] + 2):
            for z in range(lo[2] - 1, hi[2] + 2):
                if (x in (lo[0] - 1, hi[0] + 1) or y == hi[1] + 1 or z in (lo[2] - 1, hi[2] + 1)) \
                        and (x, y, z) not in solid:
                    seen.add((x, y, z))
                    queue.append((x, y, z))
    while queue:
        x, y, z = queue.popleft()
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + d[0], y + d[1], z + d[2])
            if (lo[0] - 1 <= n[0] <= hi[0] + 1 and lo[1] <= n[1] <= hi[1] + 1
                    and lo[2] - 1 <= n[2] <= hi[2] + 1 and n not in seen and n not in solid):
                seen.add(n)
                queue.append(n)
    if pale in seen:
        raise AssertionError("room is NOT sealed - outside air reaches the bore.")

    # ---- emit --------------------------------------------------------------
    shift = 2
    lines = [f"# The Throat. Stand at the CENTRE of the room, on the ground, in CLEAR AIR:",
             f"#   /function build:{NAME}",
             f"# Generated by tools/generate_throat_room.py - do not hand-edit.", ""]
    for (x, y, z), block in blocks.items():
        lines.append(f"setblock ~{x} ~{y + shift} ~{z} {block}")

    for block, cell, facing in ((PALE_BED, pale, "north"), (DARK_BED, dark, "south")):
        dz = -1 if facing == "north" else 1
        lines.append(f"setblock ~{cell[0]} ~{cell[1] + shift} ~{cell[2]} "
                     f"{block}[facing={facing},part=foot]")
        lines.append(f"setblock ~{cell[0]} ~{cell[1] + shift} ~{cell[2] + dz} "
                     f"{block}[facing={facing},part=head]")

    os.makedirs(FUNC_DIR, exist_ok=True)
    with open(f"{PACK}/pack.mcmeta", "w") as f:
        f.write('{\n  "pack": {\n    "pack_format": 48,\n'
                '    "description": "dim_descent build helpers"\n  }\n}\n')
    with open(f"{FUNC_DIR}/{NAME}.mcfunction", "w") as f:
        f.write("\n".join(lines) + "\n")

    lo_y, hi_y = min(p[1] for p in blocks), max(p[1] for p in blocks)
    span = (hi[0] - lo[0] + 1, hi_y - lo_y + 1, hi[2] - lo[2] + 1)
    actual = 2 * BORE_Z
    ratio = R_NEAR / R_FAR
    print(f"wrote {FUNC_DIR}/{NAME}.mcfunction")
    print(f"  {len(lines) - 4} setblock commands")
    print(f"  bore {2 * R_NEAR:.0f} across at the near end, {2 * R_FAR:.0f} at the far; "
          f"floor rises {F_FAR - F_NEAR}")
    print(f"  {RIBS}-start rifling, {TURNS} turns, accelerating (t^{TWIST_POWER})")
    print(f"  bore verified monotonic; actual length {actual}, reads as about "
          f"{actual * ratio:.0f} ({ratio:.1f}x)")
    print(f"  {len(reach)} cells reachable on foot, dark Nexus offset off the sightline")
    print(f"  capture size {span[0]} x {span[1]} x {span[2]} "
          f"({'OK' if max(span) <= 48 else 'TOO BIG'} for the 48 cap)")
    print()
    print("  structure blocks, standing at the run position P:")
    print(f"    SAVE   corner at  P + ({lo[0] - 1}, -1, {lo[2] - 1})")
    print(f"    CORNER corner at  P + ({hi[0] + 1}, {span[1]}, {hi[2] + 1})")


if __name__ == "__main__":
    main()
