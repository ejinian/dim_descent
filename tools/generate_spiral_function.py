#!/usr/bin/env python3
"""Generate a .mcfunction that builds a descending spiral-stair tower.

    python3 tools/generate_spiral_function.py

A spiral is a few thousand individual blocks - far too many to paste as chat commands, and not
expressible as WorldEdit primitives. So it ships as a datapack function in the BUILDER WORLD (not in
the mod): stand on the centre block and run it, and every coordinate is relative to you.

The stair itself is a helicoid: for each cell in the tread annulus, the angle around the centre maps
to a height, so the surface climbs continuously instead of being cut into discrete steps. Repeating
that at +RISE_PER_TURN gives the overlapping flights of a real spiral staircase.
"""

import math
import os
import random

WORLD = "run/saves/DimDescentRoomBuilder"
PACK = f"{WORLD}/datapacks/dimdescent_build"
FUNC_DIR = f"{PACK}/data/build/function"

BRICK = "dimdescent:altar_stone_bricks"
CRACKED = "dimdescent:cracked_altar_stone_bricks"

OUTER_R = 8          # tower radius -> 17x17 footprint
TREAD_INNER = 2      # stair runs from this radius...
TREAD_OUTER = 7      # ...to this one
PILLAR_R = 1         # central column
HEIGHT = 40          # floor at y=0, ceiling at y=HEIGHT
RISE_PER_TURN = 16   # blocks climbed per full revolution - lower is steeper
CRACK_CHANCE = 0.20

random.seed(0xC0FFEE)


def brick():
    return CRACKED if random.random() < CRACK_CHANCE else BRICK


def main():
    os.makedirs(FUNC_DIR, exist_ok=True)
    with open(f"{PACK}/pack.mcmeta", "w") as f:
        f.write('{\n  "pack": {\n    "pack_format": 48,\n'
                '    "description": "dim_descent build helpers"\n  }\n}\n')

    lines = ["# Spiral stair tower. Stand on the centre block, at floor level, and run:",
             "#   /function build:spiral",
             "# Everything below is relative to the executor.", ""]
    placed = set()

    def put(x, y, z, block):
        if (x, y, z) in placed:
            return
        placed.add((x, y, z))
        lines.append(f"setblock ~{x} ~{y} ~{z} {block}")

    # Floor and ceiling discs
    for dx in range(-OUTER_R, OUTER_R + 1):
        for dz in range(-OUTER_R, OUTER_R + 1):
            if math.hypot(dx, dz) <= OUTER_R + 0.5:
                put(dx, 0, dz, brick())
                put(dx, HEIGHT, dz, brick())

    # Outer wall
    for y in range(1, HEIGHT):
        for dx in range(-OUTER_R, OUTER_R + 1):
            for dz in range(-OUTER_R, OUTER_R + 1):
                r = math.hypot(dx, dz)
                if OUTER_R - 0.5 < r <= OUTER_R + 0.5:
                    put(dx, y, dz, brick())

    # Central pillar
    for y in range(1, HEIGHT):
        for dx in range(-PILLAR_R, PILLAR_R + 1):
            for dz in range(-PILLAR_R, PILLAR_R + 1):
                if math.hypot(dx, dz) <= PILLAR_R + 0.4:
                    put(dx, y, dz, brick())

    # The helicoid: angle -> height, repeated once per turn.
    treads = 0
    for dx in range(-TREAD_OUTER, TREAD_OUTER + 1):
        for dz in range(-TREAD_OUTER, TREAD_OUTER + 1):
            r = math.hypot(dx, dz)
            if not (TREAD_INNER - 0.4 <= r <= TREAD_OUTER + 0.4):
                continue
            theta = math.atan2(dz, dx) % (2 * math.pi)
            base = theta / (2 * math.pi) * RISE_PER_TURN
            turn = 0
            while True:
                y = round(base + turn * RISE_PER_TURN)
                if y > HEIGHT - 2:
                    break
                if y >= 1:
                    put(dx, y, dz, brick())
                    treads += 1
                turn += 1

    with open(f"{FUNC_DIR}/spiral.mcfunction", "w") as f:
        f.write("\n".join(lines) + "\n")

    turns = (HEIGHT - 2) / RISE_PER_TURN
    print(f"wrote {FUNC_DIR}/spiral.mcfunction")
    print(f"  {len(lines) - 4} setblock commands, {treads} stair treads")
    print(f"  footprint {OUTER_R * 2 + 1}x{OUTER_R * 2 + 1}, height {HEIGHT + 1}, ~{turns:.1f} turns")
    print(f"  capture size: {OUTER_R * 2 + 1} x {HEIGHT + 1} x {OUTER_R * 2 + 1} (48 cap: "
          f"{'OK' if max(OUTER_R * 2 + 1, HEIGHT + 1) <= 48 else 'TOO BIG'})")


if __name__ == "__main__":
    main()
