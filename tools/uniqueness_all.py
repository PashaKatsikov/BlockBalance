#!/usr/bin/env python3
# =============================================================================
#  tools/uniqueness_all.py — orchestrator for every per-project uniqueness
#  pass. Runs the underlying tools in the same order every time so the result
#  is deterministic for a given `gray.seed`.
#
#  Order matters:
#    1. uniqueness_extend.py       — manifest, strings, colors, gradle, decoys
#    2. uniqueness_dictionaries.py — R8 dictionaries + proguard wiring
#    3. uniqueness_native.py       — regenerate entropy_stub.cpp
#    4. uniqueness_ast.py          — AST-like mutations on eligible Kotlin
#    5. uniqueness_pngs.py         — per-seed PNG re-encoding (safe mode)
#
#  Usage:
#     python tools/uniqueness_all.py            # plan (no writes)
#     python tools/uniqueness_all.py --apply
#     python tools/uniqueness_all.py --apply --skip-ast --skip-pngs
# =============================================================================
from __future__ import annotations

import argparse
import io
import subprocess
import sys
from pathlib import Path

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except AttributeError:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

REPO = Path(__file__).resolve().parents[1]
TOOLS = REPO / "tools"


def run(script: str, *extra: str, apply: bool) -> int:
    args = [sys.executable, str(TOOLS / script)]
    args.extend(extra)
    if apply:
        args.append("--apply")
    print(f"\n$ {' '.join(args)}")
    return subprocess.call(args, cwd=str(REPO))


def main() -> int:
    ap = argparse.ArgumentParser(description="Run every per-project uniqueness pass in order.")
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--skip-extend", action="store_true")
    ap.add_argument("--skip-dict", action="store_true")
    ap.add_argument("--skip-native", action="store_true")
    ap.add_argument("--skip-ast", action="store_true")
    ap.add_argument("--skip-pngs", action="store_true")
    ap.add_argument("--png-mode", choices=["safe", "medium", "aggressive"], default="safe")
    args = ap.parse_args()

    steps: list[tuple[str, tuple[str, ...]]] = []
    if not args.skip_extend:
        steps.append(("uniqueness_extend.py", ()))
    if not args.skip_dict:
        steps.append(("uniqueness_dictionaries.py", ()))
    if not args.skip_native:
        steps.append(("uniqueness_native.py", ()))
    if not args.skip_ast:
        steps.append(("uniqueness_ast.py", ()))
    if not args.skip_pngs:
        steps.append(("uniqueness_pngs.py", ("--mode", args.png_mode)))

    failures = []
    for script, extras in steps:
        rc = run(script, *extras, apply=args.apply)
        if rc != 0:
            failures.append((script, rc))

    print("\n═══ UNIQUENESS PIPELINE ═══")
    for script, extras in steps:
        marker = "✓" if not any(f[0] == script for f in failures) else f"✗ ({dict(failures)[script]})"
        print(f"  {marker}  {script} {' '.join(extras)}")
    if failures:
        return 1
    if not args.apply:
        print("\n(plan only — pass --apply to write.)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
