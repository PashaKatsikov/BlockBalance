#!/usr/bin/env python3
# =============================================================================
#  tools/uniqueness_ast.py — pattern-based Kotlin mutations.
#
#  A proper AST rewrite of a Kotlin codebase needs a parser (`tree-sitter-
#  kotlin`, a headless Kotlin PSI, ktlint's engine, …), all of which need a
#  binary install and are heavy dependencies. This tool takes a middle path:
#  three carefully-scoped regex-based transforms that never touch anything
#  the transform did not explicitly recognise. Every mutation is idempotent —
#  either marker-bounded, or reverses to the exact same result on re-run.
#
#  Transforms implemented:
#
#    T1. DECOY_METHODS   — insert 2-4 private helpers at the end of eligible
#        classes. Marker-scoped. Deterministic per (seed, file). Skipped for
#        classes extending Activity/Application/Service/BroadcastReceiver and
#        for anything under `@Serializable`.
#
#    T2. RETURN_IF       — rewrites `return if (X) A else B` at the end of
#        an expression-body function into `return when { X -> A; else -> B }`.
#        Only fires on single-line matches. Idempotent (a re-run finds no
#        matches because the new form does not fit the pattern).
#
#    T3. STMT_SWAP       — swaps two adjacent `val a = ...\n    val b = ...`
#        declarations when neither uses the other's name. Bounded to five
#        swaps per file so a big file doesn't accumulate churn.
#
#  Files excluded from all transforms:
#    - anything under `res/`, `cpp/`, `build/`, `androidTest/`, `test/`
#    - files smaller than 40 lines (usually pure model classes)
#    - files whose name matches OrbitApp.kt, LaunchGate.kt, OrbitShell.kt,
#      KeyboardSlide.kt, AttrHub.kt — hand-tuned code the gray flow depends
#      on. The safer approach is to leave them alone.
# =============================================================================
from __future__ import annotations

import argparse
import hashlib
import io
import json
import random
import re
import sys
from pathlib import Path

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except AttributeError:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

REPO = Path(__file__).resolve().parents[1]
APP_SRC = REPO / "app" / "src" / "main"
KT_SNAP_ROOT = REPO / ".uniqueness" / "originals" / "kt"


def snapshot_kt(path: Path, apply: bool) -> str:
    """Snapshot each eligible .kt on first apply and always mutate from that
    snapshot afterwards. Without this, T3 (statement swap) flip-flops the same
    two lines on every run because the RNG is deterministic and the swap is
    its own inverse."""
    rel = path.relative_to(REPO)
    snap = KT_SNAP_ROOT / rel
    if snap.exists():
        return snap.read_text(encoding="utf-8")
    current = path.read_text(encoding="utf-8")
    if apply:
        snap.parent.mkdir(parents=True, exist_ok=True)
        snap.write_text(current, encoding="utf-8")
    return current

# Files we refuse to touch (the gray flow's core).
BANNED_FILE_STEMS = {
    "OrbitApp", "LaunchGate", "OrbitShell", "KeyboardSlide",
    "AttrHub", "CfgClient", "PushRelay", "FcmReceiver",
    "Store", "Env", "GateResult", "SignalLostScreen", "OptInPrompt",
    "OrbitLoader", "WindowGlue", "Uplink",
    "Secrets", "Trace", "UrlGuard", "UserAgent",
    "BlockBalanceApp",
    # Native + decoy — we own these through other tools.
    "EntropyStub",
}

BANNED_DIR_MARKERS = {"/build/", "/androidTest/", "/test/", "/res/", "/cpp/"}

AST_TAG = "UNIQUE:AST_DECOYS"

METHOD_VERBS = [
    "warm", "prepare", "seal", "prime", "settle", "graze", "trickle",
    "unspool", "cradle", "ripen", "furl", "sway", "peer", "buoy", "cusp",
    "hum", "drift", "level", "moor", "graze", "gather",
]


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
    key = hashlib.sha256(f"{salt}|{seed}".encode()).hexdigest()
    return random.Random(int(key[:16], 16))


# ── file selection ────────────────────────────────────────────────────────

def eligible_files() -> list[Path]:
    out: list[Path] = []
    for path in APP_SRC.rglob("*.kt"):
        p = str(path).replace("\\", "/")
        if any(marker in p for marker in BANNED_DIR_MARKERS):
            continue
        if path.stem in BANNED_FILE_STEMS:
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        if text.count("\n") < 40:
            continue
        # Skip anything with a Serializable annotation — R8 and kotlinx.serialization
        # are picky about class shapes.
        if "@Serializable" in text:
            continue
        out.append(path)
    return sorted(out)


# ── T1: decoy methods ─────────────────────────────────────────────────────

def _decoy_method(rng: random.Random, seq: int) -> str:
    verb = rng.choice(METHOD_VERBS)
    suffix = "".join(rng.choices("abcdefghjkmnpqrstuvwxyz", k=rng.randint(3, 5)))
    name = f"{verb}{suffix.capitalize()}"
    kind = rng.choice(["int_xor", "float_mix", "byte_scan"])

    if kind == "int_xor":
        mask = rng.getrandbits(32)
        shift = rng.randint(1, 7)
        return (
            f"    private fun {name}(sample: Long): Long =\n"
            f"        (sample xor 0x{mask:08X}L) shr {shift}\n"
        )
    if kind == "float_mix":
        a = rng.random()
        b = rng.random()
        return (
            f"    private fun {name}(intensity: Float): Float =\n"
            f"        {a:.3f}f + (intensity - {b:.3f}f) * {rng.random():.3f}f\n"
        )
    # byte_scan
    modulus = rng.randint(97, 991)
    return (
        f"    private fun {name}(payload: ByteArray): Int {{\n"
        f"        var acc = {rng.getrandbits(24)}\n"
        f"        for (byte in payload) acc = (acc * 31) xor byte.toInt()\n"
        f"        return acc % {modulus}\n"
        f"    }}\n"
    )


def _find_class_end(text: str) -> int | None:
    """
    Very conservative: find the FIRST `class X ... {` (skipping annotations),
    walk with a brace counter to find the matching `}`. Return the index of
    that closing brace. If the file has zero or multiple top-level classes,
    return None.
    """
    # Find top-level class declarations (no leading whitespace or ": ").
    class_pattern = re.compile(r"^(?:open\s+|abstract\s+|sealed\s+|data\s+|inner\s+)?class\s+\w+", re.MULTILINE)
    matches = list(class_pattern.finditer(text))
    if len(matches) != 1:
        return None

    start = matches[0].end()
    # Find the opening brace after the class declaration head.
    brace_open = text.find("{", start)
    if brace_open < 0:
        return None

    depth = 1
    i = brace_open + 1
    in_string = False
    in_triple = False
    in_line_comment = False
    in_block_comment = False
    while i < len(text):
        ch = text[i]
        nxt = text[i + 1] if i + 1 < len(text) else ""
        if in_line_comment:
            if ch == "\n":
                in_line_comment = False
        elif in_block_comment:
            if ch == "*" and nxt == "/":
                in_block_comment = False
                i += 1
        elif in_triple:
            if ch == '"' and text[i:i+3] == '"""':
                in_triple = False
                i += 2
        elif in_string:
            if ch == "\\":
                i += 1
            elif ch == '"':
                in_string = False
        else:
            if ch == "/" and nxt == "/":
                in_line_comment = True
                i += 1
            elif ch == "/" and nxt == "*":
                in_block_comment = True
                i += 1
            elif ch == '"' and text[i:i+3] == '"""':
                in_triple = True
                i += 2
            elif ch == '"':
                in_string = True
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    return i
        i += 1
    return None


def _is_transform_safe(text: str) -> bool:
    """Refuse the class-heuristic transform when the file contains anything
    that suggests it is not a plain class body (activities, applications,
    services, receivers, providers)."""
    banned_supers = (
        ": ComponentActivity", ": AppCompatActivity", ": Activity",
        ": Application", ": Service", ": BroadcastReceiver", ": ContentProvider",
        ": FirebaseMessagingService",
        ": ViewModel", ": AndroidViewModel",
        ": View(", ": SurfaceView(", ": FrameLayout(", ": WebView(",
        "@Composable",
    )
    for b in banned_supers:
        if b in text:
            return False
    return True


def apply_t1_decoys(text: str, rng: random.Random) -> tuple[str, int]:
    if not _is_transform_safe(text):
        return text, 0

    end_idx = _find_class_end(text)
    if end_idx is None:
        return text, 0

    n = rng.randint(2, 4)
    methods = [_decoy_method(rng, i) for i in range(n)]
    body = f"\n    // {AST_TAG}:BEGIN\n" + "\n".join(methods) + f"    // {AST_TAG}:END\n"

    begin_marker = f"// {AST_TAG}:BEGIN"
    end_marker = f"// {AST_TAG}:END"

    if begin_marker in text and end_marker in text:
        pattern = re.compile(
            r"\n\s*" + re.escape(begin_marker) + r".*?" + re.escape(end_marker) + r"\n",
            re.DOTALL,
        )
        updated = pattern.sub(body, text)
    else:
        updated = text[:end_idx] + body + text[end_idx:]

    return updated, n if updated != text else 0


# ── T2: return-if → return-when ───────────────────────────────────────────
# Very conservative: only single-line expression bodies of the exact form
#   fun name(...): T = if (cond) A else B
# with A and B being simple expressions (no braces, no nested `if`).

RETURN_IF_PATTERN = re.compile(
    r"^(\s*(?:private |public |internal |protected )?(?:override )?fun\s+\w+\([^)]*\)(?:\s*:\s*[\w<>?, .]+)?\s*=\s*)"
    r"if\s*\(([^()]+)\)\s+(\S[^\n]*?)\s+else\s+(\S[^\n]*?)$",
    re.MULTILINE,
)

def apply_t2_return_if(text: str) -> tuple[str, int]:
    count = 0

    def replace(match: re.Match) -> str:
        nonlocal count
        head, cond, a, b = match.group(1), match.group(2).strip(), match.group(3).strip(), match.group(4).strip()
        if "if (" in a or "if (" in b or "when {" in a or "when {" in b:
            return match.group(0)
        if b.startswith("if "):
            return match.group(0)
        count += 1
        return f"{head}when {{ {cond} -> {a}; else -> {b} }}"

    updated = RETURN_IF_PATTERN.sub(replace, text)
    return updated, count


# ── T3: swap adjacent independent val declarations ────────────────────────
# Recognise the pattern
#     val a = <expr not mentioning any local names starting with lowercase>
#     val b = <same>
# on two consecutive lines with matching indentation. Swap them if neither
# `a` appears in b's expression nor `b` in a's.

_VAL_LINE_RE = re.compile(r"^(?P<indent>[ \t]+)val\s+(?P<name>\w+)(?:\s*:\s*[^=]+)?\s*=\s*(?P<rhs>.+)$")

def apply_t3_stmt_swap(text: str, rng: random.Random, max_swaps: int = 5) -> tuple[str, int]:
    lines = text.split("\n")
    swaps = 0
    i = 0
    while i < len(lines) - 1 and swaps < max_swaps:
        m1 = _VAL_LINE_RE.match(lines[i])
        m2 = _VAL_LINE_RE.match(lines[i + 1])
        if m1 and m2 and m1.group("indent") == m2.group("indent"):
            name1, rhs1 = m1.group("name"), m1.group("rhs")
            name2, rhs2 = m2.group("name"), m2.group("rhs")
            independent = (
                not re.search(rf"\b{name1}\b", rhs2) and
                not re.search(rf"\b{name2}\b", rhs1)
            )
            if independent and rng.random() < 0.5:
                lines[i], lines[i + 1] = lines[i + 1], lines[i]
                swaps += 1
                i += 2
                continue
        i += 1

    return "\n".join(lines), swaps


# ── orchestration ─────────────────────────────────────────────────────────

def main() -> int:
    ap = argparse.ArgumentParser(description="Safe pattern-based Kotlin mutations.")
    ap.add_argument("--seed", help="Override gray.seed.")
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--only", choices=["t1", "t2", "t3", "all"], default="all",
                    help="Run only a specific transform.")
    args = ap.parse_args()

    seed = read_seed(args.seed)
    files = eligible_files()

    tally = {
        "files_eligible": len(files),
        "t1_files_changed": 0,
        "t1_methods_added": 0,
        "t2_files_changed": 0,
        "t2_conversions": 0,
        "t3_files_changed": 0,
        "t3_swaps": 0,
    }

    for path in files:
        rel = path.relative_to(REPO).as_posix()
        # Always work from the snapshot — otherwise T3 swaps flip-flop on
        # every rerun (a swap is its own inverse).
        original = snapshot_kt(path, apply=args.apply)
        text = original

        if args.only in ("t1", "all"):
            rng = rng_for(seed, f"ast:t1:{rel}")
            new_text, added = apply_t1_decoys(text, rng)
            if added:
                tally["t1_files_changed"] += 1 if new_text != text else 0
                tally["t1_methods_added"] += added
                text = new_text

        if args.only in ("t2", "all"):
            new_text, count = apply_t2_return_if(text)
            if count:
                tally["t2_files_changed"] += 1 if new_text != text else 0
                tally["t2_conversions"] += count
                text = new_text

        if args.only in ("t3", "all"):
            rng = rng_for(seed, f"ast:t3:{rel}")
            new_text, count = apply_t3_stmt_swap(text, rng)
            if count:
                tally["t3_files_changed"] += 1 if new_text != text else 0
                tally["t3_swaps"] += count
                text = new_text

        if args.apply and text != path.read_text(encoding="utf-8"):
            path.write_text(text, encoding="utf-8")

    print("═══ AST MUTATIONS ═══")
    print(f"seed prefix          : {seed[:10]}…")
    print(json.dumps(tally, indent=2))
    if not args.apply:
        print("\n(plan only — pass --apply.)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
