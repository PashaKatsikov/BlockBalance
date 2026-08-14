#!/usr/bin/env python3
# =============================================================================
#  tools/apk_fingerprint.py — post-build APK fuzzy-hash + portfolio ledger.
#
#  Compute a TLSH digest of a freshly built APK and compare it against the
#  local hash of every APK this repo has shipped so far. TLSH distance below
#  100 is the working threshold — anything closer than that means the two
#  binaries look "similar enough" to the same static clustering that store
#  moderation runs.
#
#  Usage:
#     python tools/apk_fingerprint.py path/to/app-release.apk
#     python tools/apk_fingerprint.py path/to/app-release.apk --tag ProjectName
#     python tools/apk_fingerprint.py --list
#     python tools/apk_fingerprint.py --threshold 120 path/to/apk.apk
#
#  Fallback: when `python-tlsh` is not installed the tool computes a
#  homegrown "shingle" hash (fixed-size chunks, sha256, XOR-fold) that is
#  strictly worse than TLSH but still catches the trivial case of two APKs
#  that are byte-identical or byte-identical minus a few pages. Install
#  `python-tlsh` (`pip install python-tlsh`) for the real thing.
# =============================================================================
from __future__ import annotations

import argparse
import hashlib
import io
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except AttributeError:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

REPO = Path(__file__).resolve().parents[1]
HASH_DB = REPO / ".uniqueness" / "apk_hashes.json"


# ── Try to load python-tlsh — otherwise use the built-in fallback ─────────
try:
    import tlsh  # type: ignore

    def compute_tlsh(data: bytes) -> str:
        h = tlsh.hash(data)
        return h if h and h != "TNULL" else ""

    def tlsh_distance(a: str, b: str) -> int:
        try:
            return tlsh.diff(a, b)
        except Exception:
            return 0

    TLSH_KIND = "python-tlsh"
except ImportError:
    def compute_tlsh(data: bytes) -> str:
        return ""

    def tlsh_distance(a: str, b: str) -> int:
        return -1

    TLSH_KIND = "unavailable (pip install python-tlsh)"


# ── Fallback shingle hash — a coarse fingerprint that catches trivial dupes.
#    128-byte digest built by chunking the file into 4 KiB blocks, hashing
#    each with sha1 and folding the block hashes with XOR. Hamming distance
#    over the resulting bits is roughly proportional to shared byte content.

def shingle_hash(data: bytes, block: int = 4096, out_bits: int = 128) -> str:
    if not data:
        return ""
    folded = bytearray(out_bits // 8)
    for i in range(0, len(data), block):
        chunk = data[i : i + block]
        digest = hashlib.sha1(chunk).digest()
        for j in range(len(folded)):
            folded[j] ^= digest[j % len(digest)]
    return folded.hex()


def shingle_distance(a: str, b: str) -> int:
    if not a or not b or len(a) != len(b):
        return -1
    ba = bytes.fromhex(a)
    bb = bytes.fromhex(b)
    return sum(bin(x ^ y).count("1") for x, y in zip(ba, bb))


# ── Ledger IO ──────────────────────────────────────────────────────────────

def load_db() -> dict:
    if not HASH_DB.exists():
        return {"version": 1, "entries": []}
    return json.loads(HASH_DB.read_text(encoding="utf-8"))


def save_db(db: dict) -> None:
    HASH_DB.parent.mkdir(parents=True, exist_ok=True)
    HASH_DB.write_text(json.dumps(db, indent=2, ensure_ascii=False), encoding="utf-8")


# ── Main ───────────────────────────────────────────────────────────────────

def cmd_check(apk: Path, tag: str | None, threshold: int, record: bool) -> int:
    data = apk.read_bytes()
    if not data:
        print("empty apk", file=sys.stderr)
        return 2

    sha = hashlib.sha256(data).hexdigest()
    tlsh_h = compute_tlsh(data)
    fallback_h = shingle_hash(data)
    size = len(data)

    db = load_db()
    conflicts_tlsh: list[tuple[dict, int]] = []
    conflicts_shingle: list[tuple[dict, int]] = []

    for entry in db["entries"]:
        if entry["sha256"] == sha:
            print(f"× identical APK already in the ledger: {entry.get('tag') or entry['sha256'][:12]}")
            return 3

        if tlsh_h and entry.get("tlsh"):
            d = tlsh_distance(tlsh_h, entry["tlsh"])
            if 0 <= d <= threshold:
                conflicts_tlsh.append((entry, d))

        if fallback_h and entry.get("shingle"):
            d = shingle_distance(fallback_h, entry["shingle"])
            # Fallback threshold is different — a 128-bit Hamming distance
            # under 40 is the coarse "very similar" band.
            if 0 <= d <= 40:
                conflicts_shingle.append((entry, d))

    print("═══ APK FINGERPRINT ═══")
    print(f"path        : {apk}")
    print(f"size        : {size:,} bytes")
    print(f"sha256      : {sha}")
    print(f"tlsh        : {tlsh_h or '(unavailable)'}")
    print(f"shingle     : {fallback_h[:32]}…")
    print(f"tlsh module : {TLSH_KIND}")
    print(f"ledger      : {len(db['entries'])} entries")

    if conflicts_tlsh:
        print("\n✗ TLSH conflicts (distance <= threshold):")
        for entry, d in conflicts_tlsh:
            print(f"    distance={d:3d}  {entry.get('tag') or ''}  sha={entry['sha256'][:12]}  {entry.get('timestamp')}")
    if conflicts_shingle and not conflicts_tlsh:
        print("\n△ Shingle conflicts (fallback signal — install python-tlsh for the real check):")
        for entry, d in conflicts_shingle:
            print(f"    hamming={d:3d}  {entry.get('tag') or ''}  sha={entry['sha256'][:12]}  {entry.get('timestamp')}")

    if conflicts_tlsh:
        return 4

    if record:
        db["entries"].append({
            "tag":       tag,
            "sha256":    sha,
            "tlsh":      tlsh_h,
            "shingle":   fallback_h,
            "size":      size,
            "timestamp": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        })
        save_db(db)
        print(f"\n✓ recorded in {HASH_DB.relative_to(REPO)}")

    return 0


def cmd_list() -> int:
    db = load_db()
    if not db["entries"]:
        print("(ledger empty)")
        return 0
    print(f"{'tag':<24} {'sha256':<16} {'size':>12}  {'timestamp'}")
    for entry in db["entries"]:
        print(f"{(entry.get('tag') or '-'):<24} {entry['sha256'][:16]:<16} {entry['size']:>12,}  {entry.get('timestamp')}")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description="Fingerprint an APK and compare it against the local ledger.")
    ap.add_argument("apk", nargs="?", type=Path, help="Path to the APK to fingerprint.")
    ap.add_argument("--tag", help="Human-readable label to record with this APK.")
    ap.add_argument("--threshold", type=int, default=100, help="TLSH distance threshold (default 100).")
    ap.add_argument("--no-record", action="store_true", help="Compute and compare but do not append to the ledger.")
    ap.add_argument("--list", action="store_true", help="List every entry in the ledger and exit.")
    args = ap.parse_args()

    if args.list:
        return cmd_list()

    if not args.apk:
        ap.print_help()
        return 1

    if not args.apk.exists():
        print(f"apk not found: {args.apk}", file=sys.stderr)
        return 2

    return cmd_check(args.apk, args.tag, args.threshold, record=not args.no_record)


if __name__ == "__main__":
    sys.exit(main())
