#!/usr/bin/env python3
"""Check a captured room .nbt before it joins the pool.

    python3 tools/verify_room_nbt.py run/saves/.../generated/dimdescent/structures/rooms/basin.nbt
    python3 tools/verify_room_nbt.py src/main/resources/data/dimdescent/structure/rooms/*.nbt

The room pool is discovered at runtime, so dropping a bad .nbt into `data/dimdescent/structure/rooms/`
is enough to put a broken room in front of players with no code change and no compile error to catch
it. This is the gate. It matters most for rooms handed over by a collaborator, where nobody here
watched the build happen.

READ ONLY. It never writes NBT - editing a structure file needs a codec proven to round-trip the
original byte-for-byte first, which is a different and much more dangerous job.

Checks, in order of how badly they bite:
  * exactly one PALE Nexus bed head - it is the entrance, the arrival facing and the way back
  * at least one DARK Nexus bed head - a room with none is a dead end nobody can leave forwards
  * no dimension larger than 48 - the structure-block cap
  * no stray terrain (grass/dirt/stone/etc.) - the classic round-room-on-the-ground capture bug
"""

import gzip
import struct
import sys
from pathlib import Path

MAX_DIM = 48
PALE_BED = "dimdescent:pale_dream_bed"
DARK_BED = "dimdescent:dream_bed"

# Blocks that mean "the capture box caught the world it was sitting in".
TERRAIN = {
    "minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:podzol",
    "minecraft:stone", "minecraft:deepslate", "minecraft:gravel", "minecraft:sand",
    "minecraft:sandstone", "minecraft:bedrock", "minecraft:water", "minecraft:snow",
    "minecraft:short_grass", "minecraft:tall_grass", "minecraft:fern", "minecraft:dead_bush",
    "minecraft:oak_log", "minecraft:oak_leaves", "minecraft:birch_log", "minecraft:birch_leaves",
}


class Reader:
    def __init__(self, data):
        self.d, self.i = data, 0

    def take(self, n):
        self.i += n
        return self.d[self.i - n:self.i]

    def num(self, fmt):
        return struct.unpack(">" + fmt, self.take(struct.calcsize(fmt)))[0]

    def string(self):
        return self.take(self.num("H")).decode("utf-8", "replace")

    def payload(self, tag):
        if tag == 1:
            return self.num("b")
        if tag == 2:
            return self.num("h")
        if tag == 3:
            return self.num("i")
        if tag == 4:
            return self.num("q")
        if tag == 5:
            return self.num("f")
        if tag == 6:
            return self.num("d")
        if tag == 7:
            return self.take(self.num("i"))
        if tag == 8:
            return self.string()
        if tag == 9:
            inner, count = self.num("b"), self.num("i")
            return [self.payload(inner) for _ in range(max(0, count))]
        if tag == 10:
            out = {}
            while True:
                t = self.num("b")
                if t == 0:
                    return out
                # Name MUST be read before the payload. `out[self.string()] = self.payload(t)`
                # looks right and is not: Python evaluates the right-hand side first, so the
                # payload gets read out of the name's bytes and the whole stream desyncs.
                key = self.string()
                out[key] = self.payload(t)
        if tag == 11:
            return [self.num("i") for _ in range(self.num("i"))]
        if tag == 12:
            return [self.num("q") for _ in range(self.num("i"))]
        raise ValueError(f"unknown NBT tag {tag}")

    def root(self):
        tag = self.num("b")
        self.string()
        return self.payload(tag)


def load(path):
    raw = Path(path).read_bytes()
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    return Reader(raw).root()


def verify(path):
    root = load(path)
    size = root.get("size") or []
    palette = root.get("palette") or []
    blocks = root.get("blocks") or []

    names = [p.get("Name", "?") for p in palette]
    # A bed occupies two cells; count HEAD halves so one bed counts once.
    heads = {PALE_BED: 0, DARK_BED: 0}
    for b in blocks:
        entry = palette[b["state"]] if b.get("state", -1) < len(palette) else {}
        name = entry.get("Name")
        if name in heads and (entry.get("Properties") or {}).get("part") == "head":
            heads[name] += 1

    problems, notes = [], []
    if len(size) == 3:
        notes.append(f"size {size[0]} x {size[1]} x {size[2]}")
        if any(d > MAX_DIM for d in size):
            problems.append(f"exceeds the {MAX_DIM}-block structure cap: {size}")
    else:
        problems.append("no size tag - is this a structure-block capture?")

    notes.append(f"{len(blocks)} blocks, {len(palette)} palette entries")
    notes.append(f"{heads[PALE_BED]} pale bed, {heads[DARK_BED]} dark bed")

    if heads[PALE_BED] != 1:
        problems.append(f"needs exactly 1 pale Nexus bed (the entrance), found {heads[PALE_BED]}")
    if heads[DARK_BED] < 1:
        problems.append("no dark Nexus bed - the room would be a dead end")

    stray = sorted(set(names) & TERRAIN)
    if stray:
        problems.append(f"stray terrain captured: {', '.join(stray)}")

    ok = not problems
    print(f"{'PASS' if ok else 'FAIL'}  {Path(path).name}")
    for n in notes:
        print(f"        {n}")
    for p in problems:
        print(f"    !!  {p}")
    return ok


def main():
    targets = sys.argv[1:]
    if not targets:
        print(__doc__)
        return 2
    return 0 if all([verify(t) for t in targets]) else 1


if __name__ == "__main__":
    sys.exit(main())
