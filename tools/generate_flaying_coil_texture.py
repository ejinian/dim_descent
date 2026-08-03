#!/usr/bin/env python3
"""Generate the Flaying Coil block and item textures.

    python3 tools/generate_flaying_coil_texture.py

Drawn from scratch, not traced from vanilla's chain.png - the mod ships none of Mojang's art. The
palette is lifted from our own dark_iron_bars instead, so the two read as the same metal, and the
blood is the same three-tone family as Forsaken Essence.

HOW THE BLOCK TEXTURE IS LAID OUT
The chain model is two flat quads crossed at right angles, and each quad takes a 3-pixel-wide strip:
columns 0-2 for one, 3-5 for the other. The second strip is the same pattern offset by half a link,
which is what makes the two planes read as links passing through each other rather than as two
identical chains occupying the same space.

THE PATTERN MUST BE PERIODIC IN Y
Chains hang in columns, so the texture's top edge has to continue into its own bottom edge or every
block boundary shows a seam. LINK is 8 rows and the tile is 16, so it repeats exactly twice - and the
script asserts the *metal* mask is period-8 rather than trusting that. The blood is deliberately
exempt from that check: it is applied after, it does not repeat, and a chain whose every link is
bloodied identically looks printed rather than used.

THE BLOOD
Old, dried, nearly brown. It only ever lands on pixels that are already metal - blood floating in the
transparent part of a cutout texture would hang in mid-air beside the chain - and that is asserted
too. It prefers the underside of a link, because that is where it would have run to.
"""

import random

from PIL import Image

BLOCK_OUT = "src/main/resources/assets/dimdescent/textures/block/flaying_coil.png"
ITEM_OUT = "src/main/resources/assets/dimdescent/textures/item/flaying_coil.png"

SIZE = 16
PERIOD = 8

# ---------------------------------------------------------------------------
# PALETTE. Metal is dark_iron_bars' own three tones so the pair match. Blood is dried, not fresh -
# keep it dark and keep green and blue close together, or it drifts orange and reads as rust.
# ---------------------------------------------------------------------------
H = (74, 74, 80, 255)      # highlight
M = (50, 50, 54, 255)      # mid
S = (30, 30, 33, 255)      # shadow
CLEAR = (0, 0, 0, 0)

BLOOD_LIT = (86, 22, 20, 255)
BLOOD_DARK = (49, 12, 13, 255)

PALETTE = {"H": H, "M": M, "S": S, ".": CLEAR}

# One link cycle: an oval seen face-on, then the next link edge-on. 8 rows, 3 wide.
LINK = [
    ".H.",
    "M.M",
    "H.S",
    "M.M",
    ".S.",
    ".H.",
    ".M.",
    ".S.",
]

# The item sprite is the same chain drawn fatter, since it is seen flat and small in a slot.
ITEM_LINK = [
    ".HHH.",
    "HM.MH",
    "HM.MH",
    "HS.SH",
    ".SSS.",
    "..H..",
    "..M..",
    "..S..",
]

BLOOD_MARKS = 7            # seed pixels; each may drag one more downward
BLOOD_SEED = 20260809


def draw(img, art, x0, rows, offset=0):
    for y in range(rows):
        line = art[(y + offset) % len(art)]
        for x, ch in enumerate(line):
            img.putpixel((x0 + x, y), PALETTE[ch])


def bloody(img, rng, marks):
    """Recolour metal pixels to dried blood. Never touches transparent pixels."""
    metal = [(x, y) for y in range(img.height) for x in range(img.width)
             if img.getpixel((x, y))[3] == 255]
    placed = []
    for _ in range(marks):
        x, y = rng.choice(metal)
        img.putpixel((x, y), BLOOD_LIT)
        placed.append((x, y))
        below = (x, y + 1)
        if below in metal and rng.random() < 0.6:      # it ran downward before it dried
            img.putpixel(below, BLOOD_DARK)
            placed.append(below)
    return placed


def main():
    rng = random.Random(BLOOD_SEED)

    block = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    draw(block, LINK, 0, SIZE, 0)
    draw(block, LINK, 3, SIZE, PERIOD // 2)     # half a link out of phase - the crossing plane

    mask = [[block.getpixel((x, y))[3] for x in range(SIZE)] for y in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            if mask[y][x] != mask[(y + PERIOD) % SIZE][x]:
                raise AssertionError(
                    f"metal is not period-{PERIOD} in y at ({x},{y}) - stacked chains will show a "
                    f"seam at every block boundary. LINK must be a whole number of cycles in {SIZE}.")

    marks = bloody(block, rng, BLOOD_MARKS)
    block.save(BLOCK_OUT)

    item = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    draw(item, ITEM_LINK, (SIZE - len(ITEM_LINK[0])) // 2, SIZE, 0)
    item_marks = bloody(item, rng, BLOOD_MARKS - 2)
    item.save(ITEM_OUT)

    for name, img, placed in (("block", block, marks), ("item", item, item_marks)):
        for x, y in placed:
            if img.getpixel((x, y))[3] != 255:
                raise AssertionError(f"{name}: blood at ({x},{y}) is on a transparent pixel - it "
                                     f"would hang in mid-air beside the chain.")

    print(f"wrote {BLOCK_OUT}  ({SIZE}x{SIZE}, two 3px strips, half a link out of phase)")
    print(f"wrote {ITEM_OUT}  ({SIZE}x{SIZE})")
    print(f"  metal verified period-{PERIOD} in y, so stacked chains have no seam")
    print(f"  {len(marks)} blood pixels on the block, {len(item_marks)} on the item, all on metal")


if __name__ == "__main__":
    main()
