"""Derives runtime-ready sprites from the master art pack.

Some master files ship without an alpha channel, so their flat backdrop is keyed
out here once instead of at runtime. Output lands in the app asset folder.

Usage: python tools/prepare_art.py
"""

import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "assets")
DST = os.path.join(ROOT, "app", "src", "main", "assets", "derived")


def key_out_backdrop(path: str, threshold: int, max_size: int) -> Image.Image:
    image = Image.open(path).convert("RGBA")
    if max(image.size) > max_size:
        scale = max_size / max(image.size)
        image = image.resize(
            (max(1, round(image.size[0] * scale)), max(1, round(image.size[1] * scale))),
            Image.LANCZOS,
        )
    width, height = image.size
    corners = ((0, 0), (width - 1, 0), (0, height - 1), (width - 1, height - 1))
    for corner in corners:
        if image.getpixel(corner)[3] == 0:
            continue
        ImageDraw.floodfill(image, corner, (0, 0, 0, 0), thresh=threshold)
    box = image.getbbox()
    if box is not None:
        image = image.crop(box)
    return image


def main() -> None:
    os.makedirs(DST, exist_ok=True)

    jobs = (
        ("ui/Game_Name.webp", "logo_wordmark.png", 60, 1024),
        ("gameplay/button_blank.jpg", "plate_blank.png", 42, 1024),
        ("gameplay/block_asset_main.webp", "stack_base.png", 60, 1024),
    )

    for source, target, threshold, max_size in jobs:
        result = key_out_backdrop(os.path.join(SRC, source), threshold, max_size)
        out_path = os.path.join(DST, target)
        result.save(out_path, optimize=True)
        print("%-22s -> %-20s %dx%d" % (source, target, result.size[0], result.size[1]))


if __name__ == "__main__":
    main()
