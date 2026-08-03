#!/usr/bin/env python3
"""Generate a .mcfunction that builds the Throat.

    python3 tools/generate_throat_room.py

WHAT WAS WRONG THE FIRST TWO TIMES
Cut one was rectangular: a corridor with a trick in it, resolved by the eye in about a second. Cut
two went circular but chopped the bore off with a wide flat floor, which removed most of the lower
half and left a shape the eye read as a chamfered box. Everything that makes a circle look like a
circle lives in the half that floor was covering.

THE IDEA
A lie about distance, told by a shape with no flat surfaces in it.

  A COMPLETE CIRCLE.  The bore is a full circle with nothing cut out of it, crossed by a three-wide
                      catwalk hanging on the axis with open bore underneath. Nothing is flat and
                      there is no wall/ceiling join anywhere, so there is no straight edge for the
                      eye to measure the taper along.
  BIG ENOUGH TO BE ROUND.  A discretised circle of radius 2.5 is a lozenge and one of radius 5 is an
                      octagon; at 8.5 it is a circle. Radius is the single biggest lever on whether
                      the shape reads as round at all, and it costs nothing but the 48-block cap.
  RIBBED.             A full circumferential rib every five blocks, standing proud of the bore.
                      These *draw* the circle rather than leaving the eye to infer it, and being
                      evenly spaced down a tapering tube they hammer the perspective as well.
  IT TAPERS.          Radius falls from 8.5 to 2.5 over thirty-four blocks. The eye assumes a
                      constant bore and puts the far end more than three times further than it is.
  IT IS RIFLED.       Three helical ribs wind down the inside like the rifling in a barrel. Moving
                      along the axis of a helix makes them appear to rotate, and because the player
                      is the thing moving, the rotation reads as their own.
  THE TWIST ACCELERATES.  The helix winds faster as the bore narrows (t**1.6), so the apparent spin
                      speeds up walking in and slows walking out. Nothing moves and it still feels
                      like the room is turning.

The underside of the bore is lined in Nullstone rather than brick, so looking over the edge of the
catwalk gives black instead of a brick gutter. The rest of the ring stays brick, which is what keeps
the circle legible - a fully black bore would be a circle nobody can see.

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

R_NEAR, R_FAR = 8.5, 2.5     # bore radius. The ratio is the perceived-length multiplier.
AXIS_Y = 2                   # the bore's centre line, flat - the catwalk hangs below it
R_NEAR_CH, R_FAR_CH = 9.0, 4.5

DECK_Y = 0                   # catwalk deck, two below the axis, so eye height lands on the axis -
DECK_HALF = 1                # which is the point the whole taper shrinks about. Three wide.
DECK_CLEAR = 4               # air kept above the deck; no rib may enter it

RING_SPACING = 5             # blocks between circumferential ribs
RING_DEPTH = 1.3             # how far a ring stands proud of the bore
BLACK_ARC = 0.9              # radians either side of straight down lined in Nullstone, so looking
                             # over the catwalk edge gives void rather than brickwork

TURNS = 2.5            # revolutions of the rifling over the length of the bore
TWIST_POWER = 1.6      # >1 makes the helix wind faster as the bore narrows
RIBS = 3
RIB_DEPTH = 1.7        # how far a rib stands proud of the bore wall
RIB_ARC = 0.34         # angular half-width of a rib, radians

BED_INSET = 2
CRACK_CHANCE = 0.16
random.seed(20260808)


def brick():
    return CRACKED if random.random() < CRACK_CHANCE else BRICK


def profile(z):
    """Bore radius at this z. The axis is flat, so only the radius carries the taper."""
    if z < -BORE_Z:
        return R_NEAR_CH
    if z > BORE_Z:
        return R_FAR_CH
    t = (z + BORE_Z) / (2 * BORE_Z)
    return R_NEAR + (R_FAR - R_NEAR) * t


def twist(z):
    """Rifling angle at this z. Accelerates, so the apparent spin is not constant."""
    t = min(1.0, max(0.0, (z + BORE_Z) / (2 * BORE_Z)))
    return 2 * math.pi * TURNS * (t ** TWIST_POWER)


def main():
    end_z = BORE_Z + CHAMBER
    max_r = max(R_NEAR_CH, R_FAR_CH, R_NEAR)
    max_x = int(max_r) + 1
    top_y = int(AXIS_Y + max_r) + 1
    bot_y = int(AXIS_Y - max_r) - 1

    blocks = {}

    def put(x, y, z, block):
        blocks[(x, y, z)] = block

    # Solid brick, then bore the tunnel out of it - a shell carved from a solid can never drift out
    # of alignment with the void it wraps.
    for x in range(-max_x - 1, max_x + 2):
        for y in range(bot_y - 1, top_y + 2):
            for z in range(-end_z - 1, end_z + 2):
                put(x, y, z, brick())

    interior = set()
    for z in range(-end_z, end_z + 1):
        r = profile(z)
        base = twist(z)
        ring = -BORE_Z <= z <= BORE_Z and (z + BORE_Z) % RING_SPACING == 0
        for x in range(-max_x, max_x + 1):
            for y in range(bot_y, top_y + 1):
                dx, dy = x, y - AXIS_Y
                d = math.hypot(dx, dy)
                if d > r:
                    continue
                # Ribs and rings are cells inside the bore left uncarved, so they stand proud of it.
                if ring and d > r - RING_DEPTH:
                    continue
                if d > r - RIB_DEPTH:
                    phi = math.atan2(dy, dx)
                    if any(abs((phi - base - k * 2 * math.pi / RIBS + math.pi)
                               % (2 * math.pi) - math.pi) < RIB_ARC for k in range(RIBS)):
                        continue
                blocks.pop((x, y, z), None)
                interior.add((x, y, z))

    # The catwalk, and the corridor above it. Cut last, so it wins against every rib and ring it
    # meets - a ring interrupted where the walkway passes through it is the correct look anyway.
    for z in range(-end_z, end_z + 1):
        for x in range(-DECK_HALF - 1, DECK_HALF + 2):
            for y in range(DECK_Y + 1, DECK_Y + 1 + DECK_CLEAR):
                blocks.pop((x, y, z), None)
                interior.add((x, y, z))
        for x in range(-DECK_HALF, DECK_HALF + 1):
            put(x, DECK_Y, z, brick())
            interior.discard((x, DECK_Y, z))

    # Line the underside of the bore in Nullstone, so looking over the catwalk edge gives void
    # instead of a brick gutter. The rest of the ring stays brick, which keeps the circle legible.
    for (x, y, z) in list(blocks):
        if abs(z) > end_z:
            continue
        r = profile(z)
        dx, dy = x, y - AXIS_Y
        d = math.hypot(dx, dy)
        if r < d <= r + 2.5 and dy < 0 and abs(math.atan2(dx, -dy)) < BLACK_ARC:
            put(x, y, z, VOID)

    lo = (-max_x - 2, bot_y - 2, -end_z - 2)
    hi = (max_x + 2, top_y + 2, end_z + 2)
    for x in range(lo[0], hi[0] + 1):
        for y in range(lo[1], hi[1] + 1):
            for z in range(lo[2], hi[2] + 1):
                if x in (lo[0], hi[0]) or y in (lo[1], hi[1]) or z in (lo[2], hi[2]):
                    put(x, y, z, VOID)

    # ---- checks ------------------------------------------------------------
    solid = set(blocks)

    prev_r, rings = None, 0
    for z in range(-BORE_Z, BORE_Z + 1):
        r = profile(z)
        if (z + BORE_Z) % RING_SPACING == 0:
            rings += 1
        head = 0
        while (0, DECK_Y + 1 + head, z) in interior:
            head += 1
        if head < 2:
            raise AssertionError(f"headroom {head} over the deck at z={z}. Raise R_FAR or DECK_Y.")
        for x in range(-DECK_HALF, DECK_HALF + 1):
            if (x, DECK_Y, z) not in solid:
                raise AssertionError(f"catwalk deck missing at ({x},{z}).")
            for y in range(DECK_Y + 1, DECK_Y + 3):
                if (x, y, z) in solid:
                    raise AssertionError(f"a rib is blocking the catwalk at ({x},{y},{z}).")
        if prev_r is not None and r > prev_r + 1e-9:
            raise AssertionError(f"the bore widens at z={z} - the illusion inverts and the tunnel "
                                 f"reads SHORTER than it is on the way in.")
        prev_r = r

    # The bore must be a full circle and not an arch. Count the open blocks directly under the deck
    # at the near end - that column is exactly what the old flat floor was filling in.
    below = sum(1 for y in range(bot_y, DECK_Y) if (0, y, -BORE_Z) not in solid)
    if below < 4:
        raise AssertionError(f"only {below} open blocks under the deck at the near end - the bore "
                             f"has been cut off from below and will read as a box again.")

    def standable(p):
        x, y, z = p
        return ((x, y - 1, z) in solid
                and all((x, y + k, z) not in solid for k in (0, 1)))

    pale = (0, DECK_Y + 1, -end_z + BED_INSET)
    dark = (BED_INSET, DECK_Y + 1, end_z - BED_INSET)
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
        raise AssertionError("the dark Nexus is unreachable on foot.")

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
    shift = -lo[1]
    lines = ["# The Throat. Stand at the CENTRE of the room, on the ground, in CLEAR AIR:",
             f"#   /function build:{NAME}",
             "# Generated by tools/generate_throat_room.py - do not hand-edit.", ""]
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
    actual, ratio = 2 * BORE_Z, R_NEAR / R_FAR
    print(f"wrote {FUNC_DIR}/{NAME}.mcfunction")
    print(f"  {len(lines) - 4} setblock commands")
    print(f"  FULL circular bore, {2 * R_NEAR:.0f} across near, {2 * R_FAR:.0f} far - nothing cut off")
    print(f"  {2 * DECK_HALF + 1}-wide catwalk on the axis, {below} blocks of open bore beneath it")
    print(f"  {rings} rings, plus {RIBS}-start rifling ({TURNS} turns, t^{TWIST_POWER})")
    print(f"  bore verified monotonic; actual length {actual}, reads as about "
          f"{actual * ratio:.0f} ({ratio:.1f}x)")
    print(f"  capture size {span[0]} x {span[1]} x {span[2]} "
          f"({'OK' if max(span) <= 48 else 'TOO BIG'} for the 48 cap)")
    print()
    print("  structure blocks, standing at the run position P:")
    print(f"    SAVE   corner at  P + ({lo[0] - 1}, -1, {lo[2] - 1})")
    print(f"    CORNER corner at  P + ({hi[0] + 1}, {span[1]}, {hi[2] + 1})")


if __name__ == "__main__":
    main()
