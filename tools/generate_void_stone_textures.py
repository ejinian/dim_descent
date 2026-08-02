#!/usr/bin/env python3
"""Regenerate the Nullstone and Allstone textures.

    python3 tools/generate_void_stone_textures.py

These two are a matched pair, so they are generated together by one script rather than being two
unrelated PNGs that happen to look opposite. The script asserts that every pixel of Allstone is the
exact channel-wise inverse of the corresponding Nullstone pixel, which turns "polar opposite" from a
description into something that fails loudly the moment it stops being true.

Both are deliberately FLAT - a single colour, no noise, no grain. Every other block in the mod has
texture to read; these two have none, so a surface built from them gives the eye nothing to measure
distance or scale against. That is what makes a Nullstone void look bottomless and an Allstone room
look like it has no far wall. Adding "just a bit of noise to break it up" would undo the only thing
they are for.
"""

from PIL import Image

OUT_NULL = "src/main/resources/assets/dimdescent/textures/block/nullstone.png"
OUT_ALL = "src/main/resources/assets/dimdescent/textures/block/allstone.png"

SIZE = 16

# The one knob. Nullstone is this; Allstone is 255 minus this, per channel.
NULL = (0, 0, 0)


def main():
    allstone = tuple(255 - c for c in NULL)

    null_img = Image.new("RGBA", (SIZE, SIZE), NULL + (255,))
    all_img = Image.new("RGBA", (SIZE, SIZE), allstone + (255,))

    # Fail loudly rather than shipping a pair that drifted apart.
    n = null_img.convert("RGB").load()
    a = all_img.convert("RGB").load()
    for y in range(SIZE):
        for x in range(SIZE):
            for ch in range(3):
                assert n[x, y][ch] + a[x, y][ch] == 255, (
                    f"Allstone is not the inverse of Nullstone at ({x},{y}) channel {ch}: "
                    f"{n[x, y][ch]} + {a[x, y][ch]} != 255")

    null_img.save(OUT_NULL)
    all_img.save(OUT_ALL)
    print(f"wrote {OUT_NULL}  rgb{NULL}")
    print(f"wrote {OUT_ALL}  rgb{allstone}")
    print("inverse check ok - every channel of every pixel sums to 255")


if __name__ == "__main__":
    main()
