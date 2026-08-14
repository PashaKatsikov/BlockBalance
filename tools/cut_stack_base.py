"""Cuts the shop front out of its black backdrop for use as the stack base.

The master art sits on flat black and has no alpha channel, and the painting
itself contains pure black outlines, so a plain black threshold would punch
holes through the shop. Instead the silhouette is closed first: the bright mask
is grown to bridge the outlines, everything the border cannot reach is treated as
shop, and the mask is shrunk back to its original size and feathered.

Usage:
    python tools/cut_stack_base.py
"""

from __future__ import annotations

import os
from collections import deque

from PIL import Image, ImageChops, ImageFilter

SOURCE = "assets/gameplay/block_asset_main.webp"
TARGET = "app/src/main/assets/derived/stack_base.png"

# Anything darker than this in every channel may be backdrop.
BLACK_LEVEL = 24

# Widest black outline the closing step has to bridge, in pixels.
BRIDGE = 9


def brightest(image: Image.Image) -> Image.Image:
    red, green, blue = image.split()
    return ImageChops.lighter(ImageChops.lighter(red, green), blue)


def reachable_from_border(mask: Image.Image) -> bytearray:
    """Marks the zero pixels of [mask] that connect to the image border."""
    width, height = mask.size
    values = mask.load()
    seen = bytearray(width * height)
    queue: deque[tuple[int, int]] = deque()

    for x in range(width):
        queue.append((x, 0))
        queue.append((x, height - 1))
    for y in range(height):
        queue.append((0, y))
        queue.append((width - 1, y))

    while queue:
        x, y = queue.popleft()
        if not (0 <= x < width and 0 <= y < height):
            continue
        index = y * width + x
        if seen[index] or values[x, y] != 0:
            continue
        seen[index] = 1
        queue.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))

    return seen


def main() -> None:
    source = Image.open(SOURCE).convert("RGB")
    width, height = source.size

    paint = brightest(source).point(lambda value: 255 if value > BLACK_LEVEL else 0)
    closed = paint.filter(ImageFilter.MaxFilter(BRIDGE))

    outside = reachable_from_border(closed)
    filled = Image.new("L", source.size, 255)
    pixels = filled.load()
    for index, marked in enumerate(outside):
        if marked:
            pixels[index % width, index // width] = 0

    mask = filled.filter(ImageFilter.MinFilter(BRIDGE)).filter(ImageFilter.GaussianBlur(0.8))

    cut = source.convert("RGBA")
    cut.putalpha(mask)
    box = cut.getbbox()
    if box:
        cut = cut.crop(box)

    os.makedirs(os.path.dirname(TARGET), exist_ok=True)
    cut.save(TARGET, "PNG", optimize=True)

    kept = sum(1 for value in mask.getdata() if value > 8)
    print(
        f"{TARGET}: {cut.size[0]}x{cut.size[1]}, "
        f"{kept * 100 // (width * height)}% of the master kept"
    )


if __name__ == "__main__":
    main()
