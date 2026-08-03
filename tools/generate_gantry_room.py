#!/usr/bin/env python3
"""Generate a .mcfunction that builds the Gantry.

    python3 tools/generate_gantry_room.py

THE IDEA
The Causeway's trick was that a Nullstone floor renders perfectly flat, so it is indistinguishable
from a hole in the world, and a lit walkway one block above it reads as a bridge over nothing. This
is that room's sibling, and it asks the obvious next question: what if the player can see that there
are OTHER bridges?

Five identical walkways, stacked directly above and below each other at eight-block intervals in an
otherwise empty void. Yours is the middle one. The other four are the same span, the same width, the
same brick, running the same direction between the same two points - and there is no way to get to
any of them.

  THEY DECAY WITH DISTANCE.  The one immediately above and the one immediately below have holes in
      them. The outermost pair are barely there - a scatter of blocks still holding the line of a
      walkway that has mostly gone. Yours is the only intact one, which invites exactly the wrong
      conclusion about which way time runs here.
  THEY ARE HUNG.  Flaying Coils run between consecutive levels, so the stack reads as one suspended
      structure rather than five unrelated bridges that happen to line up. Some of the chains have
      parted and hang short, which is what makes the intact ones look load-bearing.
  THEY ARE THE ONLY THING TO LOOK AT.  Everything else is Nullstone - flat black, no shading, no
      depth cue of any kind - so the void gives the eye nothing and the stack is all there is.

The horror is not that the other bridges are dangerous. It is that they are identical, unreachable,
and there is no reason for them to exist.
"""

import os
import random
from collections import deque

WORLD = "run/saves/DimDescentRoomBuilder"
PACK = f"{WORLD}/datapacks/dimdescent_build"
FUNC_DIR = f"{PACK}/data/build/function"
NAME = "gantry"

BRICK = "dimdescent:altar_stone_bricks"
CRACKED = "dimdescent:cracked_altar_stone_bricks"
CHAIN = "dimdescent:flaying_coil"
VOID = "dimdescent:nullstone"
DARK_BED = "dimdescent:dream_bed"
PALE_BED = "dimdescent:pale_dream_bed"

SPAN = 18          # walkway runs -SPAN .. +SPAN
WALK_HALF = 1      # 3 wide
HALL_X = 6         # void either side of the walkway

# Ghost levels, as offsets from the player's own walkway, with the fraction of each that has gone.
# Level 0 must be 0.0 or the room is not crossable.
# Spacing and count are boxed in by the 48-block cap: five levels ten apart came out 57
# tall. Eight apart is the most that fits with room for the shell.
GHOSTS = ((0, 0.00), (8, 0.18), (-8, 0.22), (16, 0.55), (-16, 0.62))

CHAIN_SPACING = 6  # blocks between hanging chains along the span
CHAIN_X = 2        # just off the walkway edge, so they pass within arm's reach
CHAIN_BREAK = 0.35 # chance a chain has parted and hangs short

CRACK_CHANCE = 0.20
random.seed(20260813)


def brick():
    return CRACKED if random.random() < CRACK_CHANCE else BRICK


def main():
    levels = sorted(y for y, _ in GHOSTS)
    decay = dict(GHOSTS)
    if decay[0] != 0.0:
        raise AssertionError("level 0 is the player's walkway and must not decay.")

    blocks = {}

    def put(x, y, z, block):
        blocks[(x, y, z)] = block

    walkway = set()
    for y, gone in GHOSTS:
        for z in range(-SPAN, SPAN + 1):
            for x in range(-WALK_HALF, WALK_HALF + 1):
                if random.random() < gone:
                    continue
                put(x, y, z, brick())
                if y == 0:
                    walkway.add((x, z))

    # Chains between consecutive levels, so the stack hangs together instead of floating apart.
    chains = 0
    for lo, hi in zip(levels, levels[1:]):
        for z in range(-SPAN + 2, SPAN - 1, CHAIN_SPACING):
            for x in (-CHAIN_X, CHAIN_X):
                top = hi - 1
                bottom = lo + 1
                if random.random() < CHAIN_BREAK:
                    bottom = top - random.randint(2, max(3, (hi - lo) - 3))
                for y in range(bottom, top + 1):
                    put(x, y, z, f"{CHAIN}[axis=y]")
                chains += 1

    lo_y, hi_y = min(levels) - 4, max(levels) + 4
    inner = (HALL_X, hi_y, SPAN + 2)
    for x in range(-inner[0] - 2, inner[0] + 3):
        for y in range(lo_y - 2, inner[1] + 3):
            for z in range(-inner[2] - 2, inner[2] + 3):
                if not (-inner[0] <= x <= inner[0] and lo_y <= y <= inner[1]
                        and -inner[2] <= z <= inner[2]):
                    put(x, y, z, VOID)

    pale_foot, pale_head = (0, 1, SPAN - 3), (0, 1, SPAN - 2)
    dark_foot, dark_head = (0, 1, -(SPAN - 3)), (0, 1, -(SPAN - 2))

    # ---- checks ------------------------------------------------------------
    solid = set(blocks)

    def standable(p):
        return ((p[0], p[1] - 1, p[2]) in solid
                and all((p[0], p[1] + k, p[2]) not in solid for k in (0, 1)))

    for label, p in (("pale", pale_foot), ("dark", dark_foot)):
        if not standable(p):
            raise AssertionError(f"the {label} Nexus at {p} is not standing on anything.")

    reach, queue = {pale_foot}, deque([pale_foot])
    while queue:
        x, y, z = queue.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, y, z + dz)
            if abs(n[2]) <= SPAN and abs(n[0]) <= HALL_X and n not in reach and standable(n):
                reach.add(n)
                queue.append(n)
    if dark_foot not in reach:
        raise AssertionError("the dark Nexus is unreachable along the walkway.")

    # A chain in the walking corridor would be an obstacle rather than scenery.
    for x in range(-WALK_HALF, WALK_HALF + 1):
        for z in range(-SPAN, SPAN + 1):
            for y in (1, 2):
                if (x, y, z) in solid:
                    raise AssertionError(f"something is blocking the walkway at ({x},{y},{z}).")

    # Every ghost level must still READ as a walkway - decayed past about three quarters and it stops
    # being a bridge and becomes litter.
    for y, gone in GHOSTS:
        if y == 0:
            continue
        left = sum(1 for z in range(-SPAN, SPAN + 1)
                   for x in range(-WALK_HALF, WALK_HALF + 1) if (x, y, z) in solid)
        total = (2 * SPAN + 1) * (2 * WALK_HALF + 1)
        if left / total < 0.25:
            raise AssertionError(f"ghost level {y:+d} is only {left / total:.0%} intact - it no "
                                 f"longer reads as a walkway. Lower its decay.")

    lo3 = (-inner[0] - 2, lo_y - 2, -inner[2] - 2)
    hi3 = (inner[0] + 2, inner[1] + 2, inner[2] + 2)
    seen, queue = set(), deque()
    for x in range(lo3[0], hi3[0] + 1):
        for y in range(lo3[1], hi3[1] + 1):
            for z in range(lo3[2], hi3[2] + 1):
                if (x in (lo3[0], hi3[0]) or y == hi3[1] or z in (lo3[2], hi3[2])) \
                        and (x, y, z) not in solid:
                    seen.add((x, y, z))
                    queue.append((x, y, z))
    while queue:
        x, y, z = queue.popleft()
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + d[0], y + d[1], z + d[2])
            if all(lo3[i] <= n[i] <= hi3[i] for i in range(3)) and n not in seen and n not in solid:
                seen.add(n)
                queue.append(n)
    if pale_foot in seen:
        raise AssertionError("room is NOT sealed - outside air reaches the walkway.")

    # ---- emit --------------------------------------------------------------
    shift = -lo3[1]
    lines = ["# The Gantry. Stand at the CENTRE of the room, in CLEAR AIR, and run:",
             f"#   /function build:{NAME}",
             "# Generated by tools/generate_gantry_room.py - do not hand-edit.", ""]
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

    span = [hi3[i] - lo3[i] + 1 for i in range(3)]
    print(f"wrote {FUNC_DIR}/{NAME}.mcfunction")
    print(f"  {len(lines) - 4} setblock commands")
    print(f"  {len(GHOSTS)} walkways at y {', '.join(f'{y:+d}' for y in levels)}, "
          f"decay {', '.join(f'{decay[y]:.0%}' for y in levels)}")
    print(f"  {chains} chains between levels, {CHAIN_BREAK:.0%} of them parted")
    print(f"  walkway crossable end to end, {len(reach)} cells; nothing blocking it")
    print(f"  capture size {span[0]} x {span[1]} x {span[2]} "
          f"({'OK' if max(span) <= 48 else 'TOO BIG'} for the 48 cap)")
    print()
    print("  structure blocks, relative to the room CENTRE P (level 0 walkway height):")
    print(f"    SAVE   corner at  P + ({lo3[0] - 1}, -1, {lo3[2] - 1})")
    print(f"    CORNER corner at  P + ({hi3[0] + 1}, {span[1]}, {hi3[2] + 1})")


if __name__ == "__main__":
    main()
