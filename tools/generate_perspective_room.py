#!/usr/bin/env python3
"""Generate a .mcfunction that builds the Perspective.

    python3 tools/generate_perspective_room.py

THE IDEA
A corridor that lies about how long it is.

Every cue the eye uses to judge depth in a corridor - how far apart the walls are, how high the
ceiling is, how the courses of brick converge - assumes the corridor has parallel sides. Break that
assumption and the eye has no way to know. This one starts seven wide and seven tall and finishes
three wide and two tall over thirty-one blocks, with the floor rising and the ceiling falling
together so both converge on eye level. The brain reads the far end as the same size as the near end,
much further away, and puts the end of the corridor at about seventy blocks. It is thirty-one.

Theatre sets have done this for four hundred years and it works here for the same reason: there is
nothing at the far end to measure against.

WHY IT WORKS TWICE
Walk back and the taper runs the other way, so the corridor now widens toward you and reads as much
SHORTER than it is. The trip out and the trip back feel like different distances, which is a
genuinely unpleasant thing to notice about a room you have already crossed once.

WHAT WOULD BREAK IT
Anything of known size at the far end - so the dark Nexus is round the corner in a side chamber,
offset off the sightline, and there is nothing else in here at all. The player's own body is the
other reference, which is why the illusion decays honestly as they walk. That is fine. The lie is
supposed to come apart; it just should not come apart at the door.
"""

import os
import random
from collections import deque

WORLD = "run/saves/DimDescentRoomBuilder"
PACK = f"{WORLD}/datapacks/dimdescent_build"
FUNC_DIR = f"{PACK}/data/build/function"
NAME = "perspective"

BRICK = "dimdescent:altar_stone_bricks"
CRACKED = "dimdescent:cracked_altar_stone_bricks"
VOID = "dimdescent:nullstone"
DARK_BED = "dimdescent:dream_bed"
PALE_BED = "dimdescent:pale_dream_bed"

# ---------------------------------------------------------------------------
# SHAPE. The room is one long axis; z is length, x is width.
#
# NEAR_* and FAR_* are the whole illusion. The ratio between them is roughly the factor by which the
# corridor's apparent length is inflated, so NEAR_W / FAR_W = 3 means a 31-block hall reads as ~93.
# Push FAR_H below 2 and the corridor stops being walkable; the assertions catch it.
# ---------------------------------------------------------------------------
HALL_Z = 15           # corridor runs -HALL_Z .. +HALL_Z
CHAMBER = 6           # depth of the chamber at each end

NEAR_W, FAR_W = 3, 1  # half-widths -> 7 wide down to 3
NEAR_H, FAR_H = 7, 2  # headroom
NEAR_F, FAR_F = 0, 3  # floor height, rising toward the far end

CH_W = 5              # near chamber half-width
FAR_CH_W = 3
BED_INSET = 2

CRACK_CHANCE = 0.16
random.seed(20260807)


def brick():
    return CRACKED if random.random() < CRACK_CHANCE else BRICK


def lerp(a, b, t):
    return int(a + (b - a) * t + 0.5)


def profile(z):
    """(half-width, floor y, headroom) at this z."""
    if z < -HALL_Z:
        return CH_W, NEAR_F, NEAR_H
    if z > HALL_Z:
        return FAR_CH_W, FAR_F, FAR_H + 1
    t = (z + HALL_Z) / (2 * HALL_Z)
    return lerp(NEAR_W, FAR_W, t), lerp(NEAR_F, FAR_F, t), lerp(NEAR_H, FAR_H, t)


def main():
    end_z = HALL_Z + CHAMBER
    max_x = max(CH_W, FAR_CH_W, NEAR_W)
    tops = [profile(z)[1] + profile(z)[2] for z in range(-end_z, end_z + 1)]
    top_y = max(tops)

    blocks = {}

    def put(x, y, z, block):
        blocks[(x, y, z)] = block

    # Solid brick block, then carve the tunnel out of it. Same reason the cylinder recipe carves
    # rather than assembling: the shell cannot drift out of alignment with the void it wraps.
    for x in range(-max_x - 1, max_x + 2):
        for y in range(-1, top_y + 2):
            for z in range(-end_z - 1, end_z + 2):
                put(x, y, z, brick())

    interior = set()
    for z in range(-end_z, end_z + 1):
        w, f, h = profile(z)
        for x in range(-w, w + 1):
            for y in range(f + 1, f + h + 1):
                blocks.pop((x, y, z), None)
                interior.add((x, y, z))

    # Nullstone skin over the whole thing.
    lo = (-max_x - 2, -2, -end_z - 2)
    hi = (max_x + 2, top_y + 2, end_z + 2)
    for x in range(lo[0], hi[0] + 1):
        for y in range(lo[1], hi[1] + 1):
            for z in range(lo[2], hi[2] + 1):
                if x in (lo[0], hi[0]) or y in (lo[1], hi[1]) or z in (lo[2], hi[2]):
                    put(x, y, z, VOID)

    # ---- checks ------------------------------------------------------------
    solid = set(blocks)

    prev = None
    for z in range(-end_z, end_z + 1):
        w, f, h = profile(z)
        if h < 2:
            raise AssertionError(f"headroom {h} at z={z} - not walkable. Raise FAR_H.")
        if prev:
            pw, pf, ph = prev
            if abs(f - pf) > 1:
                raise AssertionError(f"floor steps {abs(f - pf)} at z={z} - not walkable.")
            if -HALL_Z < z <= HALL_Z and (w > pw or h > ph):
                raise AssertionError(
                    f"the taper widens at z={z} ({pw}->{w}, {ph}->{h}) - the illusion inverts and "
                    f"the corridor reads SHORTER than it is on the way in.")
        prev = (w, f, h)

    def standable(p):
        x, y, z = p
        return ((x, y - 1, z) in solid
                and all((x, y + k, z) not in solid for k in (0, 1)))

    pale = (0, NEAR_F + 1, -end_z + BED_INSET)
    dark = (BED_INSET, FAR_F + 1, end_z - BED_INSET)
    for label, p in (("pale", pale), ("dark", dark)):
        if not standable(p):
            raise AssertionError(f"the {label} Nexus at {p} is not standing on anything.")
    if dark[0] == 0:
        raise AssertionError("the dark Nexus is on the sightline - it gives the far end a scale "
                             "reference and kills the illusion from the door.")

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
                    and lo[2] - 1 <= n[2] <= hi[2] + 1
                    and n not in seen and n not in solid):
                seen.add(n)
                queue.append(n)
    if pale in seen:
        raise AssertionError("room is NOT sealed - outside air reaches the corridor.")

    # ---- emit --------------------------------------------------------------
    shift = 2
    lines = [f"# The Perspective. Stand at the CENTRE of the room, on the ground, in CLEAR AIR:",
             f"#   /function build:{NAME}",
             f"# Generated by tools/generate_perspective_room.py - do not hand-edit.", ""]
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
    actual = 2 * HALL_Z
    ratio = (2 * NEAR_W + 1) / (2 * FAR_W + 1)
    print(f"wrote {FUNC_DIR}/{NAME}.mcfunction")
    print(f"  {len(lines) - 4} setblock commands")
    print(f"  corridor {2 * NEAR_W + 1}x{NEAR_H} at the near end, {2 * FAR_W + 1}x{FAR_H} at the far")
    print(f"  taper verified monotonic; floor rises {FAR_F - NEAR_F} over {actual} blocks")
    print(f"  actual length {actual}, reads as about {actual * ratio:.0f} ({ratio:.1f}x)")
    print(f"  capture size {span[0]} x {span[1]} x {span[2]} "
          f"({'OK' if max(span) <= 48 else 'TOO BIG'} for the 48 cap)")
    print()
    print("  structure blocks, standing at the run position P:")
    print(f"    SAVE   corner at  P + ({lo[0] - 1}, -1, {lo[2] - 1})")
    print(f"    CORNER corner at  P + ({hi[0] + 1}, {span[1]}, {hi[2] + 1})")


if __name__ == "__main__":
    main()
