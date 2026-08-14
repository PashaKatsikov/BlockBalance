#!/usr/bin/env python3
# =============================================================================
#  tools/uniqueness_pngs.py — per-seed PNG re-encoding.
#
#  Store front-ends (Play Protect, moderation clusters) compare not only class
#  names and manifests but also the byte content of drawable resources.  Two
#  apps sharing an identical `plate_blank.png` down to the byte, or a launcher
#  icon that pHashes into the same bucket as another submission, hand a
#  clustering system a match for free.
#
#  This tool touches PNG files in ONE of two modes:
#
#    safe  (default) — ±1 RGB per channel on 1 in ~700 pixels, strip all
#                      metadata, re-encode with a per-seed compression level.
#                      No dimension changes. Skips mipmap/ (launcher icons),
#                      any *.9.png (nine-patch, breaks under mutation) and
#                      anything smaller than 48×48 (icon-sized).
#
#    medium         — safe + ±3px random padding on PNGs larger than 256×256.
#    aggressive     — medium + random DPI metadata + ±3 RGB.
#
#  The tool is idempotent: on the first apply it snapshots each PNG under
#  `.uniqueness/originals/png/` and always mutates that snapshot, so re-runs
#  produce the same bytes and never drift.
# =============================================================================
from __future__ import annotations

import argparse
import hashlib
import io
import random
import shutil
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    print("Pillow is required — `pip install Pillow`", file=sys.stderr)
    sys.exit(2)

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except AttributeError:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

REPO = Path(__file__).resolve().parents[1]
APP = REPO / "app"
RES_ROOT = APP / "src" / "main" / "res"
SNAP_ROOT = REPO / ".uniqueness" / "originals" / "png"


def read_seed(explicit: str | None) -> str:
    if explicit:
        return explicit.strip()
    props = REPO / "gray.properties"
    if not props.exists():
        raise SystemExit("gray.properties not found; run graySeed first.")
    for raw in props.read_text(encoding="utf-8").splitlines():
        line = raw.split("#", 1)[0].strip()
        if line.startswith("gray.seed"):
            return line.split("=", 1)[1].strip()
    raise SystemExit("gray.seed missing.")


def rng_for(seed: str, salt: str) -> random.Random:
    key = hashlib.sha256(f"{salt}|{seed}".encode()).hexdigest()
    return random.Random(int(key[:16], 16))


def eligible_pngs(mode: str) -> list[Path]:
    """
    Walk `res/drawable*` and (in medium+ mode) `res/mipmap*`. Skip .9.png and
    everything under a size threshold. WebP is left alone — the design source
    lives elsewhere and the WebP re-encoder is a much bigger topic.
    """
    seen: set[Path] = set()
    for pat in ["drawable*"]:
        for path in RES_ROOT.glob(pat + "/**/*.png"):
            if ".9.png" in path.name:
                continue
            seen.add(path)
    if mode in ("medium", "aggressive"):
        for path in RES_ROOT.glob("mipmap*/**/*.png"):
            if ".9.png" in path.name:
                continue
            seen.add(path)
    return sorted(seen)


def snapshot(path: Path, apply: bool) -> Path:
    """Copy the file into the snapshot store on first run; always feed
    mutations from the snapshot afterwards so this pass is idempotent."""
    rel = path.relative_to(REPO)
    snap = SNAP_ROOT / rel
    if not snap.exists() and apply:
        snap.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(str(path), str(snap))
    return snap if snap.exists() else path


# ────────────────────────────────────────────────────────────────────────────
# Mutations
# ────────────────────────────────────────────────────────────────────────────

def _shift(byte: int, delta: int) -> int:
    return max(0, min(255, byte + delta))


def mutate_one(src: Path, dst: Path, rng: random.Random, mode: str) -> dict:
    """Rewrite src → dst with per-seed changes. Returns a per-file summary."""
    with Image.open(src) as im:
        im.load()
        original_size = im.size
        original_mode = im.mode
        # Strip everything on save.
        im.info.clear()

        # ── Colour noise: 1 pixel in ~700 gets a ±1 or ±3 delta ────────────
        max_delta = 1 if mode == "safe" else 3
        if im.mode not in ("RGB", "RGBA"):
            im = im.convert("RGBA")

        pixels = im.load()
        w, h = im.size
        # A predictable sample count — proportional to area — so bigger images
        # get more mutations but nothing explodes on huge sprite sheets.
        # A high area with only ~0.15% pixels touched stays visually clean.
        sample_count = max(4, int((w * h) * 0.0015))
        touched = 0
        for _ in range(sample_count):
            x = rng.randrange(0, w)
            y = rng.randrange(0, h)
            r, g, b, *a = pixels[x, y] if im.mode == "RGBA" else pixels[x, y] + (255,)
            dr, dg, db = rng.randint(-max_delta, max_delta), rng.randint(-max_delta, max_delta), rng.randint(-max_delta, max_delta)
            r2, g2, b2 = _shift(r, dr), _shift(g, dg), _shift(b, db)
            if im.mode == "RGBA":
                pixels[x, y] = (r2, g2, b2, a[0])
            else:
                pixels[x, y] = (r2, g2, b2)
            touched += 1

        # ── Optional padding for medium/aggressive on non-tiny images ─────
        if mode in ("medium", "aggressive") and min(w, h) >= 256:
            pad = rng.randint(1, 3)
            new_w, new_h = w + pad, h + pad
            padded = Image.new(im.mode, (new_w, new_h), (0, 0, 0, 0) if im.mode == "RGBA" else (0, 0, 0))
            offset_x = rng.randint(0, pad)
            offset_y = rng.randint(0, pad)
            padded.paste(im, (offset_x, offset_y))
            im = padded

        # ── Save with a per-seed compression level ───────────────────────
        compression = rng.randint(6, 9)
        params = dict(format="PNG", optimize=False, compress_level=compression)
        if mode == "aggressive":
            params["dpi"] = (rng.randint(70, 90), rng.randint(70, 90))

        dst.parent.mkdir(parents=True, exist_ok=True)
        im.save(str(dst), **params)

    return {
        "path": str(dst.relative_to(REPO)).replace("\\", "/"),
        "in_size": list(original_size),
        "out_size": list(im.size),
        "mode": original_mode,
        "pixels_touched": touched,
    }


# ────────────────────────────────────────────────────────────────────────────
# Orchestration
# ────────────────────────────────────────────────────────────────────────────

MIN_SIDE = 48   # skip favicon-sized icons

def main() -> int:
    ap = argparse.ArgumentParser(description="Per-seed PNG re-encoding.")
    ap.add_argument("--mode", choices=["safe", "medium", "aggressive"], default="safe")
    ap.add_argument("--seed", help="Override gray.seed.")
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()

    seed = read_seed(args.seed)
    rng = rng_for(seed, f"pngs:{args.mode}")
    candidates = eligible_pngs(args.mode)

    summaries = []
    skipped_small = 0
    for path in candidates:
        try:
            with Image.open(path) as probe:
                w, h = probe.size
        except Exception:
            continue
        if min(w, h) < MIN_SIDE:
            skipped_small += 1
            continue
        source = snapshot(path, apply=args.apply)
        # Draw a per-file sub-RNG from the file path + seed so touching one
        # file does not renumber the entropy for the rest.
        file_rng = rng_for(seed, f"png:{path.relative_to(REPO).as_posix()}")
        if args.apply:
            summary = mutate_one(source, path, file_rng, args.mode)
            summaries.append(summary)
        else:
            summaries.append({"path": str(path.relative_to(REPO)).replace("\\", "/"), "skipped": "plan"})

    print("═══ PNG UNIQUENESS ═══")
    print(f"seed prefix    : {seed[:10]}…")
    print(f"mode           : {args.mode}")
    print(f"eligible files : {len(candidates)}")
    print(f"skipped small  : {skipped_small}")
    print(f"processed      : {len(summaries)}")
    if not args.apply:
        print("\n(plan only — pass --apply to write.)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
