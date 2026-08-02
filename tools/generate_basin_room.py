#!/usr/bin/env python3
"""Generate a .mcfunction that builds the Basin room.

    python3 tools/generate_basin_room.py

THE IDEA
Every room in the pool so far has a flat floor and a flat ceiling, because that is all WorldEdit
primitives can draw. This one ripples BOTH surfaces from the same decaying wave, in antiphase:

    floor(r)   = -A * cos(2*pi*r / L) * exp(-r / D)
    ceiling(r) = CLEARANCE - floor(r)

Because the ceiling mirrors the floor instead of following it, headroom swings by twice the ripple
amplitude. The player crosses a shallow basin under a 13-block vault, then has to stoop through a
3-block ring where the floor swells up and the ceiling sags down to meet it, then it opens out
again. Nothing about the room is visibly "built" - it reads as a space that got pressed.

That antiphase trick is the whole reason this is a script. It is one line of maths and no sequence
of //set, //cyl or //faces can express it.

WHY THIS IS NOT A WORLDEDIT //generate
WorldEdit's //generate is real and would draw the surfaces, but it takes ONE pattern for the whole
expression, has no notion of "the top block is a grate and the two under it are hollow", and cannot
place a bed. It is a shape tool. This is a room.

THE ASSERTIONS ARE THE POINT
Three invariants are checked before anything is written, so a bad constant fails here instead of
being discovered in-game after a capture:
  * every orthogonal step in the floor is at most 1 block, or the room is not walkable
  * headroom is never under MIN_HEADROOM, or the player gets stuck in the geometry
  * the room is SEALED - air flooded from outside cannot reach the interior. RoomContainment's
    shrink-wrap relies on exactly this; an unsealed room lets the flood in and Nullstone-coats the
    inside of the build.
"""

import math
import os
import random
from collections import deque

WORLD = "run/saves/DimDescentRoomBuilder"
PACK = f"{WORLD}/datapacks/dimdescent_build"
FUNC_DIR = f"{PACK}/data/build/function"
NAME = "basin"

BRICK = "dimdescent:altar_stone_bricks"
CRACKED = "dimdescent:cracked_altar_stone_bricks"
PILLAR = "dimdescent:carved_altar_stone"
GRATE = "dimdescent:dark_iron_bars"
SCONCE = "dimdescent:daemonlight_wall"
DARK_BED = "dimdescent:dream_bed"
PALE_BED = "dimdescent:pale_dream_bed"

# ---------------------------------------------------------------------------
# SHAPE. Radius and the three wave constants are the whole design.
#
# Keep AMP * 2*pi / WAVELENGTH under ~0.8 or the floor gets too steep to walk and the slope
# assertion trips. Raising AMP without raising WAVELENGTH is the usual way to break this.
# ---------------------------------------------------------------------------
R = 20                # half-width -> 41x41 footprint
AMP = 3.0             # ripple amplitude, blocks
WAVELENGTH = 24.0     # blocks per full ripple - one basin plus one raised ring at this radius
DECAY = 18.0          # the ripple flattens out over this distance
CLEARANCE = 8         # nominal floor-to-ceiling gap on the undisturbed plane

MIN_HEADROOM = 3      # air blocks between floor top and ceiling underside

PILLAR_SPACING = 7    # grid pitch for the pillar field
PILLAR_MIN_R = 6      # leave the centre of the basin open
PILLAR_MAX_R = 19     # and keep clear of the walls
SCONCE_MIN_R = 10     # only the outer pillars are lit

GRATE_INNER = 3.5     # ring drain around the centre - sells the room as a thing that held liquid
GRATE_OUTER = 4.5
GRATE_DEPTH = 2       # hollow under the bars

CRACK_CHANCE = 0.22
random.seed(0xBA51)


def brick():
    return CRACKED if random.random() < CRACK_CHANCE else BRICK


def floor_at(r):
    """Floor surface height. Negative at the centre, so the room opens with a basin."""
    return -math.floor(AMP * math.cos(2 * math.pi * r / WAVELENGTH) * math.exp(-r / DECAY) + 0.5)


def ceiling_at(r):
    """Ceiling underside. Mirrored, not parallel - that is what makes headroom breathe."""
    return CLEARANCE - floor_at(r)


def main():
    cells = [(x, z) for x in range(-R, R + 1) for z in range(-R, R + 1)]
    radius = {c: math.hypot(*c) for c in cells}
    fh = {c: floor_at(radius[c]) for c in cells}
    ch = {c: ceiling_at(radius[c]) for c in cells}

    floor_bottom = min(fh.values()) - 1
    ceil_top = max(ch.values()) + 1

    grate = {c for c in cells if GRATE_INNER <= radius[c] <= GRATE_OUTER}

    # ---- geometry checks, before a single line is written -------------------
    interior = [c for c in cells if abs(c[0]) < R and abs(c[1]) < R]
    for x, z in interior:
        for nx, nz in ((x + 1, z), (x, z + 1)):
            if (nx, nz) in fh and abs(fh[(x, z)] - fh[(nx, nz)]) > 1:
                raise AssertionError(
                    f"floor steps {abs(fh[(x, z)] - fh[(nx, nz)])} blocks at ({x},{z}) - "
                    f"not walkable. Lower AMP or raise WAVELENGTH.")
    tight = min(ch[c] - fh[c] - 1 for c in interior)
    if tight < MIN_HEADROOM:
        raise AssertionError(f"headroom drops to {tight} - raise CLEARANCE or lower AMP.")

    # ---- build ------------------------------------------------------------
    blocks = {}

    def put(x, y, z, block):
        blocks[(x, y, z)] = block

    for c in cells:
        x, z = c
        top = fh[c] - 1 if c in grate else fh[c]
        for y in range(floor_bottom, top + 1):
            put(x, y, z, brick())
        for y in range(ch[c], ceil_top + 1):
            put(x, y, z, brick())

    # Perimeter wall, full height. Overwrites the floor/ceiling edge, which is what we want.
    for c in cells:
        x, z = c
        if abs(x) == R or abs(z) == R:
            for y in range(floor_bottom, ceil_top + 1):
                put(x, y, z, brick())

    # Hollow under the drain ring, then the bars themselves.
    for c in grate:
        x, z = c
        for y in range(fh[c] - GRATE_DEPTH, fh[c]):
            put(x, y, z, "minecraft:air")
        put(x, fh[c], z, GRATE)

    pillars = []
    for c in interior:
        x, z = c
        if x % PILLAR_SPACING or z % PILLAR_SPACING:
            continue
        if not (PILLAR_MIN_R <= radius[c] <= PILLAR_MAX_R):
            continue
        pillars.append(c)
        for y in range(fh[c] + 1, ch[c]):
            put(x, y, z, PILLAR)

    # One sconce per outer pillar, on the face looking back at the centre.
    for c in pillars:
        x, z = c
        if radius[c] < SCONCE_MIN_R:
            continue
        if abs(x) >= abs(z):
            step, facing = (-1 if x > 0 else 1, 0), "west" if x > 0 else "east"
        else:
            step, facing = (0, -1 if z > 0 else 1), "north" if z > 0 else "south"
        sx, sz = x + step[0], z + step[1]
        put(sx, fh[c] + 2, sz, f"{SCONCE}[facing={facing},lit=true]")

    # ---- seal check, on the finished block set ------------------------------
    solid = {p for p, b in blocks.items() if b != "minecraft:air"}
    lo = (-R - 1, floor_bottom - 1, -R - 1)
    hi = (R + 1, ceil_top + 1, R + 1)
    seen = set()
    queue = deque()
    for x in range(lo[0], hi[0] + 1):
        for y in range(lo[1], hi[1] + 1):
            for z in range(lo[2], hi[2] + 1):
                on_shell = x in (lo[0], hi[0]) or y in (lo[1], hi[1]) or z in (lo[2], hi[2])
                if on_shell and (x, y, z) not in solid:
                    seen.add((x, y, z))
                    queue.append((x, y, z))
    while queue:
        x, y, z = queue.popleft()
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + d[0], y + d[1], z + d[2])
            if not all(lo[i] <= n[i] <= hi[i] for i in range(3)):
                continue
            if n in seen or n in solid:
                continue
            seen.add(n)
            queue.append(n)
    for c in interior:
        for y in range(fh[c] + 1, ch[c]):
            if (c[0], y, c[1]) in seen:
                raise AssertionError(
                    f"room is NOT sealed - outside air reaches ({c[0]},{y},{c[1]}). "
                    f"RoomContainment would Nullstone-coat the interior.")

    # ---- emit --------------------------------------------------------------
    lines = [f"# The Basin. Stand at the CENTRE of the room, on the ground, and run:",
             f"#   /function build:{NAME}",
             f"# Generated by tools/generate_basin_room.py - do not hand-edit.", ""]
    shift = -floor_bottom  # so the lowest block lands at the executor's feet
    for (x, y, z), block in blocks.items():
        lines.append(f"setblock ~{x} ~{y + shift} ~{z} {block}")

    # Beds last: the dark one at the bottom of the basin inside the drain ring, the pale one
    # (entrance, and the way back) against the north wall.
    def bed(block, fx, fz, facing):
        hx, hz = {"north": (fx, fz - 1), "south": (fx, fz + 1),
                  "west": (fx - 1, fz), "east": (fx + 1, fz)}[facing]
        y = fh[(fx, fz)] + 1 + shift
        lines.append(f"setblock ~{fx} ~{y} ~{fz} {block}[facing={facing},part=foot]")
        lines.append(f"setblock ~{hx} ~{y} ~{hz} {block}[facing={facing},part=head]")

    bed(DARK_BED, 0, 1, "north")
    bed(PALE_BED, 0, -(R - 3), "north")

    os.makedirs(FUNC_DIR, exist_ok=True)
    with open(f"{PACK}/pack.mcmeta", "w") as f:
        f.write('{\n  "pack": {\n    "pack_format": 48,\n'
                '    "description": "dim_descent build helpers"\n  }\n}\n')
    with open(f"{FUNC_DIR}/{NAME}.mcfunction", "w") as f:
        f.write("\n".join(lines) + "\n")

    span_y = ceil_top - floor_bottom + 1
    span_xz = R * 2 + 1
    print(f"wrote {FUNC_DIR}/{NAME}.mcfunction")
    print(f"  {len(lines) - 4} setblock commands, {len(pillars)} pillars, {len(grate)} grate cells")
    print(f"  floor {min(fh.values())}..{max(fh.values())}, headroom {tight}..{max(ch[c] - fh[c] - 1 for c in interior)}")
    print(f"  capture size {span_xz} x {span_y} x {span_xz} "
          f"({'OK' if max(span_xz, span_y) <= 48 else 'TOO BIG'} for the 48 cap)")
    print(f"  slope, headroom and seal checks all passed")
    print()
    print("  structure blocks, standing at the run position P:")
    print(f"    SAVE   corner at  P + ({-R - 1}, -1, {-R - 1})")
    print(f"    CORNER corner at  P + ({R + 1}, {span_y}, {R + 1})")


if __name__ == "__main__":
    main()
