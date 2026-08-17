"""One-shot icon generator for the launcher icon set.

Mirrors the layout that was already in place before this refresh:
- adaptive layers `ic_launcher_background` and `ic_launcher_foreground`
  are the SAME full-bleed square PNG at 108 dp per density,
- legacy `ic_launcher` is that same square resized to 48 dp per density,
- legacy `ic_launcher_round` is the same square with a circular mask.

Run:  python tools/generate_icons.py <source.png>
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw

# Base density is mdpi at 48 dp for the legacy raster and 108 dp for the
# adaptive layers. Everything else scales by the DPI bucket factor.
DENSITIES = {
    "mdpi":    1.0,
    "hdpi":    1.5,
    "xhdpi":   2.0,
    "xxhdpi":  3.0,
    "xxxhdpi": 4.0,
}
LEGACY_BASE = 48
ADAPTIVE_BASE = 108


def resample(src: Image.Image, size: int) -> Image.Image:
    return src.resize((size, size), Image.LANCZOS)


def circular(square: Image.Image) -> Image.Image:
    mask = Image.new("L", square.size, 0)
    ImageDraw.Draw(mask).ellipse((0, 0, square.size[0], square.size[1]), fill=255)
    out = Image.new("RGBA", square.size, (0, 0, 0, 0))
    out.paste(square, (0, 0), mask=mask)
    return out


def main(source_path: str) -> None:
    src = Image.open(source_path).convert("RGBA")
    if src.size[0] != src.size[1]:
        raise SystemExit(f"expected square source, got {src.size}")

    res_root = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res"

    for bucket, factor in DENSITIES.items():
        legacy_size = round(LEGACY_BASE * factor)
        adaptive_size = round(ADAPTIVE_BASE * factor)

        folder = res_root / f"mipmap-{bucket}"
        folder.mkdir(parents=True, exist_ok=True)

        square = resample(src, legacy_size)
        square.save(folder / "ic_launcher.png", optimize=True)
        circular(square).save(folder / "ic_launcher_round.png", optimize=True)

        layer = resample(src, adaptive_size)
        layer.save(folder / "ic_launcher_background.png", optimize=True)
        layer.save(folder / "ic_launcher_foreground.png", optimize=True)

        print(f"{bucket}: legacy {legacy_size}px, adaptive {adaptive_size}px")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: python tools/generate_icons.py <source.png>")
    main(sys.argv[1])
