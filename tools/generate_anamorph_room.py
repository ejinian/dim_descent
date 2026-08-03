#!/usr/bin/env python3
"""Generate a .mcfunction that builds the Anamorph.

    python3 tools/generate_anamorph_room.py

THE IDEA
A figure standing in the dark in front of you, made of blocks that are not in the shape of a figure
and are not anywhere near each other. Step one pace sideways and it is gone - not moved, not hidden,
*gone*, because it was never there. It was a coincidence of sightlines that only existed from where
you were standing.

This is the one room in the pool that is about the player rather than about geometry, and it is only
possible because arrival is deterministic. NullDomainRooms.arrivalAt puts the player's feet on the
block two steps past the pale Nexus's foot, facing the reverse of the bed, every single time - so the
exact position of their eyes on entry is known while the room is still being generated.

HOW IT WORKS, AND THE CONSTRAINT THAT SHAPES EVERYTHING
Cast a ray from the eye through each lit cell of a picture and put a block somewhere along it. The
catch is angular size: a 1-block cube at distance d covers 1/d radians, so it only fills its cell of
the picture at one specific distance. Put it nearer and it spills over its neighbours; further and it
leaves a gap.

So depth cannot be arbitrary - it is quantised. A cell can sit at D0 as a single block, or at 2*D0
as a 2x2x2 cluster, or at 3*D0 as 3x3x3, and nothing in between. Each cell picks one at random, which
is what scatters the thing in depth: the near layer is a fine dust of single blocks and the far layer
is a coarse rubble of 2x2 lumps, twice as far away and twice as big, and neither half is recognisable
on its own. The 48-block cap is what limits this to two layers - three would need the room to be half
again as deep as the format can hold.

IT IS VERIFIED BY RENDERING IT
An anamorphic build that is subtly wrong looks like scattered blocks, which is also what a correct
one looks like from anywhere except the arrival square - so eyeballing it proves nothing. Instead the
generator ray-marches the finished block set from the computed eye position, one ray per picture
cell, and asserts the silhouette that comes back is the picture. Then it does it again from four
blocks to the side and asserts the figure has *fallen apart* - because a build that reads correctly
from everywhere is just a statue, and the whole point is that it is not.
"""

import math
import os
import random
from collections import deque

WORLD = "run/saves/DimDescentRoomBuilder"
PACK = f"{WORLD}/datapacks/dimdescent_build"
FUNC_DIR = f"{PACK}/data/build/function"
NAME = "anamorph"

BRICK = "dimdescent:altar_stone_bricks"
CRACKED = "dimdescent:cracked_altar_stone_bricks"
VOID = "dimdescent:nullstone"
DARK_BED = "dimdescent:dream_bed"
PALE_BED = "dimdescent:pale_dream_bed"

# ---------------------------------------------------------------------------
# THE PICTURE. Editable string art; '#' is a lit cell. A standing figure, arms at its sides - the
# same thing the Hallucination is, which is the point: it is there and then it is not.
# ---------------------------------------------------------------------------
FIGURE = [
    "....###....",
    "....###....",
    "...#####...",
    "..#######..",
    ".#.#####.#.",
    ".#.#####.#.",
    ".#.#####.#.",
    ".#.#####.#.",
    "...#####...",
    "...#####...",
    "....#.#....",
    "....#.#....",
    "....#.#....",
    "....#.#....",
    "...##.##...",
]

D0 = 16            # reference distance: where a cell is exactly one block across
SCALES = (1, 2)    # depth layers, as multiples of D0. Cluster size equals the multiple.
IMAGE_UP = 9       # cells the picture's centre sits above eye level, measured at D0

EYE_HEIGHT = 1.62  # vanilla standing eye height above the feet block
STEP_ASIDE = 4     # how far sideways the "does it fall apart" check moves
MAX_INTACT = 0.5   # ...and the most of the figure allowed to survive that move

MARGIN = 2         # air between the outermost block and the shell
CRACK_CHANCE = 0.18
random.seed(20260810)


def brick():
    return CRACKED if random.random() < CRACK_CHANCE else BRICK


def main():
    h, w = len(FIGURE), len(FIGURE[0])
    if any(len(row) != w for row in FIGURE):
        raise AssertionError("FIGURE rows are not all the same length.")
    lit = [(i, j) for j in range(h) for i in range(w) if FIGURE[j][i] == "#"]

    # Arrival square is the origin. The player faces +z, so screen-right is -x (face south in
    # Minecraft and west is on your right), and up is +y.
    fwd = (0.0, 0.0, 1.0)
    right = (-1.0, 0.0, 0.0)
    up = (0.0, 1.0, 0.0)
    eye = (0.5, EYE_HEIGHT, 0.5)

    def ray(i, j):
        """Offset from the eye to picture cell (i, j) on the reference plane."""
        ox = i - (w - 1) / 2.0
        oy = (h - 1) / 2.0 - j + IMAGE_UP
        return tuple(D0 * fwd[a] + ox * right[a] + oy * up[a] for a in range(3))

    blocks = {}
    debris = set()
    for (i, j) in lit:
        k = random.choice(SCALES)
        d = ray(i, j)
        centre = tuple(eye[a] + k * d[a] for a in range(3))
        for a3 in range(k):
            for b3 in range(k):
                for c3 in range(k):
                    off = [(a3 - (k - 1) / 2.0) * right[a]
                           + (b3 - (k - 1) / 2.0) * up[a]
                           + (c3 - (k - 1) / 2.0) * fwd[a] for a in range(3)]
                    cell = tuple(math.floor(centre[a] + off[a]) for a in range(3))
                    debris.add(cell)

    lo = [min(c[a] for c in debris) for a in range(3)]
    hi = [max(c[a] for c in debris) for a in range(3)]
    lo[0] = min(lo[0], -MARGIN - 1)
    hi[0] = max(hi[0], MARGIN + 1)
    lo[1] = min(lo[1], -1)
    lo[2] = min(lo[2], -4)
    inner_lo = [lo[a] - MARGIN for a in range(3)]
    inner_hi = [hi[a] + MARGIN for a in range(3)]
    inner_lo[1] = min(inner_lo[1], -1)

    def put(c, block):
        blocks[c] = block

    # Nullstone box, two layers, so the debris hangs in something with no depth cues at all.
    for x in range(inner_lo[0] - 2, inner_hi[0] + 3):
        for y in range(inner_lo[1] - 2, inner_hi[1] + 3):
            for z in range(inner_lo[2] - 2, inner_hi[2] + 3):
                outside = not (inner_lo[0] <= x <= inner_hi[0]
                               and inner_lo[1] <= y <= inner_hi[1]
                               and inner_lo[2] <= z <= inner_hi[2])
                if outside:
                    put((x, y, z), VOID)
    for x in range(inner_lo[0], inner_hi[0] + 1):
        for z in range(inner_lo[2], inner_hi[2] + 1):
            put((x, -1, z), VOID)

    for c in debris:
        put(c, brick())

    pale_head = (0, 0, -2)
    pale_foot = (0, 0, -1)
    dark_foot = (0, 0, inner_hi[2] - 2)
    dark_head = (0, 0, inner_hi[2] - 1)

    # ---- checks ------------------------------------------------------------
    for c in debris:
        if not all(inner_lo[a] <= c[a] <= inner_hi[a] for a in range(3)):
            raise AssertionError(f"debris block {c} is outside the room.")
        if c[1] <= 0:
            raise AssertionError(f"debris block {c} is at or below head height - it would be an "
                                 f"obstacle rather than an image.")

    # How far a ray has to travel, measured ALONG THE RAY rather than along the forward axis. Cells
    # near the top of the picture sit far off-axis, so their rays are much longer than D0 * scale -
    # using the axial distance stops the march short and the top of the figure silently vanishes.
    reach = max(SCALES) * max(math.sqrt(sum(v * v for v in ray(i, j))) for (i, j) in lit) + 4

    def render(origin):
        """Ray-march one ray per picture cell and report which come back lit."""
        out = set()
        limit = reach
        for (i, j) in [(i, j) for j in range(h) for i in range(w)]:
            d = ray(i, j)
            length = math.sqrt(sum(v * v for v in d))
            step = [v / length for v in d]
            t = 0.5
            while t < limit:
                cell = tuple(math.floor(origin[a] + step[a] * t) for a in range(3))
                if cell in debris:
                    out.add((i, j))
                    break
                t += 0.08
        return out

    seen = render(eye)
    target = set(lit)
    if seen != target:
        missing, extra = target - seen, seen - target
        raise AssertionError(
            f"the picture does not resolve from the arrival square: {len(missing)} cells missing, "
            f"{len(extra)} spurious. First missing {sorted(missing)[:3]}. A cluster is occluding a "
            f"ray it does not belong to, or a scale/distance pair is inconsistent.")

    aside = tuple(eye[a] + STEP_ASIDE * right[a] for a in range(3))
    intact = len(render(aside) & target) / len(target)
    if intact > MAX_INTACT:
        raise AssertionError(
            f"{intact:.0%} of the figure survives a {STEP_ASIDE}-block step sideways - that is a "
            f"statue, not an anamorph. Spread SCALES further apart.")

    # ---- emit --------------------------------------------------------------
    shift = -(inner_lo[1] - 2)
    lines = ["# The Anamorph. Stand ON THE ARRIVAL SQUARE facing SOUTH (+z), in CLEAR AIR:",
             f"#   /function build:{NAME}",
             "# The origin is where the player's feet land, NOT the centre of the room.",
             "# Generated by tools/generate_anamorph_room.py - do not hand-edit.", ""]
    for (x, y, z), block in blocks.items():
        lines.append(f"setblock ~{x} ~{y + shift} ~{z} {block}")

    lines.append(f"setblock ~{pale_foot[0]} ~{pale_foot[1] + shift} ~{pale_foot[2]} "
                 f"{PALE_BED}[facing=north,part=foot]")
    lines.append(f"setblock ~{pale_head[0]} ~{pale_head[1] + shift} ~{pale_head[2]} "
                 f"{PALE_BED}[facing=north,part=head]")
    lines.append(f"setblock ~{dark_foot[0]} ~{dark_foot[1] + shift} ~{dark_foot[2]} "
                 f"{DARK_BED}[facing=south,part=foot]")
    lines.append(f"setblock ~{dark_head[0]} ~{dark_head[1] + shift} ~{dark_head[2]} "
                 f"{DARK_BED}[facing=south,part=head]")

    os.makedirs(FUNC_DIR, exist_ok=True)
    with open(f"{PACK}/pack.mcmeta", "w") as f:
        f.write('{\n  "pack": {\n    "pack_format": 48,\n'
                '    "description": "dim_descent build helpers"\n  }\n}\n')
    with open(f"{FUNC_DIR}/{NAME}.mcfunction", "w") as f:
        f.write("\n".join(lines) + "\n")

    box_lo = [inner_lo[a] - 2 for a in range(3)]
    box_hi = [inner_hi[a] + 2 for a in range(3)]
    span = [box_hi[a] - box_lo[a] + 1 for a in range(3)]
    print(f"wrote {FUNC_DIR}/{NAME}.mcfunction")
    print(f"  {len(lines) - 5} setblock commands, {len(debris)} debris blocks from "
          f"{len(lit)} picture cells")
    print(f"  layers at {' and '.join(str(k * D0) for k in SCALES)} blocks, "
          f"cluster sizes {' and '.join(f'{k}x{k}x{k}' for k in SCALES)}")
    print(f"  RESOLVES from the arrival square: all {len(target)} cells correct")
    print(f"  COLLAPSES {STEP_ASIDE} blocks aside: only {intact:.0%} of the figure survives")
    print(f"  capture size {span[0]} x {span[1]} x {span[2]} "
          f"({'OK' if max(span) <= 48 else 'TOO BIG'} for the 48 cap)")
    print()
    print("  structure blocks, relative to the ARRIVAL SQUARE P:")
    print(f"    SAVE   corner at  P + ({box_lo[0] - 1}, {box_lo[1] + shift - 1}, {box_lo[2] - 1})")
    print(f"    CORNER corner at  P + ({box_hi[0] + 1}, {box_hi[1] + shift + 1}, {box_hi[2] + 1})")


if __name__ == "__main__":
    main()
