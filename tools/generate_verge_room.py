#!/usr/bin/env python3
"""Generate a .mcfunction that builds the Verge.

    python3 tools/generate_verge_room.py

THE IDEA
A stretch of road at night, with the night removed and nothing put back.

Forty-five blocks of asphalt with painted lines, a raised kerb, pavements, clipped hedges in brick
planters, and a row of lamp standards down each side - and then, immediately past the hedge, nothing.
No verge running on, no buildings, no horizon. The road does not come from anywhere and does not go
anywhere; it is a piece of infrastructure with no settlement attached to it.

This is the most ordinary thing in the whole mod and it should be the worst one to stand in. Every
other room announces itself as a dungeon. This one is a road, and a road that exists in a black box
implies a town that does not.

  THE MARKINGS DO THE WORK.  Two solid edge lines and a dashed centre line, and they are the reason
      it reads instantly as a ROAD rather than as a dark floor. They also give the eye a perspective
      cue that runs straight into the black, which is what makes the far end look like distance
      rather than like a wall.
  SOME OF THE LAMPS ARE OUT.  Roughly one in five has grey panels instead of white. A row of lamps
      all lit is a diagram; a row with gaps in it is a place that is maintained by somebody, badly.
  ONE HAS COME DOWN.  A single standard lies across the pavement where it fell. It is the only
      evidence in the room that any time has passed.

Everything except the shell is a vanilla block - concrete, stone brick, brick, flowering azalea -
because the point is that this is a piece of the ordinary world, and altar brick would make it
architecture again.
"""

import math
import os
import random
from collections import deque

WORLD = "run/saves/DimDescentRoomBuilder"
PACK = f"{WORLD}/datapacks/dimdescent_build"
FUNC_DIR = f"{PACK}/data/build/function"
NAME = "verge"

VOID = "dimdescent:nullstone"
ASPHALT = "minecraft:black_concrete"
PAINT = "minecraft:white_concrete"
PAVING = "minecraft:gray_concrete"
KERB = "minecraft:smooth_stone"
POST = "minecraft:stone_bricks"
LAMP_ON = "minecraft:white_concrete"
LAMP_OFF = "minecraft:light_gray_concrete"
PLANTER = "minecraft:bricks"
HEDGE = "minecraft:flowering_azalea_leaves[distance=7,persistent=true,waterlogged=false]"
DARK_BED = "dimdescent:dream_bed"
PALE_BED = "dimdescent:pale_dream_bed"

LENGTH = 21        # road runs -LENGTH .. +LENGTH. 22 put the capture box at 49 - one over.
TOP = 18           # interior height

ROAD_HALF = 8      # carriageway half-width
EDGE_LINE = 7      # solid white line at this |x|
DASH_ON, DASH_OFF = 2, 3     # centre line rhythm along z
KERB_X = 9
PAVE_X = (10, 11)  # walkable pavement, where the standards stand
HEDGE_X = 12
WALL_X = 13

LAMP_X = 10
LAMP_SPACING = 7
LAMP_H = 9         # post height above the pavement
LAMP_ARM = 2       # how far the cross arms reach
LAMP_DEAD = 0.20   # fraction of standards with the lights out

CRACK = 0.0
random.seed(20260816)


def main():
    blocks = {}

    def put(x, y, z, block):
        blocks[(x, y, z)] = block

    interior = [(x, z) for x in range(-WALL_X, WALL_X + 1)
                for z in range(-LENGTH, LENGTH + 1)]

    skin = WALL_X + 2
    for x in range(-skin, skin + 1):
        for z in range(-LENGTH - 2, LENGTH + 3):
            for y in range(-2, TOP + 3):
                if (max(abs(x) - WALL_X, abs(z) - LENGTH) > 0) or y in (-2, -1, TOP + 1, TOP + 2):
                    put(x, y, z, VOID)

    # Carriageway, kerb, pavement.
    road = set()
    for x, z in interior:
        a = abs(x)
        if a <= ROAD_HALF:
            paint = (a == EDGE_LINE) or (x == 0 and (z + LENGTH) % (DASH_ON + DASH_OFF) < DASH_ON)
            put(x, 0, z, PAINT if paint else ASPHALT)
            road.add((x, z))
        elif a == KERB_X:
            put(x, 0, z, KERB)
            put(x, 1, z, KERB)
        else:
            put(x, 0, z, KERB)
            put(x, 1, z, PAVING)

    # Planters and hedge along the back of each pavement.
    for x, z in interior:
        a = abs(x)
        if a == WALL_X:
            put(x, 2, z, PLANTER)
            put(x, 3, z, PLANTER)
        elif a == HEDGE_X:
            put(x, 2, z, HEDGE)
            put(x, 3, z, HEDGE)

    # Lamp standards. A four-armed cross with a panel on each arm, which is what the reference has.
    def standard(px, pz, top_y, dead, lying=False):
        if lying:
            # Down along the pavement, laid toward the middle of the room so it always has space.
            # It lies ALONG z rather than across it: a full-length arm reaching sideways would put
            # its head on the carriageway, which the road check refuses - correctly, since a block
            # lying in the road at head height is an obstacle, not scenery.
            run = -1 if pz > 0 else 1
            for i in range(LAMP_H):
                put(px, 2, pz + run * i, POST)
            head = pz + run * (LAMP_H - 1)
            for d in (1, -1):
                put(px + d, 2, head, LAMP_OFF)
            return
        for y in range(2, top_y):
            put(px, y, pz, POST)
        for d in (1, -1):
            put(px + d, top_y, pz, POST)
            put(px, top_y, pz + d, POST)
            put(px + d * LAMP_ARM, top_y, pz, LAMP_OFF if dead else LAMP_ON)
            put(px, top_y, pz + d * LAMP_ARM, LAMP_OFF if dead else LAMP_ON)
        put(px, top_y, pz, POST)

    posts = []
    for side in (-1, 1):
        for z in range(-LENGTH + 3, LENGTH - 2, LAMP_SPACING):
            posts.append((side * LAMP_X, z))
    fallen = random.randrange(len(posts))
    lamps_out = 0
    for i, (px, pz) in enumerate(posts):
        if i == fallen:
            standard(px, pz, 2 + LAMP_H, True, lying=True)
            continue
        dead = random.random() < LAMP_DEAD
        lamps_out += dead
        standard(px, pz, 2 + LAMP_H, dead)

    pale_foot, pale_head = (0, 1, LENGTH - 3), (0, 1, LENGTH - 2)
    dark_foot, dark_head = (0, 1, -(LENGTH - 3)), (0, 1, -(LENGTH - 2))

    # ---- checks ------------------------------------------------------------
    solid = set(blocks)

    def standable(p):
        return ((p[0], p[1] - 1, p[2]) in solid
                and all((p[0], p[1] + k, p[2]) not in solid for k in (0, 1)))

    for label, p in (("pale", pale_foot), ("dark", dark_foot)):
        if not standable(p):
            raise AssertionError(f"the {label} Nexus at {p} is not standing on anything.")

    for x, z in road:
        for y in (1, 2):
            if (x, y, z) in solid:
                raise AssertionError(f"something is on the carriageway at ({x},{y},{z}).")

    reach, queue = {pale_foot}, deque([pale_foot])
    while queue:
        x, y, z = queue.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for ny in (y + 1, y, y - 1):
                n = (x + dx, ny, z + dz)
                if (max(abs(n[0]) - WALL_X, abs(n[2]) - LENGTH) <= 0
                        and n not in reach and standable(n)):
                    reach.add(n)
                    queue.append(n)
                    break
    if dark_foot not in reach:
        raise AssertionError("the road is not crossable end to end.")
    if not any(abs(c[0]) in PAVE_X for c in reach):
        raise AssertionError("the pavement cannot be stepped onto - the kerb is too high.")

    for c, b in blocks.items():
        if "azalea_leaves" in b and "persistent=true" not in b:
            raise AssertionError(f"hedge at {c} is not persistent - it will decay away.")

    top = max(c[1] for c in blocks if blocks[c] in (POST, LAMP_ON, LAMP_OFF))
    if top + 1 >= TOP:
        raise AssertionError(f"the standards reach y={top}, into the ceiling at {TOP}.")

    painted = sum(1 for c, b in blocks.items() if b == PAINT)
    if painted < 100:
        raise AssertionError(f"only {painted} blocks of paint - without the markings this reads as "
                             f"a dark floor, not a road.")

    lo, hi = (-skin, -2, -LENGTH - 2), (skin, TOP + 2, LENGTH + 2)
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
        raise AssertionError("room is NOT sealed - outside air reaches the road.")

    # ---- emit --------------------------------------------------------------
    shift = 2
    lines = ["# The Verge. Stand at the CENTRE of the road, in CLEAR AIR, and run:",
             f"#   /function build:{NAME}",
             "# Generated by tools/generate_verge_room.py - do not hand-edit.", ""]
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

    span = (2 * skin + 1, TOP + 5, 2 * LENGTH + 5)
    print(f"wrote {FUNC_DIR}/{NAME}.mcfunction")
    print(f"  {len(lines) - 4} setblock commands")
    print(f"  {2 * LENGTH + 1} blocks of carriageway, {2 * ROAD_HALF + 1} wide, "
          f"{painted} blocks of paint")
    print(f"  {len(posts)} lamp standards, {lamps_out} with the lights out, 1 come down")
    print(f"  hedge and planters both sides; road crossable end to end, pavement steppable")
    print(f"  capture size {span[0]} x {span[1]} x {span[2]} "
          f"({'OK' if max(span) <= 48 else 'TOO BIG'} for the 48 cap)")
    print()
    print("  structure blocks, relative to the room CENTRE P:")
    print(f"    SAVE   corner at  P + ({-skin - 1}, -1, {-LENGTH - 3})")
    print(f"    CORNER corner at  P + ({skin + 1}, {span[1]}, {LENGTH + 3})")


if __name__ == "__main__":
    main()
