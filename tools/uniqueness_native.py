#!/usr/bin/env python3
# =============================================================================
#  tools/uniqueness_native.py — regenerate app/src/main/cpp/entropy_stub.cpp
#  with per-seed constants.
#
#  The file is opt-in: `gray.enableNativeStub = true` flips the AGP
#  externalNativeBuild block on and the .so goes into the APK. When the flag
#  is false the file is regenerated all the same (so the source tree still
#  varies per project), it just does not compile.
# =============================================================================
from __future__ import annotations

import argparse
import hashlib
import io
import random
import re
import sys
from pathlib import Path

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except AttributeError:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

REPO = Path(__file__).resolve().parents[1]
CPP_PATH = REPO / "app" / "src" / "main" / "cpp" / "entropy_stub.cpp"


def read_seed(explicit: str | None) -> str:
    if explicit:
        return explicit.strip()
    props = REPO / "gray.properties"
    if not props.exists():
        raise SystemExit("gray.properties not found.")
    for raw in props.read_text(encoding="utf-8").splitlines():
        line = raw.split("#", 1)[0].strip()
        if line.startswith("gray.seed"):
            return line.split("=", 1)[1].strip()
    raise SystemExit("gray.seed missing.")


def rng_for(seed: str, salt: str) -> random.Random:
    key = hashlib.sha256(f"{salt}|{seed}".encode("utf-8")).hexdigest()
    return random.Random(int(key[:16], 16))


def regen_cpp(seed: str, apply: bool) -> dict:
    rng = rng_for(seed, "native:entropy")
    pattern = rng.getrandbits(32)
    rotate = rng.randint(3, 13)     # avoid 0/1/32 boundaries
    magic  = rng.getrandbits(32) | 1
    tail_mix = rng.getrandbits(32)

    old = CPP_PATH.read_text(encoding="utf-8") if CPP_PATH.exists() else ""

    # Replace the two constants inside the marker region so hand comments
    # around them do not need to change.
    new_pattern_line = f"constexpr uint32_t kPattern = 0x{pattern:08X}u;"
    new_rotate_line  = f"constexpr uint32_t kRotate  = {rotate}u;"

    updated = old
    updated = re.sub(
        r"constexpr uint32_t kPattern\s*=\s*0x[0-9A-Fa-f]+u;",
        new_pattern_line, updated
    )
    updated = re.sub(
        r"constexpr uint32_t kRotate\s*=\s*\d+u;",
        new_rotate_line, updated
    )
    # Also rotate the two magic constants inside churn_word.
    updated = re.sub(
        r"r \* 0x[0-9A-Fa-f]+u \+ 0x[0-9A-Fa-f]+u;",
        f"r * 0x{magic:08X}u + 0x{tail_mix:08X}u;",
        updated,
    )

    changed = updated != old
    if apply and changed:
        CPP_PATH.write_text(updated, encoding="utf-8")

    return {
        "pattern": f"0x{pattern:08X}",
        "rotate":  rotate,
        "magic":   f"0x{magic:08X}",
        "tailmix": f"0x{tail_mix:08X}",
        "changed": changed,
    }


def main() -> int:
    ap = argparse.ArgumentParser(description="Regenerate entropy_stub.cpp per seed.")
    ap.add_argument("--seed", help="Override gray.seed.")
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()

    seed = read_seed(args.seed)
    result = regen_cpp(seed, apply=args.apply)

    print("═══ NATIVE STUB ═══")
    print(f"seed prefix : {seed[:10]}…")
    print(f"pattern     : {result['pattern']}")
    print(f"rotate      : {result['rotate']}")
    print(f"magic       : {result['magic']}")
    print(f"tail mix    : {result['tailmix']}")
    print(f"changed     : {result['changed']}")
    if not args.apply:
        print("\n(plan only — pass --apply.)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
