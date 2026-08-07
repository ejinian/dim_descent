#!/usr/bin/env python3
"""Generate a .mcfunction that builds the Fault.

    python3 tools/generate_fault_room.py

THE IDEA
The Far Lands, rebuilt out of blocks.

In Beta 1.7.3 the terrain generator broke at x or z = 12,550,821. The noise function's input
coordinates got large enough that floating point could no longer hold them apart, the density field
lost almost all of its dependence on y, and terrain stopped being landscape and became a wall - one
enormous striated face running to the horizon, shot through with horizontal shelves, repeating the
same formation over and over because the degenerate noise had gone periodic. Push out to 2**25 and
the RENDERER goes as well: block positions stop landing on representable coordinates, so the world
snaps to a coarser and coarser grid and the textures visibly come apart.

This room is a canyon in that terrain. Three things carry it, and they are the three things that
actually go wrong out there:

  THE WALLS ARE EXTRUDED AND PERIODIC.  The density field has no z term worth the name, so every
      wall runs the full length of the room without changing - the iconic Far Lands view, a canyon
      between two vast striated faces stretching into black. Across the room it repeats EXACTLY
      every PERIOD_X blocks: the canyon you are in has an identical twin either side of it, and the
      horizontal shelf tunnels let you see through into it. Asserted, not hoped for.
  THE GRID DEGRADES AS YOU GO DEEPER.  The room is divided into four zones along its length, and
      each one samples the field on a coarser lattice than the last: 1, then 2, then 4, then 8. At
      the near end the terrain is built out of blocks. At the far end it is built out of
      eight-block lumps, and the materials come in eight-block lumps too, because a precision loss
      does not politely restrict itself to geometry. The dark Nexus sits at the far end, where the
      world has stopped resolving. Walking deeper IS the mantissa running out.
  THE MATERIALS ARE WRONG PER BLOCK.  Every solid cell picks from a palette of near-identical greys
      by hash, so no surface ever holds one material for more than a block or two. Vanilla stone
      sits against altar brick sits against deepslate laid on its side. Scattered through it are
      single blocks of Nullstone and Allstone - flat black and flat white, unshaded, no grain - which
      in the middle of a stone wall read as exactly what they look like: a texture lookup that
      failed. Where it fails badly enough it fails in patches, and a patch is a 1x1 checkerboard of
      the two, world-grid aligned, which is the oldest missing-texture tell there is.

WHY THE PERIODICITY IS THE ASSERTION
Everything else here could be eyeballed. Periodicity cannot: the room is only 2.5 periods wide, so
if the harmonics or the lattice quantisation broke exact repetition it would look merely noisy and
nobody would notice the room had lost its whole point. Both the sine sum and the hash jitter are
built to be exactly periodic in x, and quantisation preserves it only because every zone step
divides PERIOD_X. Change PERIOD_X to something not divisible by 8 and the check fires.

TRAVERSAL IS EARNED, NOT CARVED
The canyons run along the travel axis so end-to-end walking usually falls out for free, but a q=8
lump can plug one. Rather than trench a corridor through the middle - which would flatten the
terrain the room exists for - the generator runs a Dijkstra over standing positions where open
ground is free and clearing a block costs one, and removes only the cheapest set that connects the
two Nexus beds. Same technique the Gyroid needed for the same reason.
"""

import math
import os
from collections import deque
from heapq import heappush, heappop

WORLD = "run/saves/DimDescentRoomBuilder"
PACK = f"{WORLD}/datapacks/dimdescent_build"
FUNC_DIR = f"{PACK}/data/build/function"
NAME = "fault"

VOID = "dimdescent:nullstone"
GLARE = "dimdescent:allstone"
DARK_BED = "dimdescent:dream_bed"
PALE_BED = "dimdescent:pale_dream_bed"

HALF_X = 20        # interior -20..20, 41 wide -> 2.5 periods visible
LENGTH = 18        # interior -18..18, 37 long
TOP = 26           # interior 0..26; y=0 is the base floor, so walking space is y>=1

TAU = 2.0 * math.pi

# The bug signature. MUST be divisible by every entry in QUANTS or the repeat stops being exact.
PERIOD_X = 16

# The x harmonics of the density field. Odd multiples only, so the period is exactly PERIOD_X.
HARMONICS = ((1, 1.00, 0.0), (3, 0.62, 1.7), (5, 0.41, 4.1), (7, 0.27, 2.3))

SHELF = ((7.0, 0.46, 0.9), (11.0, 0.30, 2.6))   # (wavelength in y, amplitude, phase) - the tunnels
DRIFT_PERIOD, DRIFT_AMP = 89.0, 0.34            # far longer than the room: walls warp, never repeat
JITTER = 0.58                                   # per-lattice-cell roughness; smooth sine = wave, not wall
THRESHOLD = 0.36

# Lattice step per zone, near end first. The room degrades from left to right along -z.
QUANTS = (1, 2, 4, 8)
ZONE_EDGES = (9, 0, -9)     # z > 9 -> q=1; > 0 -> q=2; > -9 -> q=4; else q=8

# Weighted material palette. Everything is a grey or near-grey stone, so the mismatch reads as one
# surface failing rather than as a mosaic somebody laid deliberately.
PALETTE = (
    ("minecraft:stone", 10),
    ("minecraft:cobblestone", 8),
    ("minecraft:andesite", 8),
    ("minecraft:polished_andesite", 5),
    ("minecraft:stone_bricks", 6),
    ("minecraft:cracked_stone_bricks", 6),
    ("minecraft:tuff", 6),
    ("minecraft:cobbled_deepslate", 6),
    ("minecraft:smooth_stone", 4),
    ("dimdescent:altar_stone", 7),
    ("dimdescent:carved_altar_stone", 3),
    ("dimdescent:altar_stone_bricks", 6),
    ("dimdescent:cracked_altar_stone_bricks", 5),
    (VOID, 4),
    (GLARE, 4),
)
# Pillar blocks, placed on a hashed axis. A deepslate lying on its side in a wall is not a texture
# variant, it is a block that has been rotated by something that was not paying attention.
PILLARS = (("minecraft:deepslate", 6), ("minecraft:basalt", 5), ("minecraft:polished_basalt", 3))

PATCHES = 22        # missing-texture checkerboard blooms
PATCH_R = (2.5, 5.0)

DEBRIS = 70         # floating lumps in the upper air, the other Far Lands tell
DEBRIS_MIN_Y = 12

BED_INSET = 3
ALCOVE = (2, 3, 2)  # half-extent in x, height, half-extent in z of the cleared arrival pockets
MAX_BREACH = 90     # if the cheapest crossing costs more than this, the terrain is too dense


def hash01(*args):
    """Deterministic [0,1) from integers. Used instead of random() so a value can be re-derived
    from a coordinate alone, which is what makes the periodicity check possible."""
    v = 2166136261
    for a in args:
        v ^= (int(a) + 0x9E3779B9) & 0xFFFFFFFF
        v = (v * 16777619) & 0xFFFFFFFF
        v ^= v >> 15
    return v / 4294967296.0


def quant(z):
    for edge, q in zip(ZONE_EDGES, QUANTS):
        if z > edge:
            return q
    return QUANTS[-1]


def density(qx, qy, qz):
    # x is reduced mod PERIOD_X BEFORE the sine, not left to the sine's own periodicity. Real
    # arithmetic does not care; floating point does - sin(t) and sin(t + 2*pi*k) differ in the last
    # bits, and a cell sitting within that of THRESHOLD would flip and break the repeat. Reducing
    # first makes x and x+PERIOD_X bit-identical.
    ax = TAU * (qx % PERIOD_X) / PERIOD_X
    d = sum(a * math.sin(n * ax + p) for n, a, p in HARMONICS)
    for wavelength, amp, phase in SHELF:
        d += amp * math.sin(TAU * qy / wavelength + phase)
    d += DRIFT_AMP * math.sin(TAU * qz / DRIFT_PERIOD)
    # Periodic in x by construction: the lattice x is reduced mod PERIOD_X before hashing, and
    # PERIOD_X is a multiple of every quantisation step, so x and x+PERIOD_X share a lattice cell.
    d += JITTER * (hash01(qx % PERIOD_X, qy, qz, 7717) * 2.0 - 1.0)
    return d


def solid_at(x, y, z):
    q = quant(z)
    return density((x // q) * q, (y // q) * q, (z // q) * q) > THRESHOLD


def material(x, y, z):
    """Per-block material, quantised on the same lattice as the terrain."""
    q = quant(z)
    lx, ly, lz = (x // q) * q, (y // q) * q, (z // q) * q
    r = hash01(lx, ly, lz, 4093)
    total = sum(w for _, w in PALETTE) + sum(w for _, w in PILLARS)
    pick = r * total
    for name, w in PALETTE:
        pick -= w
        if pick < 0:
            return name
    for name, w in PILLARS:
        pick -= w
        if pick < 0:
            axis = "xyz"[int(hash01(lx, ly, lz, 8191) * 3) % 3]
            return f"{name}[axis={axis}]"
    return "minecraft:stone"


def main():
    skin_x, skin_z = HALF_X + 2, LENGTH + 2
    interior = [(x, z) for x in range(-HALF_X, HALF_X + 1) for z in range(-LENGTH, LENGTH + 1)]

    # ---- terrain -----------------------------------------------------------
    terrain = set()
    for x, z in interior:
        terrain.add((x, 0, z))                       # base floor: something is always underfoot
        for y in range(1, TOP):
            if solid_at(x, y, z):
                terrain.add((x, y, z))

    # The repeat is the whole point of the room, so it is checked before anything else touches the
    # cells. Only the pure terrain field is periodic - the dither and the patches deliberately are
    # not, because out there the SHAPE repeats and the mess on top of it does not.
    for x in range(-HALF_X, HALF_X + 1 - PERIOD_X):
        for z in range(-LENGTH, LENGTH + 1):
            for y in range(1, TOP):
                if ((x, y, z) in terrain) != ((x + PERIOD_X, y, z) in terrain):
                    raise AssertionError(
                        f"terrain is not periodic in x at ({x},{y},{z}) - the walls no longer "
                        f"repeat, which is the one thing the room is about. PERIOD_X={PERIOD_X} "
                        f"must be a multiple of every entry in QUANTS={QUANTS}.")

    # Every zone must actually be built on its own lattice, or the degradation is decorative.
    for q, lo, hi in zip(QUANTS, (ZONE_EDGES[0] + 1, ZONE_EDGES[1] + 1, ZONE_EDGES[2] + 1, -LENGTH),
                         (LENGTH, ZONE_EDGES[0], ZONE_EDGES[1], ZONE_EDGES[2])):
        for z in range(lo, hi + 1):
            for x in range(-HALF_X, HALF_X + 1):
                for y in range(1, TOP):
                    if (x // q) * q == x and (y // q) * q == y:
                        continue
                    lx, ly = (x // q) * q, (y // q) * q
                    if ly < 1 or lx < -HALF_X:
                        continue
                    if ((x, y, z) in terrain) != ((lx, ly, z) in terrain):
                        raise AssertionError(
                            f"zone q={q} is not lump-quantised at ({x},{y},{z}) - the grid does "
                            f"not actually coarsen here.")

    # ---- floating debris ---------------------------------------------------
    debris = set()
    placed = 0
    for i in range(DEBRIS * 6):
        if placed >= DEBRIS:
            break
        x = int(hash01(i, 11) * (2 * HALF_X + 1)) - HALF_X
        y = DEBRIS_MIN_Y + int(hash01(i, 22) * (TOP - DEBRIS_MIN_Y - 2))
        z = int(hash01(i, 33) * (2 * LENGTH + 1)) - LENGTH
        lump = [(x + dx, y + dy, z + dz)
                for dx in range(int(hash01(i, 44) * 3) + 1)
                for dy in range(int(hash01(i, 55) * 2) + 1)
                for dz in range(int(hash01(i, 66) * 3) + 1)]
        lump = [c for c in lump if abs(c[0]) <= HALF_X and abs(c[2]) <= LENGTH and 1 <= c[1] < TOP]
        if not lump or any(c in terrain for c in lump):
            continue
        # Only count it as floating if nothing under it reaches the ground.
        if any((c[0], c[1] - 1, c[2]) in terrain for c in lump):
            continue
        debris.update(lump)
        placed += 1

    solid = terrain | debris

    # ---- arrival pockets ---------------------------------------------------
    pale_foot = (0, 1, LENGTH - BED_INSET)
    pale_head = (0, 1, LENGTH - BED_INSET + 1)
    dark_foot = (0, 1, -(LENGTH - BED_INSET))
    dark_head = (0, 1, -(LENGTH - BED_INSET) - 1)

    for cx, _, cz in (pale_foot, dark_foot):
        for dx in range(-ALCOVE[0], ALCOVE[0] + 1):
            for dy in range(1, ALCOVE[1] + 1):
                for dz in range(-ALCOVE[2], ALCOVE[2] + 1):
                    solid.discard((cx + dx, dy, cz + dz))
        for dx in range(-ALCOVE[0], ALCOVE[0] + 1):
            for dz in range(-ALCOVE[2], ALCOVE[2] + 1):
                solid.add((cx + dx, 0, cz + dz))

    # ---- traversal ---------------------------------------------------------
    def inside(p):
        return abs(p[0]) <= HALF_X and abs(p[2]) <= LENGTH and 1 <= p[1] <= TOP - 2

    def settle(p, blocked):
        x, y, z = p
        while y > 1 and (x, y - 1, z) not in blocked:
            y -= 1
        return (x, y, z)

    def standable(p, blocked):
        return ((p[0], p[1] - 1, p[2]) in blocked
                and p not in blocked and (p[0], p[1] + 1, p[2]) not in blocked)

    # Dijkstra over standing positions: open ground free, each block that has to come out costs 1.
    # A single trench through the middle would connect the room in one line and destroy the terrain
    # this room is made of, so the cost function is what keeps the damage honest.
    best = {pale_foot: 0}
    prev = {pale_foot: (None, ())}
    heap = [(0, pale_foot)]
    while heap:
        cost, p = heappop(heap)
        if cost > best.get(p, 1 << 30):
            continue
        if p == dark_foot:
            break
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for dy in (1, 0):
                c = (p[0] + dx, p[1] + dy, p[2] + dz)
                if not inside(c):
                    continue
                clears = tuple(n for n in (c, (c[0], c[1] + 1, c[2])) if n in solid)
                # `clears` only ever holds the two cells the body occupies, never the one under
                # them, so what the player lands on is unaffected and `solid` can be used as is.
                landed = settle(c, solid)
                if not inside(landed):
                    continue
                nc = cost + len(clears)
                if nc < best.get(landed, 1 << 30):
                    best[landed] = nc
                    prev[landed] = (p, clears)
                    heappush(heap, (nc, landed))

    if dark_foot not in best:
        raise AssertionError("no route to the dark Nexus at any price - the terrain has sealed the "
                             "room. Lower THRESHOLD or JITTER.")
    if best[dark_foot] > MAX_BREACH:
        raise AssertionError(f"the cheapest crossing costs {best[dark_foot]} blocks, over the "
                             f"{MAX_BREACH} budget - the canyons no longer run through. "
                             f"Lower THRESHOLD.")

    breach = set()
    node = dark_foot
    while node is not None:
        parent, clears = prev[node]
        breach.update(clears)
        node = parent
    solid -= breach

    # ---- checks ------------------------------------------------------------
    for label, p in (("pale", pale_foot), ("dark", dark_foot)):
        if not standable(p, solid):
            raise AssertionError(f"the {label} Nexus at {p} is not standing in clear air.")
    for label, head in (("pale", pale_head), ("dark", dark_head)):
        if head in solid or (head[0], head[1] + 1, head[2]) in solid:
            raise AssertionError(f"the {label} Nexus head at {head} is buried.")

    reach, queue = {pale_foot}, deque([pale_foot])
    while queue:
        p = queue.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for dy in (1, 0, -1):
                c = (p[0] + dx, p[1] + dy, p[2] + dz)
                if not inside(c) or c in reach:
                    continue
                if standable(c, solid):
                    reach.add(c)
                    queue.append(c)
                    break
    if dark_foot not in reach:
        raise AssertionError("the dark Nexus is not reachable on foot after breaching.")

    filled = sum(1 for c in solid if 1 <= c[1] < TOP)
    volume = (2 * HALF_X + 1) * (TOP - 1) * (2 * LENGTH + 1)
    if not 0.20 < filled / volume < 0.55:
        raise AssertionError(f"the room is {filled / volume:.0%} solid - under 20% it is an empty "
                             f"box, over 55% it is a mine. Retune THRESHOLD.")

    # ---- materials ---------------------------------------------------------
    blocks = {}
    for x in range(-skin_x, skin_x + 1):
        for z in range(-skin_z, skin_z + 1):
            for y in range(-2, TOP + 3):
                if abs(x) > HALF_X or abs(z) > LENGTH or y in (-2, -1, TOP + 1, TOP + 2):
                    blocks[(x, y, z)] = VOID

    for c in solid:
        blocks[c] = material(*c)

    # Missing-texture blooms, laid over the dither. A checkerboard of the flat black and the flat
    # white block, aligned to the world grid rather than to the surface, because that is what a
    # texture that has not loaded looks like and nothing else in Minecraft does.
    checkered = 0
    for i in range(PATCHES):
        cx = int(hash01(i, 101) * (2 * HALF_X + 1)) - HALF_X
        cy = 2 + int(hash01(i, 202) * (TOP - 4))
        cz = int(hash01(i, 303) * (2 * LENGTH + 1)) - LENGTH
        r = PATCH_R[0] + hash01(i, 404) * (PATCH_R[1] - PATCH_R[0])
        ri = int(r) + 1
        for dx in range(-ri, ri + 1):
            for dy in range(-ri, ri + 1):
                for dz in range(-ri, ri + 1):
                    c = (cx + dx, cy + dy, cz + dz)
                    if c in solid and dx * dx + dy * dy + dz * dz <= r * r:
                        blocks[c] = VOID if (c[0] + c[1] + c[2]) % 2 == 0 else GLARE
                        checkered += 1

    # ---- seal --------------------------------------------------------------
    filled_cells = set(blocks)
    lo, hi = (-skin_x, -2, -skin_z), (skin_x, TOP + 2, skin_z)
    seen, queue = set(), deque()
    for x in range(lo[0], hi[0] + 1):
        for y in range(lo[1], hi[1] + 1):
            for z in range(lo[2], hi[2] + 1):
                if (x in (lo[0], hi[0]) or y == hi[1] or z in (lo[2], hi[2])) \
                        and (x, y, z) not in filled_cells:
                    seen.add((x, y, z))
                    queue.append((x, y, z))
    while queue:
        x, y, z = queue.popleft()
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + d[0], y + d[1], z + d[2])
            if all(lo[i] <= n[i] <= hi[i] for i in range(3)) and n not in seen \
                    and n not in filled_cells:
                seen.add(n)
                queue.append(n)
    if pale_foot in seen:
        raise AssertionError("room is NOT sealed - outside air reaches the canyon floor.")

    # ---- emit --------------------------------------------------------------
    # z is shifted so that nothing the function places, and neither structure block, ever lands
    # further along +z than the square the player runs it from.
    yshift, zshift = 2, -(LENGTH + 3)
    lines = ["# The Fault. Stand in CLEAR AIR - the build runs away from you along -Z - and run:",
             f"#   /function build:{NAME}",
             "# Generated by tools/generate_fault_room.py - do not hand-edit.", ""]
    for (x, y, z), block in blocks.items():
        lines.append(f"setblock ~{x} ~{y + yshift} ~{z + zshift} {block}")
    for block, foot, head, facing in ((PALE_BED, pale_foot, pale_head, "south"),
                                      (DARK_BED, dark_foot, dark_head, "north")):
        lines.append(f"setblock ~{foot[0]} ~{foot[1] + yshift} ~{foot[2] + zshift} "
                     f"{block}[facing={facing},part=foot]")
        lines.append(f"setblock ~{head[0]} ~{head[1] + yshift} ~{head[2] + zshift} "
                     f"{block}[facing={facing},part=head]")

    os.makedirs(FUNC_DIR, exist_ok=True)
    with open(f"{PACK}/pack.mcmeta", "w") as f:
        f.write('{\n  "pack": {\n    "pack_format": 48,\n'
                '    "description": "dim_descent build helpers"\n  }\n}\n')
    with open(f"{FUNC_DIR}/{NAME}.mcfunction", "w") as f:
        f.write("\n".join(lines) + "\n")

    span = (2 * skin_x + 1, TOP + 5, 2 * skin_z + 1)
    zones = ", ".join(f"q={q}" for q in QUANTS)
    print(f"wrote {FUNC_DIR}/{NAME}.mcfunction")
    print(f"  {len(lines) - 4} setblock commands")
    print(f"  terrain {filled} blocks, {filled / volume:.0%} of the interior")
    print(f"  walls exactly periodic in x every {PERIOD_X} blocks "
          f"({(2 * HALF_X + 1) / PERIOD_X:.1f} repeats visible across the room)")
    print(f"  {len(QUANTS)} lattice zones along -z: {zones}, each verified lump-quantised")
    print(f"  {len(PALETTE) + len(PILLARS)} materials dithered per lump; "
          f"{checkered} blocks in {PATCHES} missing-texture patches")
    print(f"  {placed} floating debris lumps above y={DEBRIS_MIN_Y}")
    print(f"  crossing cost {best[dark_foot]} blocks breached (budget {MAX_BREACH}); "
          f"{len(reach)} standable cells reachable")
    print(f"  capture size {span[0]} x {span[1]} x {span[2]} "
          f"({'OK' if max(span) <= 48 else 'TOO BIG'} for the 48 cap)")
    print()
    print("  structure blocks, relative to the square P you run the function from:")
    print(f"    SAVE   corner at  P + ({-skin_x - 1}, -1, {-skin_z + zshift - 1})")
    print(f"    CORNER corner at  P + ({skin_x + 1}, {span[1]}, {skin_z + zshift + 1})")


if __name__ == "__main__":
    main()
