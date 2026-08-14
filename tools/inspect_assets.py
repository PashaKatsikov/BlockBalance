"""Prints asset dimensions and writes downscaled PNG previews for art review."""

import glob
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "assets")
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "build_preview")


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    for path in sorted(glob.glob(os.path.join(ROOT, "**", "*.*"), recursive=True)):
        try:
            image = Image.open(path)
        except Exception as error:  # noqa: BLE001
            print("FAIL", path, error)
            continue
        frames = getattr(image, "n_frames", 1)
        name = os.path.basename(path)
        ratio = image.size[0] / image.size[1]
        print(
            "%-45s %5dx%-5d ratio=%.3f mode=%-5s frames=%d"
            % (name, image.size[0], image.size[1], ratio, image.mode, frames)
        )
        preview = image.convert("RGBA")
        preview.thumbnail((420, 420))
        preview.save(os.path.join(OUT, os.path.splitext(name)[0] + ".png"))


if __name__ == "__main__":
    main()
