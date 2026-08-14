#!/usr/bin/env python3
# =============================================================================
#  tools/uniqueness_dictionaries.py — per-project R8/ProGuard dictionaries.
#
#  R8 replaces class, method, field and package identifiers with short tokens
#  from `-classobfuscationdictionary`, `-obfuscationdictionary` and
#  `-packageobfuscationdictionary`. When two apps in the portfolio share a
#  dictionary they end up with the same obfuscated shape after minify (`a`,
#  `b`, `c` … in the same order), which is exactly the fingerprint a cluster
#  scan looks at first.
#
#  This tool draws three dictionaries from a per-seed RNG:
#    - class-dictionary.txt   (short PascalCase tokens for class names)
#    - obf-dictionary.txt     (short lowerCamel tokens for methods + fields)
#    - package-dictionary.txt (single-word lowercase tokens for packages)
#
#  All three files live under `app/proguard-dict/` and are wired into
#  `proguard-rules.pro` between UNIQUE:DICTIONARY_WIRING markers so re-runs
#  refresh the dictionaries without disturbing the rest of the file.
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
APP = REPO / "app"
DICT_DIR = APP / "proguard-dict"

# Word banks — chosen so any picked subset stays plausible as an R8-obfuscated
# identifier. Names never repeat inside a single dictionary because R8 wants
# the file to be a set. We enforce that with a python `set` before writing.
STEM_ROOTS_CAMEL = [
    "aurora", "brine", "cinder", "delta", "ember", "fen", "gale", "harbor",
    "iris", "jade", "kelp", "lyra", "moss", "nectar", "onyx", "pyre",
    "quiet", "ridge", "salt", "tide", "umbra", "vale", "wick", "yaw",
    "arbor", "beacon", "clove", "drift", "eddy", "flint", "grove", "haze",
    "ivy", "juno", "kite", "loam", "mote", "nook", "orb", "peat",
    "quill", "reed", "sable", "thorn", "under", "vein", "wither", "yarn",
]
STEM_SUFFIXES = [
    "port", "loom", "chord", "step", "bloom", "fold", "cusp", "vane",
    "hook", "ring", "clasp", "gill", "reef", "shard", "stalk", "brace",
]
PACKAGE_ROOTS = [
    "core", "internal", "runtime", "wire", "hub", "codec", "surface",
    "buffer", "stream", "channel", "beacon", "index", "handoff", "shell",
    "vault", "portal", "cache", "flow", "sync", "bridge", "signal",
]


def rng_for(seed: str, domain: str) -> random.Random:
    key = hashlib.sha256(f"{domain}|{seed}".encode("utf-8")).hexdigest()
    return random.Random(int(key[:16], 16))


def read_seed(explicit: str | None) -> str:
    if explicit:
        return explicit.strip()
    props_file = REPO / "gray.properties"
    if not props_file.exists():
        raise SystemExit("gray.properties not found; run graySeed first.")
    for raw in props_file.read_text(encoding="utf-8").splitlines():
        line = raw.split("#", 1)[0].strip()
        if line.startswith("gray.seed"):
            _, value = line.split("=", 1)
            return value.strip()
    raise SystemExit("gray.seed missing from gray.properties.")


def _tokens(rng: random.Random, count: int, style: str) -> list[str]:
    """Draw `count` unique tokens without repeats.

    style:
      - class   : PascalCase, 3-6 chars (single stem letters, capitalised)
      - member  : lowerCamel, 3-6 chars
      - package : all-lowercase, 3-8 chars
    """
    out: set[str] = set()
    lengths = list(range(3, 7)) if style != "package" else list(range(3, 9))
    attempts = 0
    while len(out) < count and attempts < count * 60:
        attempts += 1
        length = rng.choice(lengths)
        letters = "abcdefghijklmnopqrstuvwxyz"
        token = "".join(rng.choices(letters, k=length))
        if style == "class":
            token = token.capitalize()
        # Filter out tokens that look like Java/Kotlin keywords.
        if token.lower() in _RESERVED:
            continue
        out.add(token)

    # Sprinkle a few themed stems on top so the file passes a quick eyeball
    # inspection (a wall of pure random letters is not credible).
    themed_extras: list[str] = []
    if style == "class":
        stems = [(r.capitalize() + s.capitalize()) for r in STEM_ROOTS_CAMEL for s in STEM_SUFFIXES]
        rng.shuffle(stems)
        themed_extras = stems[: max(count // 6, 6)]
    elif style == "member":
        pairs = [(r + s.capitalize()) for r in STEM_ROOTS_CAMEL for s in STEM_SUFFIXES]
        rng.shuffle(pairs)
        themed_extras = pairs[: max(count // 6, 6)]
    elif style == "package":
        rng.shuffle(PACKAGE_ROOTS)
        themed_extras = PACKAGE_ROOTS[: max(count // 6, 4)]

    for t in themed_extras:
        out.add(t)

    ordered = list(out)
    rng.shuffle(ordered)
    return ordered[:count]


_RESERVED = {
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
    "class", "const", "continue", "default", "do", "double", "else", "enum",
    "extends", "final", "finally", "float", "for", "goto", "if", "implements",
    "import", "instanceof", "int", "interface", "long", "native", "new",
    "null", "package", "private", "protected", "public", "return", "short",
    "static", "strictfp", "super", "switch", "synchronized", "this", "throw",
    "throws", "transient", "try", "void", "volatile", "while", "true", "false",
    "yield", "record", "sealed", "permits", "var", "val", "fun", "object",
    "when", "in", "is", "as", "typealias", "internal", "companion", "init",
    "constructor", "override", "open", "abstract", "vararg",
}


def generate_dictionaries(seed: str, apply: bool) -> dict:
    rng_cls = rng_for(seed, "dict:class")
    rng_mem = rng_for(seed, "dict:member")
    rng_pkg = rng_for(seed, "dict:package")

    class_tokens   = _tokens(rng_cls, 400, "class")
    member_tokens  = _tokens(rng_mem, 600, "member")
    package_tokens = _tokens(rng_pkg, 120, "package")

    files = {
        "class-dictionary.txt":   class_tokens,
        "obf-dictionary.txt":     member_tokens,
        "package-dictionary.txt": package_tokens,
    }

    if apply:
        DICT_DIR.mkdir(exist_ok=True)
        for name, tokens in files.items():
            (DICT_DIR / name).write_text("\n".join(tokens) + "\n", encoding="utf-8")

    return {name: len(tokens) for name, tokens in files.items()}


# =============================================================================
# ProGuard rules wiring
# =============================================================================

DICT_WIRING_TAG = "UNIQUE:DICTIONARY_WIRING"

def wire_proguard(apply: bool) -> tuple[bool, str]:
    """Insert the -*obfuscationdictionary directives + resource-adapt flags
    between UNIQUE:DICTIONARY_WIRING markers in proguard-rules.pro."""
    path = APP / "proguard-rules.pro"
    original = path.read_text(encoding="utf-8")

    body = "\n".join([
        f"# {DICT_WIRING_TAG}:BEGIN",
        "# Per-project R8 dictionaries — generated by tools/uniqueness_dictionaries.py.",
        "# Refresh them by running the script again after `gradlew graySeed`.",
        "-classobfuscationdictionary   proguard-dict/class-dictionary.txt",
        "-obfuscationdictionary        proguard-dict/obf-dictionary.txt",
        "-packageobfuscationdictionary proguard-dict/package-dictionary.txt",
        "",
        "# Rename resources referenced from the manifest / XML so that after",
        "# R8 the file names in the APK do not line up with any prior build.",
        "-adaptresourcefilenames    **.properties,**.xml,**.png,**.webp,**.json,**.html",
        "-adaptresourcefilecontents **.properties,**.xml,**.json,META-INF/MANIFEST.MF",
        "",
        "# Move package hierarchy under a single random root — R8 collapses",
        "# every non-kept package into `a.b.c` etc. from the package dict, so",
        "# two projects share zero package paths after minify.",
        "-repackageclasses",
        "-allowaccessmodification",
        f"# {DICT_WIRING_TAG}:END",
    ])

    pattern = re.compile(
        r"# " + re.escape(DICT_WIRING_TAG) + r":BEGIN.*?# " + re.escape(DICT_WIRING_TAG) + r":END",
        re.DOTALL,
    )

    if pattern.search(original):
        updated = pattern.sub(body, original)
    else:
        # Append at end.
        trailer = "" if original.endswith("\n") else "\n"
        updated = original + trailer + "\n" + body + "\n"

    changed = updated != original
    if apply and changed:
        path.write_text(updated, encoding="utf-8")
    return changed, body


def main() -> int:
    ap = argparse.ArgumentParser(description="Generate per-project R8 dictionaries + wire proguard-rules.pro.")
    ap.add_argument("--seed", help="Override gray.seed.")
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()

    seed = read_seed(args.seed)
    sizes = generate_dictionaries(seed, apply=args.apply)
    proguard_changed, proguard_body = wire_proguard(apply=args.apply)

    print("═══ R8 DICTIONARIES ═══")
    print(f"seed prefix : {seed[:10]}…")
    for name, count in sizes.items():
        print(f"  {name:<24} → {count} tokens")
    print(f"proguard    : {'wired' if proguard_changed else 'already up to date'}")

    if not args.apply:
        print("\n(plan only — pass --apply to write.)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
