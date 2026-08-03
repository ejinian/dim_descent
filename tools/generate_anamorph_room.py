#!/usr/bin/env python3
"""Generate a .mcfunction that builds the Anamorph.

    python3 tools/generate_anamorph_room.py

THE IDEA
A figure standing in the dark in front of you, assembled out of four flat slabs hanging at three
different distances, none of which is the shape of anything. Walk sideways and it comes apart - the
head slides off the shoulders, the arms slide off the torso - because it was never a figure, only a
coincidence of sightlines that existed from exactly where you were standing.

Only possible because arrival is deterministic: NullDomainRooms.arrivalAt puts the player's feet on
the block two steps past the pale Nexus's foot, facing the reverse of the bed, every time. Their eye
position on entry is known while the room is still being generated.

WHY THE FIRST ATTEMPT FAILED, AND WHAT THE ACTUAL ALGORITHM IS
Cut one put one independent cube per picture cell, each at a randomly chosen depth. That is
geometrically correct and completely illegible. Two adjacent cells at different depths are two
separate floating cubes: each projects as a hexagon with three differently-shaded faces, and their
outlines do not meet edge to edge, so a region that should read as one solid shape comes out as a
gappy pile of blocks with shading discontinuities all through it.

Real anamorphosis is painted on a SMALL NUMBER OF FLAT SURFACES, not on per-pixel depth noise. So:

  1. Partition the picture into contiguous regions - head, arms, torso, legs.
  2. Give each region ONE plane, one block thick, square to the sightline, at its own depth.
  3. RASTERISE rather than project: for every block in that plane, work out which picture cell it
     falls into and place it if that cell belongs to this region.

Step 3 is what makes it work. Projecting cell -> blocks leaves rounding gaps between neighbours;
rasterising block -> cell means every block in the region is filled and no block is claimed twice, so
each body part comes out as one unbroken slab with no internal seams at all.

Region partitioning also kills occlusion for free: regions are disjoint in the picture, so no ray to
a far region can pass through a near one.

DEPTH IS QUANTISED
A block at distance d subtends 1/d, so a plane at k*D0 renders each picture cell as exactly k*k
blocks. k must be a whole number - there is no depth between 2*D0 and 3*D0 that tiles. Three layers
is what fits the 48-block cap, since the far plane is three times the linear size of the near one.

THE FIGURE IS ALLSTONE AND THE ROOM IS NULLSTONE
Not a palette choice. A silhouette is defined by figure-against-ground, so a dark figure in a black
void is invisible by construction. Allstone is the right material for the specific reason that it has
NO face shading at all - a slab of it is a perfectly flat white shape with no internal gradient to
give away that it is made of cubes, which is exactly what an anamorph needs. (To make the figure dark
instead, invert it: a lit backdrop with a figure-shaped hole in it.)

IT IS VERIFIED BY RENDERING IT
Eyeballing an anamorph proves nothing - a broken one looks like scattered blocks, which is also what
a correct one looks like from anywhere but the arrival square. The generator ray-marches the finished
block set from the computed eye at nine samples per picture cell and asserts the silhouette that
comes back IS the picture. Cut one only asked "does this ray hit anything", which passed while the
thing was unreadable; supersampling asks the stronger question, which is whether the EDGES land where
the picture's edges are.
"""

import math
import os
from collections import deque

WORLD = "run/saves/DimDescentRoomBuilder"
PACK = f"{WORLD}/datapacks/dimdescent_build"
FUNC_DIR = f"{PACK}/data/build/function"
NAME = "anamorph"

FIG = "dimdescent:allstone"
VOID = "dimdescent:nullstone"
DARK_BED = "dimdescent:dream_bed"
PALE_BED = "dimdescent:pale_dream_bed"

# ---------------------------------------------------------------------------
# THE PICTURE. Editable string art. Each letter is a REGION, and every region gets its own plane at
# its own distance, so letters that touch must differ or the seam between them will not shear.
# Bold shapes only: a one-cell detail is invisible at this angular size.
# ---------------------------------------------------------------------------
# NOTE the height. A layer at k*D0 scales the picture's height offset by k as well as its size, so
# the tallest layer needs k * (IMAGE_UP + h/2) blocks of room above the eye while the nearest still
# has to clear the player's head. Nine rows is what lets three depth layers fit under the 48 cap;
# eleven rows forced the legs down to head height and tripped FLOOR_CLEAR.
FIGURE = [
    "....HHH....",
    "...HHHHH...",
    ".AASSSSSAA.",
    ".AASSSSSAA.",
    ".AASSSSSAA.",
    "...SSSSS...",
    "...LL.LL...",
    "...LL.LL...",
    "...LL.LL...",
]

# Region -> depth, as a multiple of D0. Neighbours must differ.
DEPTH = {"H": 1, "A": 3, "S": 2, "L": 1}

D0 = 11            # reference distance: where one picture cell is one block across
IMAGE_UP = 8       # cells the picture's centre sits above eye level, measured at D0
EYE_HEIGHT = 1.62  # vanilla standing eye height above the feet block

SUPERSAMPLE = 3    # rays per picture cell per axis
MIN_MATCH = 0.95   # ...and how much of the render must agree with the picture
STEP_ASIDE = 8     # how far sideways the "does it come apart" check walks
MAX_INTACT = 0.60  # ...and the most of the figure allowed to survive it

MARGIN = 2
FLOOR_CLEAR = 4    # no part of the figure may hang lower than this above the floor


def main():
    h, w = len(FIGURE), len(FIGURE[0])
    if any(len(row) != w for row in FIGURE):
        raise AssertionError("FIGURE rows are not all the same length.")
    cell_region = {(i, j): FIGURE[j][i] for j in range(h) for i in range(w)
                   if FIGURE[j][i] != "."}
    for r in set(cell_region.values()):
        if r not in DEPTH:
            raise AssertionError(f"region '{r}' has no entry in DEPTH.")

    # Neighbouring regions must sit at different depths, or their shared edge stays rigid when the
    # player moves and that part of the figure never comes apart.
    for (i, j), r in cell_region.items():
        for n in ((i + 1, j), (i, j + 1)):
            other = cell_region.get(n)
            if other and other != r and DEPTH[other] == DEPTH[r]:
                raise AssertionError(
                    f"regions '{r}' and '{other}' touch at {n} and share depth {DEPTH[r]} - that "
                    f"seam will not shear. Give them different DEPTH values.")

    # Arrival square is the origin. Facing +z (south), screen-right is -x, up is +y.
    eye = (0.5, EYE_HEIGHT, 0.5)
    cx, cy = (w - 1) / 2.0, (h - 1) / 2.0 + IMAGE_UP

    def cell_of(px, py, pz):
        """Which picture cell a world point projects into, as floats."""
        dz = pz - eye[2]
        if dz <= 0.01:
            return None
        scale = D0 / dz
        return (-(px - eye[0]) * scale + cx, cy - (py - eye[1]) * scale)

    # ---- rasterise each region onto its own plane ---------------------------
    figure = {}
    for region, k in DEPTH.items():
        cells = [c for c, r in cell_region.items() if r == region]
        if not cells:
            continue
        plate_z = k * D0            # block whose CENTRE sits exactly k*D0 from the eye
        reach_x = k * w
        reach_y = k * (h + IMAGE_UP) + 4
        for bx in range(-reach_x, reach_x + 1):
            for by in range(0, reach_y + 1):
                fi, fj = cell_of(bx + 0.5, by + 0.5, plate_z + 0.5)
                i, j = round(fi), round(fj)
                if cell_region.get((i, j)) == region:
                    figure[(bx, by, plate_z)] = region

    if not figure:
        raise AssertionError("nothing was rasterised - check D0 and IMAGE_UP.")
    low = min(c[1] for c in figure)
    if low < FLOOR_CLEAR:
        raise AssertionError(f"the figure reaches down to y={low}, under FLOOR_CLEAR - it would be "
                             f"an obstacle in the room rather than an image above it.")

    # ---- verify by rendering ------------------------------------------------
    solid = set(figure)
    reach = max(DEPTH.values()) * D0 * 3 + 6

    def sample_dirs():
        for j in range(h):
            for i in range(w):
                for b in range(SUPERSAMPLE):
                    for a in range(SUPERSAMPLE):
                        fi = i + (a + 0.5) / SUPERSAMPLE - 0.5
                        fj = j + (b + 0.5) / SUPERSAMPLE - 0.5
                        ox, oy = fi - cx, cy - fj
                        yield (i, j), (-ox, oy, float(D0))

    def render(origin):
        hits = set()
        for idx, (key, d) in enumerate(sample_dirs()):
            length = math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2])
            step = (d[0] / length, d[1] / length, d[2] / length)
            t = 0.5
            while t < reach:
                cell = (math.floor(origin[0] + step[0] * t),
                        math.floor(origin[1] + step[1] * t),
                        math.floor(origin[2] + step[2] * t))
                if cell in solid:
                    hits.add(idx)
                    break
                t += 0.12
        return hits

    keys = [key for key, _ in sample_dirs()]
    want = {n for n, key in enumerate(keys) if key in cell_region}
    got = render(eye)
    agree = sum(1 for n in range(len(keys)) if (n in want) == (n in got)) / len(keys)
    if agree < MIN_MATCH:
        raise AssertionError(
            f"the picture only resolves to {agree:.1%} from the arrival square, under {MIN_MATCH:.0%}. "
            f"{len(want - got)} lit samples empty, {len(got - want)} spurious. A plane is misaligned "
            f"or a region is being occluded.")

    aside = (eye[0] - STEP_ASIDE, eye[1], eye[2])
    intact = len(render(aside) & want) / len(want)
    if intact > MAX_INTACT:
        raise AssertionError(
            f"{intact:.0%} of the figure survives a {STEP_ASIDE}-block step sideways, over "
            f"{MAX_INTACT:.0%} - that is a statue. Spread the DEPTH values further apart.")

    # ---- room ---------------------------------------------------------------
    lo = [min(c[a] for c in figure) for a in range(3)]
    hi = [max(c[a] for c in figure) for a in range(3)]
    inner_lo = [lo[0] - MARGIN, -1, -4]
    inner_hi = [hi[0] + MARGIN, hi[1] + MARGIN, hi[2] + MARGIN]
    inner_lo[0] = min(inner_lo[0], -MARGIN - 1)
    inner_hi[0] = max(inner_hi[0], MARGIN + 1)

    blocks = {}
    for x in range(inner_lo[0] - 2, inner_hi[0] + 3):
        for y in range(inner_lo[1] - 2, inner_hi[1] + 3):
            for z in range(inner_lo[2] - 2, inner_hi[2] + 3):
                if not (inner_lo[0] <= x <= inner_hi[0] and inner_lo[1] <= y <= inner_hi[1]
                        and inner_lo[2] <= z <= inner_hi[2]):
                    blocks[(x, y, z)] = VOID
    for x in range(inner_lo[0], inner_hi[0] + 1):
        for z in range(inner_lo[2], inner_hi[2] + 1):
            blocks[(x, -1, z)] = VOID
    for c in figure:
        blocks[c] = FIG

    pale_head, pale_foot = (0, 0, -2), (0, 0, -1)
    dark_foot, dark_head = (0, 0, inner_hi[2] - 2), (0, 0, inner_hi[2] - 1)

    # ---- emit ---------------------------------------------------------------
    shift = -(inner_lo[1] - 2)
    lines = ["# The Anamorph. Stand ON THE ARRIVAL SQUARE, in CLEAR AIR, and run:",
             f"#   /function build:{NAME}",
             "# The origin is where the player's FEET land, not the centre of the room.",
             "# Generated by tools/generate_anamorph_room.py - do not hand-edit.", ""]
    for (x, y, z), block in blocks.items():
        lines.append(f"setblock ~{x} ~{y + shift} ~{z} {block}")
    for block, foot, head, facing in ((PALE_BED, pale_foot, pale_head, "north"),
                                      (DARK_BED, dark_foot, dark_head, "south")):
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

    box_lo = [inner_lo[a] - 2 for a in range(3)]
    box_hi = [inner_hi[a] + 2 for a in range(3)]
    span = [box_hi[a] - box_lo[a] + 1 for a in range(3)]
    print(f"wrote {FUNC_DIR}/{NAME}.mcfunction")
    print(f"  {len(lines) - 5} setblock commands, {len(figure)} figure blocks in "
          f"{len(DEPTH)} slabs at {', '.join(str(k * D0) for k in sorted(set(DEPTH.values())))} blocks")
    print(f"  RESOLVES from the arrival square: {agree:.1%} of "
          f"{w * h * SUPERSAMPLE ** 2} samples agree with the picture")
    print(f"  COMES APART {STEP_ASIDE} blocks aside: {intact:.0%} of the figure survives")
    print(f"  capture size {span[0]} x {span[1]} x {span[2]} "
          f"({'OK' if max(span) <= 48 else 'TOO BIG'} for the 48 cap)")
    print()
    print("  structure blocks, relative to the ARRIVAL SQUARE P:")
    print(f"    SAVE   corner at  P + ({box_lo[0] - 1}, {box_lo[1] + shift - 1}, {box_lo[2] - 1})")
    print(f"    CORNER corner at  P + ({box_hi[0] + 1}, {box_hi[1] + shift + 1}, {box_hi[2] + 1})")


if __name__ == "__main__":
    main()
