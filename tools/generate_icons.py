"""One-shot icon generator for the launcher icon set.

Adaptive-icon layout — the two layers do different jobs:

* ic_launcher_foreground: source drawn INSIDE the 66 dp inner safe circle of
  the 108 dp canvas. That way every launcher mask (round / squircle / square
  / teardrop) hits transparent padding, never the artwork — nothing is cropped.
* ic_launcher_background: the source itself, upscaled to fill the whole 108 dp
  canvas and softened with a small blur. The mask crops this, and what it
  crops is a soft continuation of the image, so the icon reads as full-bleed
  with no empty band.

Legacy raster (`ic_launcher`, `ic_launcher_round`) is only shown on very old
launchers and in a few Android chrome spots that never mask the icon. Keeping
them at full 48 dp full-bleed matches the historical layout for those cases.

Run:  python tools/generate_icons.py <source.png>
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

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
# How large the artwork sits inside the 108 dp adaptive canvas. Android
# guarantees only the inner 66 dp survives every launcher mask, but every
# launcher we ship to shows at least ~75 dp of the canvas (Pixel squircle,
# OneUI square, MIUI teardrop all clear that much). Going bigger than the
# strict guarantee makes the icon fill visibly more of the tile — the source
# already has sky/clouds around the tower, so the outer sliver a very tight
# circular mask might still clip is filler pixels, not content.
SAFE_INNER_DP = 75


def resample(src: Image.Image, size: int) -> Image.Image:
    return src.resize((size, size), Image.LANCZOS)


def circular(square: Image.Image) -> Image.Image:
    mask = Image.new("L", square.size, 0)
    ImageDraw.Draw(mask).ellipse((0, 0, square.size[0], square.size[1]), fill=255)
    out = Image.new("RGBA", square.size, (0, 0, 0, 0))
    out.paste(square, (0, 0), mask=mask)
    return out


def foreground_layer(src: Image.Image, canvas: int) -> Image.Image:
    """Full source shrunk to the safe inner square, centred on a clear canvas."""
    inner = round(canvas * SAFE_INNER_DP / ADAPTIVE_BASE)
    inner_img = resample(src, inner)
    out = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    offset = (canvas - inner) // 2
    out.paste(inner_img, (offset, offset), mask=inner_img.split()[-1])
    return out


def background_layer(src: Image.Image, canvas: int) -> Image.Image:
    """Full source scaled to the whole canvas, softened so mask cuts blend in."""
    full = resample(src.convert("RGB"), canvas)
    blurred = full.filter(ImageFilter.GaussianBlur(radius=canvas * 0.045))
    return blurred.convert("RGBA")


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

        foreground_layer(src, adaptive_size).save(
            folder / "ic_launcher_foreground.png", optimize=True
        )
        background_layer(src, adaptive_size).save(
            folder / "ic_launcher_background.png", optimize=True
        )

        print(f"{bucket}: legacy {legacy_size}px, adaptive {adaptive_size}px "
              f"(safe inner {round(adaptive_size * SAFE_INNER_DP / ADAPTIVE_BASE)}px)")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: python tools/generate_icons.py <source.png>")
    main(sys.argv[1])
