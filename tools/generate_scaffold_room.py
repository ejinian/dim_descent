#!/usr/bin/env python3
"""Generate a .mcfunction that builds the Scaffold.

    python3 tools/generate_scaffold_room.py

THE IDEA
A brick armature for a building that was never built, filling the largest volume the structure format
can hold, and nothing else at all.

Not walls, not floors, not rooms - beams. A cubic wireframe on a six-block pitch running through the
whole interior, cut off on every side by black. There is no ground under it. You arrive on the top
grid, forty blocks up, and everything below you is frame and then nothing.

The name is the double one. It is a construction frame, and it is the other thing.

  BEAMS, NOT PLANES.  A cell is brick where at least TWO of its three coordinates land on the pitch
      grid, which gives the EDGES of the cubes. One-of-three would give their faces and the room
      would be a honeycomb of sealed boxes instead of an open frame. It comes out 7% solid, so the
      room is essentially all sightline.
  ONE PIECE.  Every brick in the room is face-connected to every other one. Beams meet at nodes and
      nodes are shared, so the whole 5341-block frame is a single object - checked by flood fill,
      not assumed. This is the room's only structural claim and it is the easiest thing in the world
      to break: anything decorative laid across the openings (diagonal braces, say) touches its
      neighbours at the CORNERS only and hangs in the air as its own component, and any vertical
      displacement of one region severs every beam crossing into it.
  NO LANDMARK.  The lattice is exactly invariant under one pitch of translation on all three axes.
      Asserted, because "the room offers nowhere to navigate by" is a property of the generator
      rather than of the eye, and a single stray brick would quietly hand back the one thing the
      room is built to withhold without looking wrong in a screenshot.
  IT DOES NOT END, IT IS CUT.  The pitch does not divide the half-width, so the outermost beams run
      three blocks past their last node and stop dead against the shell. Beams that stop at a node
      look finished; beams sawn off mid-span look like the room is a fragment of something larger.

THE SHIPPED ROOM IS NOT EXACTLY THIS
scaffold.nbt was finished by hand after this ran. The beds below are a starting point - one pale, one
dark, at opposite corners of the top grid - and in the captured room all four were re-placed: the pale
Nexus at the CENTRE of the top grid, and THREE dark ones down on the y=33, y=21 and y=3 grids, so the
only way to two of them is to drop, level by level, onto beams. Re-running this and re-capturing over
the top would throw that away. Check the bed count with verify_room_nbt.py before overwriting.

WHY THE LATTICE IS CLAMPED IN Y
The vertical posts are solid wherever x and z are both on-grid - which is EVERY y, including the
three above the top grid the player has to stand in. Left alone, a post rises out of every single
intersection of the walkway and the top grid becomes impassable. So the frame is clamped to the band
between its lowest and highest node levels, and the three blocks of clearance above it are asserted
rather than eyeballed.
"""

import os
from collections import deque

WORLD = "run/saves/DimDescentRoomBuilder"
PACK = f"{WORLD}/datapacks/dimdescent_build"
FUNC_DIR = f"{PACK}/data/build/function"
NAME = "scaffold"

BRICK = "minecraft:bricks"
VOID = "dimdescent:nullstone"
DARK_BED = "dimdescent:dream_bed"
PALE_BED = "dimdescent:pale_dream_bed"

HALF = 21          # interior -21..21 -> 43 across
TOP = 42           # interior 0..42
SKIN = HALF + 2    # 2 layers of shell -> 47 capture, the format maximum

PITCH = 6          # cell size: 1 beam + 5 open
LAT_LO = 3         # lowest node level; also the y-phase of the whole lattice
LAT_HI = 39        # highest node level. TOP - LAT_HI is the headroom on the walkway.
HEADROOM = 3

BED_X = 18         # both beds sit on node columns, at opposite corners of the top grid
BED_Z = 18


def on(v):
    return v % PITCH == 0


def on_y(y):
    return (y - LAT_LO) % PITCH == 0


def lattice(x, y, z):
    """Brick where at least two of the three coordinates land on the grid - the EDGES of the cubes.
    Clamped to the node band so no post protrudes into the walkway on top."""
    if not (LAT_LO <= y <= LAT_HI):
        return False
    return (on(x) + on_y(y) + on(z)) >= 2


def main():
    blocks = {}

    # ---- shell -------------------------------------------------------------
    for x in range(-SKIN, SKIN + 1):
        for y in range(-2, TOP + 3):
            for z in range(-SKIN, SKIN + 1):
                if abs(x) > HALF or abs(z) > HALF or y in (-2, -1, TOP + 1, TOP + 2):
                    blocks[(x, y, z)] = VOID

    # ---- the frame ---------------------------------------------------------
    frame = set()
    for x in range(-HALF, HALF + 1):
        for y in range(LAT_LO, LAT_HI + 1):
            for z in range(-HALF, HALF + 1):
                if lattice(x, y, z):
                    frame.add((x, y, z))
    for c in frame:
        blocks[c] = BRICK

    # ---- checks ------------------------------------------------------------
    # One piece. The whole point of the complaint that produced this version, and the one property
    # here that decoration destroys silently: a diagonal laid across an opening touches its own
    # neighbours at the corners only, so it reads as part of the frame and is not attached to it.
    start = next(iter(frame))
    seen, queue = {start}, deque([start])
    while queue:
        x, y, z = queue.popleft()
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + d[0], y + d[1], z + d[2])
            if n in frame and n not in seen:
                seen.add(n)
                queue.append(n)
    if len(seen) != len(frame):
        raise AssertionError(f"the frame is in more than one piece - {len(frame) - len(seen)} of "
                             f"{len(frame)} blocks are not attached to the rest.")

    for x in range(-HALF, HALF + 1):
        for y in range(LAT_LO, LAT_HI + 1):
            for z in range(-HALF, HALF + 1):
                if y + PITCH <= LAT_HI and lattice(x, y, z) != lattice(x, y + PITCH, z):
                    raise AssertionError(f"lattice is not periodic in y at ({x},{y},{z}).")
                if x + PITCH <= HALF and lattice(x, y, z) != lattice(x + PITCH, y, z):
                    raise AssertionError(f"lattice is not periodic in x at ({x},{y},{z}).")
                if z + PITCH <= HALF and lattice(x, y, z) != lattice(x, y, z + PITCH):
                    raise AssertionError(f"lattice is not periodic in z at ({x},{y},{z}).")

    if TOP - LAT_HI < HEADROOM:
        raise AssertionError(f"only {TOP - LAT_HI} blocks above the top grid - a player standing on "
                             f"the walkway needs {HEADROOM}.")
    for c in frame:
        if c[1] > LAT_HI:
            raise AssertionError(f"a post protrudes above the top grid at {c} - it would block the "
                                 f"walkway at every intersection.")

    solid = set(blocks)
    walk_y = LAT_HI + 1
    pale_foot, pale_head = (-BED_X, walk_y, -BED_Z), (-BED_X, walk_y, -BED_Z + 1)
    dark_foot, dark_head = (BED_X, walk_y, BED_Z), (BED_X, walk_y, BED_Z - 1)

    def standable(p):
        return ((p[0], p[1] - 1, p[2]) in solid
                and p not in solid and (p[0], p[1] + 1, p[2]) not in solid)

    for label, foot, head in (("pale", pale_foot, pale_head), ("dark", dark_foot, dark_head)):
        for cell in (foot, head):
            if not standable(cell):
                raise AssertionError(f"the {label} Nexus cell {cell} has no beam under it.")

    reach, queue = {pale_foot}, deque([pale_foot])
    while queue:
        x, y, z = queue.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, y, z + dz)
            if abs(n[0]) <= HALF and abs(n[2]) <= HALF and n not in reach and standable(n):
                reach.add(n)
                queue.append(n)
    if dark_foot not in reach:
        raise AssertionError("the dark Nexus is not reachable along the top grid.")

    filled = len(frame)
    volume = (2 * HALF + 1) ** 2 * (2 * HALF + 1)
    if not 0.03 < filled / volume < 0.15:
        raise AssertionError(f"the frame is {filled / volume:.0%} solid - it has stopped being "
                             f"see-through. Raise PITCH.")

    lo, hi = (-SKIN, -2, -SKIN), (SKIN, TOP + 2, SKIN)
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
        raise AssertionError("room is NOT sealed - outside air reaches the walkway.")

    # ---- emit --------------------------------------------------------------
    shift = 2
    out = ["# The Scaffold. Stand at the CENTRE of the room, in CLEAR AIR, and run:",
           f"#   /function build:{NAME}",
           "# Generated by tools/generate_scaffold_room.py - do not hand-edit.", ""]
    for (x, y, z), block in blocks.items():
        out.append(f"setblock ~{x} ~{y + shift} ~{z} {block}")
    for block, foot, head, facing in ((PALE_BED, pale_foot, pale_head, "south"),
                                      (DARK_BED, dark_foot, dark_head, "north")):
        out.append(f"setblock ~{foot[0]} ~{foot[1] + shift} ~{foot[2]} "
                   f"{block}[facing={facing},part=foot]")
        out.append(f"setblock ~{head[0]} ~{head[1] + shift} ~{head[2]} "
                   f"{block}[facing={facing},part=head]")

    os.makedirs(FUNC_DIR, exist_ok=True)
    with open(f"{PACK}/pack.mcmeta", "w") as f:
        f.write('{\n  "pack": {\n    "pack_format": 48,\n'
                '    "description": "dim_descent build helpers"\n  }\n}\n')
    with open(f"{FUNC_DIR}/{NAME}.mcfunction", "w") as f:
        f.write("\n".join(out) + "\n")

    span = 2 * SKIN + 1
    levels = [y for y in range(LAT_LO, LAT_HI + 1) if on_y(y)]
    print(f"wrote {FUNC_DIR}/{NAME}.mcfunction")
    print(f"  {len(out) - 4} setblock commands")
    print(f"  {len(frame)} brick blocks, {filled / volume:.0%} of the interior - nothing else in it")
    print(f"  ONE connected piece, verified by flood fill")
    print(f"  {PITCH}-block pitch; grids at y {levels}; periodic under one pitch on all three axes")
    print(f"  beds on the top grid at y={walk_y}, opposite corners, "
          f"{len(reach)} walkway cells reachable between them")
    print(f"  {TOP - LAT_HI} blocks of clearance above the walkway; no floor - the frame hangs")
    print(f"  capture size {span} x {span} x {span} "
          f"({'OK' if span <= 48 else 'TOO BIG'} for the 48 cap)")
    print()
    print("  structure blocks, relative to the room CENTRE P:")
    print(f"    SAVE   corner at  P + ({-SKIN - 1}, -1, {-SKIN - 1})")
    print(f"    CORNER corner at  P + ({SKIN + 1}, {span}, {SKIN + 1})")


if __name__ == "__main__":
    main()
