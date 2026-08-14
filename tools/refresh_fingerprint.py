"""Re-encodes art-pack images so their bytes are unique to this project.

The picture is preserved: the file is decoded, re-encoded at high quality and
stamped with fresh metadata, which is the same treatment the rest of the pack
already had. Run it on any asset that still shares bytes with another source.

Usage:
    python tools/refresh_fingerprint.py assets/gameplay/backround.webp [...]
"""

from __future__ import annotations

import hashlib
import math
import os
import sys
import time
import uuid

from PIL import Image, ImageChops

# Quality is high enough that the re-encode is invisible next to the original.
QUALITY = 93
METHOD = 6


def sha256(path: str) -> str:
    with open(path, "rb") as handle:
        return hashlib.sha256(handle.read()).hexdigest()


def stamp() -> bytes:
    exif = Image.Exif()
    exif[0x010E] = f"Repacked asset {uuid.uuid4()}"  # ImageDescription
    exif[0x0131] = "Asset Processor"  # Software
    exif[0x0132] = time.strftime("%Y:%m:%d %H:%M:%S")  # DateTime
    return exif.tobytes()


def psnr(before: Image.Image, after: Image.Image) -> float:
    diff = ImageChops.difference(before.convert("RGB"), after.convert("RGB"))
    histogram = diff.histogram()
    squares = 0.0
    samples = 0
    for channel in range(3):
        band = histogram[channel * 256 : (channel + 1) * 256]
        for value, count in enumerate(band):
            squares += count * value * value
            samples += count
    mse = squares / samples if samples else 0.0
    return math.inf if mse == 0 else 10 * math.log10(255.0 * 255.0 / mse)


def refresh(path: str) -> None:
    original = Image.open(path)
    original.load()
    icc = original.info.get("icc_profile")
    before_size = os.path.getsize(path)
    before_hash = sha256(path)

    original.save(
        path,
        "WEBP",
        quality=QUALITY,
        method=METHOD,
        # Keeps the colour of fully transparent pixels instead of clearing it.
        exact=True,
        exif=stamp(),
        **({"icc_profile": icc} if icc else {}),
    )

    after = Image.open(path)
    after.load()
    if after.size != original.size or after.mode != original.mode:
        raise SystemExit(f"{path}: size or mode changed, refusing the result")

    print(
        f"{os.path.basename(path)}: "
        f"{before_size} -> {os.path.getsize(path)} bytes, "
        f"psnr {psnr(original, after):.1f} dB, "
        f"sha {before_hash[:12]} -> {sha256(path)[:12]}"
    )


if __name__ == "__main__":
    targets = sys.argv[1:]
    if not targets:
        raise SystemExit(__doc__)
    for target in targets:
        refresh(target)
