#!/usr/bin/env python3
"""Generate a .mcfunction that builds the Oubliette.

    python3 tools/generate_oubliette_room.py

THE IDEA
An oubliette is a cell you are put into and forgotten - from `oublier`, to forget. This is a small
room built entirely out of that word.

Three square rings, nested. Between them, corridors two blocks wide. At the dead centre, a 3x3 cell
with the dark Nexus in it. Two things make it work:

  THE CEILING FUNNELS DOWN.  Height is a function of Chebyshev distance from the centre, so the
  ceiling drops as you move inward - six blocks of headroom at the outer wall, two at the centre.
  It steps down mid-corridor rather than at the ring walls, so there is never a doorway to brace
  for; the room just quietly closes on you. By the time you reach the Nexus you are in a space you
  exactly fit.

  EVERY OBVIOUS WAY IS BRICKED UP.  Each ring has a doorway on all four sides. The ring walls are
  two blocks thick, so a doorway can be cut through the outer layer only, leaving a one-block-deep
  recess with a brick face at the back of it. Three of the four are like that. The one that goes
  through is on a different side each ring, so the route switchbacks: enter at the south, walk half
  the ring to the north door, then half the next ring back to the south door.

Nothing is hidden and nothing is a puzzle. You can see every exit from the moment you arrive, and
you still have to walk the whole thing.

Palette is exactly what was asked for: altar stone bricks and their cracked variant for everything
you can see, the two Nexus beds, and a Nullstone skin around the outside so the build reads as void
from beyond it. Nullstone also fills the dead space above the funnelled ceiling, so breaking through
it finds the same nothing that surrounds the room.
"""

import math
import os
import random
from collections import deque

WORLD = "run/saves/DimDescentRoomBuilder"
PACK = f"{WORLD}/datapacks/dimdescent_build"
FUNC_DIR = f"{PACK}/data/build/function"
NAME = "oubliette"

BRICK = "dimdescent:altar_stone_bricks"
CRACKED = "dimdescent:cracked_altar_stone_bricks"
VOID = "dimdescent:nullstone"
AIR = "minecraft:air"
DARK_BED = "dimdescent:dream_bed"
PALE_BED = "dimdescent:pale_dream_bed"

# ---------------------------------------------------------------------------
# SHAPE. Everything is Chebyshev distance d = max(|x|, |z|), which is what makes the rings square.
# ---------------------------------------------------------------------------
SKIN = 11             # Nullstone shell -> 23x23 footprint
WALL = 10             # brick outer wall of the room
RING_A = (7, 6)       # (outer layer, inner layer) - two thick, so a door can be a blind recess
RING_B = (3, 2)
#   corridors fall out of the above: d 8..9 outer, d 4..5 middle, d 0..1 the cell

BASE_H = 2            # headroom at the centre. Two blocks: you fit, and that is all.
SLOPE = 0.4           # extra headroom per block of distance outward

DOOR_H = 2            # doorways are two tall, so they read as doors and not as missing wall
OPEN_SIDE = {RING_A: "north", RING_B: "south"}   # the one door per ring that goes through

BED_Z = 9             # pale bed in the outer corridor, opposite ring A's open door
MIN_PATH = 40         # if the walk is shorter than this the labyrinth is not doing its job

CRACK_CHANCE = 0.22
random.seed(20260802)   # plain decimal. Cute hex spellings keep turning out not to be hex.


def brick():
    return CRACKED if random.random() < CRACK_CHANCE else BRICK


def headroom(d):
    return BASE_H + math.floor(d * SLOPE + 0.5)


def door_cells(ring, side):
    """The two cells - outer layer then inner layer - a doorway passes through on one side."""
    return [{"north": (0, -r), "south": (0, r), "east": (r, 0), "west": (-r, 0)}[side]
            for r in ring]


def main():
    lid = headroom(WALL - 1) + 2
    cells = [(x, z) for x in range(-SKIN, SKIN + 1) for z in range(-SKIN, SKIN + 1)]
    dist = {c: max(abs(c[0]), abs(c[1])) for c in cells}
    ring_layers = set(RING_A) | set(RING_B)

    blocks = {}

    def put(x, y, z, block):
        blocks[(x, y, z)] = block

    for c in cells:
        x, z = c
        d = dist[c]
        put(x, -1, z, VOID)                       # Nullstone underside
        put(x, lid, z, VOID)                      # Nullstone lid
        if d == SKIN:
            for y in range(0, lid):
                put(x, y, z, VOID)                # Nullstone walls
            continue
        if d == WALL:
            for y in range(0, lid):
                put(x, y, z, brick())             # the room's own outer wall
            continue

        h = headroom(d)
        put(x, 0, z, brick())                     # floor
        for y in range(1, h + 1):                 # ring walls are solid here, corridors are air
            put(x, y, z, brick() if d in ring_layers else AIR)
        put(x, h + 1, z, brick())                 # the funnelled ceiling
        for y in range(h + 2, lid):               # dead space above it, filled with void
            put(x, y, z, VOID)

    # Doorways. Cut the outer layer of every ring on all four sides; cut the inner layer only on
    # the side that actually goes through. The other three become one-block-deep bricked-up recesses.
    for ring in (RING_A, RING_B):
        for side in ("north", "south", "east", "west"):
            outer, inner = door_cells(ring, side)
            for cell in ((outer, inner) if side == OPEN_SIDE[ring] else (outer,)):
                for y in range(1, DOOR_H + 1):
                    put(cell[0], y, cell[1], AIR)

    # ---- checks ------------------------------------------------------------
    solid = {p for p, b in blocks.items() if b != AIR}

    def walkable(x, z):
        return ((x, 0, z) in solid
                and all((x, y, z) not in solid for y in range(1, DOOR_H + 1)))

    for c in cells:
        if dist[c] <= WALL - 1 and headroom(dist[c]) < DOOR_H:
            raise AssertionError(
                f"headroom at d={dist[c]} is {headroom(dist[c])}, under the {DOOR_H} a player needs. "
                f"Raise BASE_H.")

    start, goal = (0, BED_Z), (0, 0)
    for label, (bx, bz) in (("pale", start), ("dark", goal)):
        for z in (bz, bz - 1):
            if not walkable(bx, z):
                raise AssertionError(f"{label} bed cell ({bx},{z}) is not standable.")

    steps = {start: 0}
    queue = deque([start])
    while queue:
        x, z = queue.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, z + dz)
            if abs(n[0]) <= WALL and abs(n[1]) <= WALL and n not in steps and walkable(*n):
                steps[n] = steps[(x, z)] + 1
                queue.append(n)
    if goal not in steps:
        raise AssertionError("the dark Nexus is unreachable - no open door chain to the centre.")
    if steps[goal] < MIN_PATH:
        raise AssertionError(
            f"the walk from entrance to centre is only {steps[goal]} blocks, under MIN_PATH "
            f"{MIN_PATH} - the open doors are probably not on opposite sides.")

    lo = (-SKIN - 1, -2, -SKIN - 1)
    hi = (SKIN + 1, lid + 1, SKIN + 1)
    seen, queue = set(), deque()
    for x in range(lo[0], hi[0] + 1):
        for y in range(lo[1], hi[1] + 1):
            for z in range(lo[2], hi[2] + 1):
                if (x in (lo[0], hi[0]) or y in (lo[1], hi[1]) or z in (lo[2], hi[2])) \
                        and (x, y, z) not in solid:
                    seen.add((x, y, z))
                    queue.append((x, y, z))
    while queue:
        x, y, z = queue.popleft()
        for d3 in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + d3[0], y + d3[1], z + d3[2])
            if all(lo[i] <= n[i] <= hi[i] for i in range(3)) and n not in seen and n not in solid:
                seen.add(n)
                queue.append(n)
    leaks = [p for p, b in blocks.items() if b == AIR and p in seen]
    if leaks:
        raise AssertionError(
            f"room is NOT sealed - outside air reaches {len(leaks)} interior cells, first {leaks[0]}.")

    # ---- emit --------------------------------------------------------------
    shift = 1
    lines = [f"# The Oubliette. Stand at the CENTRE of the room, on the ground, and run:",
             f"#   /function build:{NAME}",
             f"# Generated by tools/generate_oubliette_room.py - do not hand-edit.", ""]
    for (x, y, z), block in blocks.items():
        lines.append(f"setblock ~{x} ~{y + shift} ~{z} {block}")

    def bed(block, fz, facing):
        hz = fz - 1 if facing == "north" else fz + 1
        lines.append(f"setblock ~0 ~{1 + shift} ~{fz} {block}[facing={facing},part=foot]")
        lines.append(f"setblock ~0 ~{1 + shift} ~{hz} {block}[facing={facing},part=head]")

    bed(PALE_BED, BED_Z, "north")
    bed(DARK_BED, 0, "north")

    os.makedirs(FUNC_DIR, exist_ok=True)
    with open(f"{PACK}/pack.mcmeta", "w") as f:
        f.write('{\n  "pack": {\n    "pack_format": 48,\n'
                '    "description": "dim_descent build helpers"\n  }\n}\n')
    with open(f"{FUNC_DIR}/{NAME}.mcfunction", "w") as f:
        f.write("\n".join(lines) + "\n")

    span_xz, span_y = 2 * SKIN + 1, lid + 2
    print(f"wrote {FUNC_DIR}/{NAME}.mcfunction")
    print(f"  {len(lines) - 4} setblock commands")
    print(f"  headroom {headroom(0)} at the centre -> {headroom(WALL - 1)} at the wall")
    print(f"  walk from entrance to the dark Nexus: {steps[goal]} blocks")
    print(f"  capture size {span_xz} x {span_y} x {span_xz} "
          f"({'OK' if max(span_xz, span_y) <= 48 else 'TOO BIG'} for the 48 cap)")
    print(f"  headroom, reachability, path-length and seal checks all passed")
    print()
    print("  structure blocks, standing at the run position P:")
    print(f"    SAVE   corner at  P + ({-SKIN - 1}, -1, {-SKIN - 1})")
    print(f"    CORNER corner at  P + ({SKIN + 1}, {span_y}, {SKIN + 1})")


if __name__ == "__main__":
    main()
