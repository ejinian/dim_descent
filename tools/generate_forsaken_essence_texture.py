#!/usr/bin/env python3
"""Regenerate the animated Forsaken Essence texture.

    python3 tools/generate_forsaken_essence_texture.py

Writes a 16x384 PNG (24 frames of 16x16) to the mod's block textures. Animation timing lives
separately in forsaken_essence.png.mcmeta ("frametime": 4, "interpolate": true) - edit that file to
change SPEED, and this one to change LOOK.

WHY IT IS GENERATED RATHER THAN DRAWN
Every wave below uses INTEGER frequencies over the 16px tile and the 24-frame loop, which makes the
result exactly periodic in x, y and t. That buys three things a hand-drawn texture kept getting
wrong: it tiles seamlessly against the neighbouring block left/right, it tiles top/bottom, and frame
24 flows back into frame 1 with no visible snap. The script asserts all three at the end, so a bad
edit fails loudly instead of shipping a seam.
"""

import math
import random

from PIL import Image

OUT = "src/main/resources/assets/dimdescent/textures/block/forsaken_essence.png"

W = H = 16   # tile size
F = 24       # frames in the loop


# ---------------------------------------------------------------------------
# COLOUR - this is the bit you want. Plain (R, G, B), 0-255.
#
# To darken the whole thing, scale all three down together; the relationship between them is what
# reads as "blood" rather than "lava". Keep green and blue well under red and roughly equal, or it
# drifts orange (which is what the first version got wrong).
# ---------------------------------------------------------------------------
BASE = (7, 3, 4)      # the dead flesh between veins - almost black
DEEP = (44, 5, 7)     # clotted crimson, the body of a vein
CORE = (92, 12, 13)   # the thin bright filament running down a vein's centre

# Where the vein body gives way to the bright core. Lower = thicker glowing cores.
CORE_THRESHOLD = 0.55

# Vein geometry. THICKNESS is how wide a vein spreads before fading out; SHARPNESS pinches it into a
# strand (higher = thinner, more wiry). Together these decide "filaments" vs "broad bands".
VEIN_THICKNESS = 1.15
VEIN_SHARPNESS = 2.2

# Static per-pixel noise, applied identically in every frame so it does not shimmer.
GRAIN = 0.10
GRAIN_SEED = 0xB100D

# Traveling waves: (amplitude, fx, fy, ft, phase). fx/fy/ft MUST stay integers - that is what keeps
# the tiling and the loop seamless. Positive fy with positive ft makes the pattern run DOWNWARD,
# like blood on a wall; flip the sign of ft to run it upward.
TERMS = [
    (1.00, 1, 2, 1, 0.00),
    (0.70, 2, 3, 1, 1.70),
    (0.45, 3, 5, 2, 3.10),
    (0.30, 1, 7, 2, 0.60),
    (0.20, 4, 4, 3, 2.20),
]


def field(x, y, t):
    """Signed wave field. Veins live where this crosses zero."""
    total = 0.0
    for amp, fx, fy, ft, phase in TERMS:
        total += amp * math.sin(2 * math.pi * (fx * x / W + fy * y / H - ft * t / F) + phase)
    return total


def lerp(a, b, u):
    return tuple(round(a[i] + (b[i] - a[i]) * u) for i in range(3))


def main():
    random.seed(GRAIN_SEED)
    grain = [[random.uniform(-GRAIN, GRAIN) for _ in range(W)] for _ in range(H)]

    sheet = Image.new("RGBA", (W, H * F))
    for t in range(F):
        for y in range(H):
            for x in range(W):
                distance = abs(field(x, y, t))
                vein = max(0.0, 1.0 - distance / VEIN_THICKNESS) ** VEIN_SHARPNESS
                vein = min(1.0, max(0.0, vein + grain[y][x] * vein))
                if vein < CORE_THRESHOLD:
                    colour = lerp(BASE, DEEP, vein / CORE_THRESHOLD)
                else:
                    colour = lerp(DEEP, CORE, (vein - CORE_THRESHOLD) / (1.0 - CORE_THRESHOLD))
                sheet.putpixel((x, t * H + y), colour + (255,))

    # Fail loudly rather than shipping a seam.
    tile_x = max(abs(field(0, y, t) - field(W, y, t)) for y in range(H) for t in range(F))
    tile_y = max(abs(field(x, 0, t) - field(x, H, t)) for x in range(W) for t in range(F))
    loop_t = max(abs(field(x, y, 0) - field(x, y, F)) for x in range(W) for y in range(H))
    for label, err in (("x tiling", tile_x), ("y tiling", tile_y), ("animation loop", loop_t)):
        assert err < 1e-9, f"{label} is not seamless (error {err:.3e}) - are all fx/fy/ft integers?"

    sheet.save(OUT)
    print(f"wrote {OUT}  ({sheet.size[0]}x{sheet.size[1]}, {F} frames)")
    print(f"seams ok - x {tile_x:.1e}, y {tile_y:.1e}, loop {loop_t:.1e}")


if __name__ == "__main__":
    main()
