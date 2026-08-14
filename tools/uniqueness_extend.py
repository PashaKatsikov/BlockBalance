#!/usr/bin/env python3
# =============================================================================
#  tools/uniqueness_extend.py — second-tier per-project uniqueness pass.
#
#  Runs AFTER `gradlew graySeed` + `rebrand.py`. Where `rebrand.py` rotates the
#  SHAPE of the code (package + class + folder names) and `build.gradle.kts`
#  derives runtime identifiers from `gray.seed`, THIS tool covers the tail of
#  Play-clustering vectors that neither of them touches:
#
#    - AndroidManifest.xml permission + activity ordering
#    - strings.xml key ordering + decoy strings
#    - colors.xml RGB channel shift (+/- 2, imperceptible)
#    - Dead-code utility classes in a decoy Kotlin package
#    - Decoy BuildConfig feature flags
#    - No-op decoy calls around the SDK init in the Application class
#    - Dependency block ordering in app/build.gradle.kts
#
#  The tool is deterministic: same seed produces the same manifest across runs.
#  It is idempotent: rerunning with the same seed writes the same output, and
#  rerunning with a DIFFERENT seed rewrites the marked regions cleanly.
#
#  Marker system:
#     // UNIQUE:<TAG>:BEGIN
#     ... generated content ...
#     // UNIQUE:<TAG>:END
#  Everything between these markers is owned by this tool; hand edits inside
#  will be lost on the next run. Everything outside the markers is preserved
#  verbatim.
#
#  Usage:
#     python tools/uniqueness_extend.py           # plan only, no changes
#     python tools/uniqueness_extend.py --apply
#     python tools/uniqueness_extend.py --apply --seed <override>
# =============================================================================
from __future__ import annotations

import argparse
import hashlib
import io
import json
import random
import re
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path

# UTF-8 stdout for Windows consoles.
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except AttributeError:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

REPO = Path(__file__).resolve().parents[1]
APP = REPO / "app"
SRC_MAIN = APP / "src" / "main"
JAVA_ROOT = SRC_MAIN / "java"

MARKER_TAGS = {
    "buildconfig_decoys": "UNIQUE:BUILDCONFIG_DECOYS",
    "orbitapp_pre":       "UNIQUE:INIT_DECOYS_PRE",
    "orbitapp_post":      "UNIQUE:INIT_DECOYS_POST",
    "gradle_deps":        "UNIQUE:GRADLE_DEPS_SHUFFLE",
    "strings_decoys":     "UNIQUE:STRINGS_DECOYS",
    "decoy_kotlin":       "UNIQUE:DECOY_KOTLIN_PKG",
}

# =============================================================================
# Word banks — decoy strings, class names, method names
# =============================================================================

DECOY_STRING_POOL = [
    ("hint_swipe",         "Swipe to explore your progress"),
    ("hint_tap",           "Tap a card to reveal details"),
    ("hint_hold",          "Hold to review your last streak"),
    ("dialog_confirm",     "Are you sure?"),
    ("dialog_cancel",      "Not now"),
    ("dialog_accept",      "Continue"),
    ("empty_history",      "Nothing here yet — play a round"),
    ("empty_shelf",        "Your collection starts empty"),
    ("empty_ledger",       "Nothing banked this week"),
    ("toast_saved",        "Saved"),
    ("toast_copied",       "Copied to clipboard"),
    ("toast_reset",        "Progress reset"),
    ("label_daily",        "Daily reward"),
    ("label_weekly",       "Weekly summary"),
    ("label_lifetime",     "Lifetime bests"),
    ("badge_new",          "NEW"),
    ("badge_hot",          "HOT"),
    ("badge_rare",         "RARE"),
    ("badge_gifted",       "GIFTED"),
    ("nav_previous",       "Previous"),
    ("nav_next",           "Next"),
    ("nav_finish",         "Finish"),
    ("chip_all",           "All"),
    ("chip_owned",         "Owned"),
    ("chip_locked",        "Locked"),
    ("units_credits",      "cr"),
    ("units_multiplier",   "x"),
    ("units_percent",      "%%"),  # %% because strings.xml wants a single %
    ("share_message",      "I stacked to floor %1$d — beat that."),
    ("share_title",        "Share result"),
    ("crash_generic",      "Something went sideways. Try again."),
    ("crash_offline",      "The link dropped mid-move."),
    ("crash_storage",      "Local storage looks tight."),
    ("credit_footer",      "Made with warm winters"),
    ("changelog_intro",    "What's new"),
]

DECOY_CLASS_STEMS = [
    ("TelemetryBucket",     "logging"),
    ("FrameRateGovernor",   "playback"),
    ("PayloadStamper",      "wire"),
    ("HandshakeCache",      "wire"),
    ("EntropySieve",        "random"),
    ("PressureQueue",       "scheduling"),
    ("RipeningWindow",      "scheduling"),
    ("PebbleLedger",        "logging"),
    ("SlateCorral",         "playback"),
    ("BreakerTally",        "random"),
    ("PlumeCatalogue",      "logging"),
    ("MoltenBufferPool",    "playback"),
    ("SolsticeCounter",     "random"),
    ("HarborRegistry",      "wire"),
    ("CanopyOverlay",       "playback"),
]

DECOY_METHOD_VERBS = [
    "warm", "prepare", "seal", "prime", "settle", "graze", "trickle",
    "unspool", "cradle", "ripen", "furl", "sway", "peer", "buoy", "cusp",
]

DECOY_BUILDCONFIG_FLAGS = [
    "featureRibbonV2",
    "featureDailyPulse",
    "featureAmberDrops",
    "featureTideChoir",
    "featureQuietMode",
    "featureLegacyBanner",
    "featureCanvasProbe",
    "featureThroatlatchMetric",
    "experimentGraphiteBlend",
    "experimentSilverRail",
    "experimentTrackerTuner",
    "experimentPetalPurge",
]


# =============================================================================
# Seed + RNG
# =============================================================================

def load_seed(explicit: str | None) -> str:
    """Read gray.seed from gray.properties, unless overridden."""
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


def rng_for(seed: str, domain: str) -> random.Random:
    """Domain-separated RNG so each mutator draws from an independent stream."""
    key = hashlib.sha256(f"{domain}|{seed}".encode("utf-8")).hexdigest()
    return random.Random(int(key[:16], 16))


# =============================================================================
# Marker helpers
# =============================================================================

def marker_pair(tag: str, style: str) -> tuple[str, str]:
    """
    Build BEGIN / END marker lines for the given file style.
      style: 'kt' | 'gradle' | 'xml' | 'gitignore'
    """
    if style in ("kt", "gradle"):
        return f"// {tag}:BEGIN", f"// {tag}:END"
    if style == "xml":
        return f"<!-- {tag}:BEGIN -->", f"<!-- {tag}:END -->"
    if style == "gitignore":
        return f"# {tag}:BEGIN", f"# {tag}:END"
    raise ValueError(f"unknown marker style {style!r}")


def splice_marked(text: str, tag: str, style: str, body: str) -> str:
    """
    Replace the region between BEGIN/END markers with `body`. If markers are
    absent, appends them (plus body) at the end of the file. `body` should NOT
    include the markers themselves.
    """
    begin, end = marker_pair(tag, style)
    pattern = re.compile(
        re.escape(begin) + r".*?" + re.escape(end),
        re.DOTALL,
    )
    replacement = f"{begin}\n{body.rstrip()}\n{end}"
    if pattern.search(text):
        return pattern.sub(replacement, text)
    trailer = "" if text.endswith("\n") else "\n"
    return f"{text}{trailer}\n{replacement}\n"


# =============================================================================
# Snapshot / restore for files whose mutation is not marker-bounded.
#
# `colors.xml` and `AndroidManifest.xml` are rewritten in place — the mutation
# does not leave BEGIN/END markers behind. Re-running would cumulatively drift
# them (colours would shift by ±2 per run instead of ±2 total). To keep the
# tool idempotent for those files, we snapshot the pre-mutation content on the
# first `--apply` and restore it before every subsequent mutation.
# =============================================================================

def snapshot_or_restore(path: Path, apply: bool) -> str:
    """
    Returns the text to feed the mutator. On first apply this is the file's
    current content; on subsequent applies it is whatever was saved on the
    first run, so mutations always compute against the same input.
    """
    snap_dir = REPO / ".uniqueness" / "originals"
    rel = path.relative_to(REPO)
    snap = snap_dir / rel

    if snap.exists():
        return snap.read_text(encoding="utf-8")

    current = path.read_text(encoding="utf-8")
    if apply:
        snap.parent.mkdir(parents=True, exist_ok=True)
        snap.write_text(current, encoding="utf-8")
    return current


# =============================================================================
# AndroidManifest.xml — permission + activity ordering
# =============================================================================

def mutate_manifest(rng: random.Random, apply: bool) -> tuple[int, str]:
    """Shuffle <uses-permission> and <activity> siblings, keeping the launcher
    activity first among activities so the launcher intent-filter stays
    obviously first when someone reads the file."""
    path = SRC_MAIN / "AndroidManifest.xml"
    original = snapshot_or_restore(path, apply)

    def shuffle_block(text: str, tag: str, keep_first_if_launcher: bool) -> str:
        pattern = re.compile(
            rf"(?:[ \t]*<{tag}\b[^>]*?/>[ \t]*\n)"
            rf"|(?:[ \t]*<{tag}\b.*?</{tag}>[ \t]*\n)",
            re.DOTALL,
        )
        matches = list(pattern.finditer(text))
        if len(matches) < 2:
            return text

        blocks = [m.group(0) for m in matches]
        # Anchor: keep the first launcher-tagged activity in slot 0 so the file
        # still reads top-down: launcher, then the rest reshuffled.
        anchor_index = 0
        if keep_first_if_launcher:
            for i, b in enumerate(blocks):
                if "category.LAUNCHER" in b:
                    anchor_index = i
                    break
        anchor = blocks[anchor_index]
        rest = [b for i, b in enumerate(blocks) if i != anchor_index]
        rng.shuffle(rest)
        new_blocks = [anchor] + rest

        # Splice: cut everything from the first match start to the last match
        # end, then paste the reshuffled blocks in the same slot.
        start = matches[0].start()
        end = matches[-1].end()
        return text[:start] + "".join(new_blocks) + text[end:]

    updated = original
    updated = shuffle_block(updated, "uses-permission", keep_first_if_launcher=False)
    updated = shuffle_block(updated, "activity", keep_first_if_launcher=True)

    changed = int(updated != original)
    return changed, updated if changed else original


# =============================================================================
# strings.xml — decoys + key reorder
# =============================================================================

def mutate_strings(rng: random.Random) -> tuple[int, str, list[str]]:
    """
    - Reorders <string> keys under a marker so we do not disturb the file's
      resource keys silently; the block between markers is our sandbox.
    - Adds a marker-scoped block of decoys BEFORE the closing </resources>.
    """
    path = SRC_MAIN / "res" / "values" / "strings.xml"
    original = path.read_text(encoding="utf-8")

    decoy_count = rng.randint(10, 20)
    picks = list(DECOY_STRING_POOL)
    rng.shuffle(picks)
    picks = picks[:decoy_count]
    # Randomize order of the picks themselves too.
    rng.shuffle(picks)

    body_lines = [f'    <string name="{k}">{v}</string>' for k, v in picks]
    body = "\n".join(body_lines)

    # Splice inside <resources>...</resources>: insert marker block right
    # before </resources>.
    tag = MARKER_TAGS["strings_decoys"]
    begin, end = marker_pair(tag, "xml")
    marker_block = f"    {begin}\n{body}\n    {end}"

    # If markers exist, replace; else insert before </resources>.
    pattern = re.compile(
        r"[ \t]*" + re.escape(begin) + r".*?" + re.escape(end),
        re.DOTALL,
    )
    if pattern.search(original):
        updated = pattern.sub(marker_block, original)
    else:
        updated = re.sub(
            r"(</resources>)",
            marker_block + r"\n\1",
            original,
        )

    changed = int(updated != original)
    added_keys = [k for k, _ in picks]
    return changed, updated if changed else original, added_keys


# =============================================================================
# colors.xml — ±2 RGB shift
# =============================================================================

def mutate_colors(rng: random.Random, apply: bool) -> tuple[int, str, list[tuple[str, str, str]]]:
    """
    Shifts every color channel by a signed delta in [-2, +2], clamped to
    [0, 255], preserving alpha. Colors listed by name go through as-is if the
    RNG happens to draw a zero delta for every channel.
    """
    path = SRC_MAIN / "res" / "values" / "colors.xml"
    original = snapshot_or_restore(path, apply)

    shifts: list[tuple[str, str, str]] = []

    def shift_channel(byte: int) -> int:
        delta = rng.randint(-2, 2)
        return max(0, min(255, byte + delta))

    def rewrite_color(match: re.Match) -> str:
        name = match.group("name")
        raw = match.group("value").upper().lstrip("#")
        if len(raw) == 8:      # AARRGGBB
            a, r, g, b = raw[0:2], raw[2:4], raw[4:6], raw[6:8]
        elif len(raw) == 6:    # RRGGBB
            a, r, g, b = "", raw[0:2], raw[2:4], raw[4:6]
        else:
            return match.group(0)
        new_r = shift_channel(int(r, 16))
        new_g = shift_channel(int(g, 16))
        new_b = shift_channel(int(b, 16))
        new_hex = "#" + a + f"{new_r:02X}{new_g:02X}{new_b:02X}"
        shifts.append((name, "#" + a + f"{int(r,16):02X}{int(g,16):02X}{int(b,16):02X}", new_hex))
        prefix = match.group(0)[: match.group(0).index(match.group("value"))]
        suffix = match.group(0)[match.group(0).index(match.group("value")) + len(match.group("value")):]
        return prefix + new_hex + suffix

    pattern = re.compile(
        r'<color\s+name="(?P<name>[^"]+)"\s*>(?P<value>#[0-9A-Fa-f]{6,8})</color>'
    )
    updated = pattern.sub(rewrite_color, original)
    changed = int(updated != original)
    return changed, updated if changed else original, shifts


# =============================================================================
# app/build.gradle.kts — decoy BuildConfig flags + dependency shuffling
# =============================================================================

def mutate_gradle(rng: random.Random) -> tuple[int, str, list[str], int]:
    """
    Two independent edits inside app/build.gradle.kts:
      1. Insert 4-8 decoy BuildConfig booleans inside defaultConfig, framed
         by BUILDCONFIG_DECOYS markers.
      2. Reorder the top-level `implementation(...)` / test-family lines of the
         dependencies { } block, framed by GRADLE_DEPS_SHUFFLE markers.
    Returns (changed?, new_text, decoy_flag_names, deps_shuffled_count).
    """
    path = APP / "build.gradle.kts"
    original = path.read_text(encoding="utf-8")

    # ── (1) decoy BuildConfig flags ────────────────────────────────────────
    n_flags = rng.randint(4, 8)
    pool = list(DECOY_BUILDCONFIG_FLAGS)
    rng.shuffle(pool)
    flags = pool[:n_flags]
    rng.shuffle(flags)

    flag_lines = []
    for name in flags:
        # boolean, string, or int — one of each variety keeps the block plausible.
        kind = rng.choice(["boolean", "int", "String"])
        symbol = "_".join(re.findall(r"[A-Z]?[a-z0-9]+", name)).upper()
        if kind == "boolean":
            value = rng.choice(["true", "false"])
            flag_lines.append(f'        buildConfigField("boolean", "{symbol}", "{value}")')
        elif kind == "int":
            value = str(rng.randint(1, 32))
            flag_lines.append(f'        buildConfigField("int", "{symbol}", "{value}")')
        else:
            # String values need the quote characters embedded in the Kotlin
            # source literal, i.e. the field goes in as: "\"literal\"".
            literal = "".join(rng.choices("abcdefghijklmnopqrstuvwxyz0123456789", k=rng.randint(5, 9)))
            flag_lines.append(f'        buildConfigField("String", "{symbol}", "\\"{literal}\\"")')

    tag_flags = MARKER_TAGS["buildconfig_decoys"]
    begin, end = marker_pair(tag_flags, "gradle")
    flag_block = f"        {begin}\n" + "\n".join(flag_lines) + f"\n        {end}"

    pattern_flags = re.compile(
        r"[ \t]*" + re.escape(begin) + r".*?" + re.escape(end),
        re.DOTALL,
    )
    if pattern_flags.search(original):
        step1 = pattern_flags.sub(flag_block, original)
    else:
        # Insert just before the final `}` that closes defaultConfig { … }.
        # We anchor on the ALLOWED_HOSTS line since it is the last buildConfigField.
        insertion_anchor = re.search(
            r'(buildConfigField\("String",\s*"ALLOWED_HOSTS".*?\n)',
            original,
        )
        if not insertion_anchor:
            step1 = original  # can't find anchor; skip flags
        else:
            idx = insertion_anchor.end()
            step1 = original[:idx] + "\n" + flag_block + "\n" + original[idx:]

    # ── (2) dependency block shuffle ───────────────────────────────────────
    tag_deps = MARKER_TAGS["gradle_deps"]
    begin_d, end_d = marker_pair(tag_deps, "gradle")

    deps_match = re.search(r"dependencies\s*\{([\s\S]*?)\n\}", step1)
    deps_count = 0
    step2 = step1
    if deps_match:
        body = deps_match.group(1)

        # Take lines that are safe to reorder: they start with implementation,
        # api, testImplementation, debugImplementation, androidTestImplementation,
        # or ksp/kapt. Skip lines with `platform(` (BOM must precede its
        # consumers). Skip nested blocks.
        shuffle_prefixes = (
            "implementation(",
            "api(",
            "testImplementation(",
            "debugImplementation(",
            "androidTestImplementation(",
            "runtimeOnly(",
            "compileOnly(",
            "ksp(",
            "kapt(",
        )
        # Extract line-by-line so comments and blank lines stay put.
        lines = body.split("\n")
        movable_indices = []
        for i, ln in enumerate(lines):
            stripped = ln.strip()
            if not stripped or stripped.startswith("//") or stripped.startswith("/*"):
                continue
            if "platform(" in stripped:
                continue
            if any(stripped.startswith(p) for p in shuffle_prefixes):
                movable_indices.append(i)

        if len(movable_indices) >= 4:
            movable_lines = [lines[i] for i in movable_indices]
            rng.shuffle(movable_lines)
            for idx, new_ln in zip(movable_indices, movable_lines):
                lines[idx] = new_ln
            new_body = "\n".join(lines)

            # On the first run the markers do not exist yet, so wrap the whole
            # dependencies body. On subsequent runs the shuffled lines already
            # sit inside the existing markers (rewritten in-place above), so
            # `new_body` is the final answer and no further wrapping is needed.
            already_has_markers = begin_d in body and end_d in body
            if not already_has_markers:
                new_body = (
                    "\n    " + begin_d + "\n"
                    + new_body.lstrip("\n").rstrip("\n")
                    + "\n    " + end_d + "\n"
                )

            step2 = step1[: deps_match.start(1)] + new_body + step1[deps_match.end(1):]
            deps_count = len(movable_indices)

    changed = int(step2 != original)
    return changed, step2 if changed else original, flags, deps_count


# =============================================================================
# OrbitApp.kt — decoy no-op calls around SDK init
# =============================================================================

def _decoy_call(rng: random.Random) -> str:
    verb = rng.choice(DECOY_METHOD_VERBS)
    tag = "".join(rng.choices("abcdefghjkmnpqrstuvwxyz23456789", k=rng.randint(4, 6)))
    return f"        // decoy: probe {tag}\n        run {{ val {verb}Probe{tag.capitalize()} = System.nanoTime(); {verb}Probe{tag.capitalize()}.hashCode() }}"


def mutate_orbit_app(rng: random.Random) -> tuple[int, str, int, int]:
    path = _find_orbit_app()
    if path is None:
        return 0, "", 0, 0
    original = path.read_text(encoding="utf-8")

    n_pre = rng.randint(2, 4)
    n_post = rng.randint(2, 4)
    pre_lines = "\n".join(_decoy_call(rng) for _ in range(n_pre))
    post_lines = "\n".join(_decoy_call(rng) for _ in range(n_post))

    updated = original
    # Anchor 1: after `super.onCreate()`
    tag_pre = MARKER_TAGS["orbitapp_pre"]
    begin_pre, end_pre = marker_pair(tag_pre, "kt")
    block_pre = f"        {begin_pre}\n{pre_lines}\n        {end_pre}"

    pattern_pre_marked = re.compile(
        r"[ \t]*" + re.escape(begin_pre) + r".*?" + re.escape(end_pre),
        re.DOTALL,
    )
    if pattern_pre_marked.search(updated):
        updated = pattern_pre_marked.sub(block_pre, updated)
    else:
        # Insert right after `super.onCreate()` line.
        insertion = re.search(r"(super\.onCreate\(\)\s*\n)", updated)
        if insertion:
            idx = insertion.end()
            updated = updated[:idx] + "\n" + block_pre + "\n" + updated[idx:]

    # Anchor 2: after the last statement of onCreate — right before `.prime()` call
    # or at end of the method. Use the `trackingDispatch.prime()` line as anchor.
    tag_post = MARKER_TAGS["orbitapp_post"]
    begin_post, end_post = marker_pair(tag_post, "kt")
    block_post = f"        {begin_post}\n{post_lines}\n        {end_post}"

    pattern_post_marked = re.compile(
        r"[ \t]*" + re.escape(begin_post) + r".*?" + re.escape(end_post),
        re.DOTALL,
    )
    if pattern_post_marked.search(updated):
        updated = pattern_post_marked.sub(block_post, updated)
    else:
        insertion = re.search(r"(trackingDispatch\.prime\(\)\s*\n)", updated)
        if insertion:
            idx = insertion.end()
            updated = updated[:idx] + "\n" + block_post + "\n" + updated[idx:]

    changed = int(updated != original)
    return changed, updated if changed else original, n_pre, n_post


def _find_orbit_app() -> Path | None:
    """
    The Application class was rebranded per-project — locate it by scanning
    java/**/*.kt for `class X : OrbitApp()` or `open class OrbitApp`, or the
    theme-renamed successor. Falls back to a filename match.
    """
    for candidate in JAVA_ROOT.rglob("*.kt"):
        text = candidate.read_text(encoding="utf-8", errors="ignore")
        if re.search(r"open\s+class\s+OrbitApp\s*:\s*Application", text):
            return candidate
        # Also accept a theme-renamed superclass placeholder — anything that
        # extends Application AND calls `.prime()` in onCreate is the right file.
        if "trackingDispatch.prime()" in text and "class " in text and "Application" in text:
            return candidate
    return None


# =============================================================================
# Decoy Kotlin utility classes (unreferenced, ProGuard strips in release)
# =============================================================================

def _decoy_class_body(rng: random.Random, stem: str, flavor: str) -> str:
    verbs = list(DECOY_METHOD_VERBS)
    rng.shuffle(verbs)
    verbs = verbs[:rng.randint(3, 5)]

    if flavor == "logging":
        lines = []
        for v in verbs:
            n = rng.randint(1, 5)
            lines.append(
                f"    fun {v}(sample: Long = System.nanoTime()): Long {{\n"
                f"        return (sample xor 0x{rng.getrandbits(32):08X}L) shr {n}\n"
                f"    }}"
            )
        body = "\n\n".join(lines)
        return f"object {stem} {{\n{body}\n}}\n"

    if flavor == "playback":
        lines = []
        for v in verbs:
            lines.append(
                f"    fun {v}(intensity: Float, floor: Float = {rng.random():.3f}f): Float =\n"
                f"        floor + (intensity - floor) * {rng.random():.3f}f"
            )
        body = "\n\n".join(lines)
        return f"class {stem} {{\n{body}\n}}\n"

    if flavor == "wire":
        lines = []
        for v in verbs:
            lines.append(
                f"    fun {v}(payload: ByteArray): Int {{\n"
                f"        var acc = 0x{rng.getrandbits(32):08X}L.toInt()\n"
                f"        for (byte in payload) acc = (acc * 31) xor byte.toInt()\n"
                f"        return acc\n"
                f"    }}"
            )
        body = "\n\n".join(lines)
        return f"object {stem} {{\n{body}\n}}\n"

    if flavor == "scheduling":
        lines = []
        for v in verbs:
            lines.append(
                f"    fun {v}(seedMs: Long): Long =\n"
                f"        (seedMs + {rng.randint(37, 991)}L) % {rng.randint(1_009, 9_991)}L"
            )
        body = "\n\n".join(lines)
        return f"object {stem} {{\n{body}\n}}\n"

    # "random"
    lines = []
    for v in verbs:
        lines.append(
            f"    fun {v}(seed: Long): Long {{\n"
            f"        var x = seed\n"
            f"        x = x xor (x shl 13)\n"
            f"        x = x xor (x ushr 7)\n"
            f"        x = x xor (x shl 17)\n"
            f"        return x and 0x{rng.getrandbits(48):012X}L\n"
            f"    }}"
        )
    body = "\n\n".join(lines)
    return f"object {stem} {{\n{body}\n}}\n"


def generate_decoy_kotlin(rng: random.Random, apply: bool) -> tuple[int, list[str], Path]:
    """
    Creates 5-10 decoy Kotlin utility files in a distinct package. Regenerates
    the directory from scratch each run so mid-run deletions do not leave
    orphans. The directory itself is marker-scoped via a sentinel file.
    """
    # Detect the current app package root by inspecting java/**/*.kt for one
    # of the files whose `package` line names the root — take the shortest
    # package that includes at least one Kotlin file (heuristic).
    package_root = _detect_package_root()
    if package_root is None:
        return 0, [], JAVA_ROOT

    decoy_pkg_name = "instrument"
    pkg_dir = JAVA_ROOT.joinpath(*package_root.split("."), decoy_pkg_name)

    # Purge if exists to keep runs idempotent.
    if apply and pkg_dir.exists():
        shutil.rmtree(pkg_dir)

    n_files = rng.randint(5, 10)
    stems = list(DECOY_CLASS_STEMS)
    rng.shuffle(stems)
    stems = stems[:n_files]

    file_names: list[str] = []
    for stem, flavor in stems:
        content = (
            f"// {MARKER_TAGS['decoy_kotlin']}:GENERATED\n"
            f"// Deterministic per-seed decoy; ProGuard strips this in release.\n"
            f"package {package_root}.{decoy_pkg_name}\n\n"
            + _decoy_class_body(rng, stem, flavor)
        )
        if apply:
            pkg_dir.mkdir(parents=True, exist_ok=True)
            (pkg_dir / f"{stem}.kt").write_text(content, encoding="utf-8")
        file_names.append(f"{stem}.kt")

    return len(file_names), file_names, pkg_dir


def _detect_package_root() -> str | None:
    """Return the root Kotlin package that the app uses under java/. Chosen as
    the longest common package prefix among the first few *.kt files."""
    kt_files = list(JAVA_ROOT.rglob("*.kt"))[:10]
    packages = []
    for kt in kt_files:
        m = re.search(r"^package\s+([\w.]+)", kt.read_text(encoding="utf-8", errors="ignore"), re.MULTILINE)
        if m:
            packages.append(m.group(1).split("."))
    if not packages:
        return None
    common = packages[0]
    for parts in packages[1:]:
        common = [a for a, b in zip(common, parts) if a == b]
    if not common:
        return None
    return ".".join(common)


# =============================================================================
# .gitignore
# =============================================================================

def ensure_gitignore(apply: bool) -> bool:
    path = REPO / ".gitignore"
    original = path.read_text(encoding="utf-8") if path.exists() else ""
    if ".uniqueness/" in original or ".uniqueness" in original.split():
        return False
    updated = original.rstrip() + "\n\n# Per-project uniqueness manifest — do not commit.\n.uniqueness/\n"
    if apply:
        path.write_text(updated, encoding="utf-8")
    return True


# =============================================================================
# Manifest
# =============================================================================

def write_manifest(payload: dict, seed: str, apply: bool) -> Path:
    directory = REPO / ".uniqueness"
    if apply:
        directory.mkdir(exist_ok=True)
    prefix = hashlib.sha256(seed.encode("utf-8")).hexdigest()[:12]
    out = directory / f"{prefix}.json"
    text = json.dumps(payload, indent=2, ensure_ascii=False)
    if apply:
        out.write_text(text, encoding="utf-8")
    return out


# =============================================================================
# Orchestration
# =============================================================================

def main() -> int:
    ap = argparse.ArgumentParser(description="Extend per-project uniqueness beyond gray.properties + rebrand.py.")
    ap.add_argument("--seed", help="Override gray.seed (otherwise read from gray.properties).")
    ap.add_argument("--apply", action="store_true", help="Write changes; without this flag, prints a plan only.")
    args = ap.parse_args()

    seed = load_seed(args.seed)

    # Independent RNG streams so tweaks to one mutator don't ripple through the
    # rest of the manifest.
    rng_manifest = rng_for(seed, "manifest")
    rng_strings  = rng_for(seed, "strings")
    rng_colors   = rng_for(seed, "colors")
    rng_gradle   = rng_for(seed, "gradle")
    rng_orbit    = rng_for(seed, "orbitapp")
    rng_decoys   = rng_for(seed, "decoys")

    # Plan pass (no writes yet). Snapshot writes happen inside mutate_manifest
    # / mutate_colors only when --apply is set, so the plan pass is truly
    # read-only.
    manifest_changed, manifest_text = mutate_manifest(rng_manifest, apply=args.apply)
    strings_changed, strings_text, decoy_keys = mutate_strings(rng_strings)
    colors_changed, colors_text, color_shifts = mutate_colors(rng_colors, apply=args.apply)
    gradle_changed, gradle_text, flag_names, deps_count = mutate_gradle(rng_gradle)
    orbit_changed, orbit_text, n_pre, n_post = mutate_orbit_app(rng_orbit)
    n_decoys, decoy_files, pkg_dir = generate_decoy_kotlin(rng_decoys, apply=False)
    gi_changed = ensure_gitignore(apply=False)

    print("═══ UNIQUENESS EXTEND ═══")
    print(f"seed prefix        : {seed[:10]}…")
    print(f"AndroidManifest    : {'reshuffled' if manifest_changed else 'no change'}")
    print(f"strings.xml decoys : {len(decoy_keys)} keys")
    print(f"colors.xml shifts  : {len(color_shifts)} entries")
    print(f"build.gradle flags : {len(flag_names)} flags")
    print(f"build.gradle deps  : {deps_count} lines reordered")
    print(f"OrbitApp pre/post  : {n_pre} + {n_post} decoy calls")
    print(f"decoy kotlin files : {n_decoys}")
    print(f"gitignore updated  : {gi_changed}")

    if not args.apply:
        print("\n(plan only — pass --apply to write.)")
        return 0

    # Apply pass — write everything.
    if manifest_changed:
        (SRC_MAIN / "AndroidManifest.xml").write_text(manifest_text, encoding="utf-8")
    if strings_changed:
        (SRC_MAIN / "res" / "values" / "strings.xml").write_text(strings_text, encoding="utf-8")
    if colors_changed:
        (SRC_MAIN / "res" / "values" / "colors.xml").write_text(colors_text, encoding="utf-8")
    if gradle_changed:
        (APP / "build.gradle.kts").write_text(gradle_text, encoding="utf-8")
    if orbit_changed:
        orbit_path = _find_orbit_app()
        if orbit_path:
            orbit_path.write_text(orbit_text, encoding="utf-8")
    # Regenerate decoys deterministically.
    generate_decoy_kotlin(rng_for(seed, "decoys"), apply=True)
    ensure_gitignore(apply=True)

    # UNIQUENESS MANIFEST
    payload = {
        "seed_prefix": seed[:10],
        "seed_sha256_short": hashlib.sha256(seed.encode()).hexdigest()[:12],
        "generated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "architecture": "MVVM (Compose) + Application-scoped ServiceLocator",
        "di": "manual (BlockBalanceApp.game extension)",
        "package_root": _detect_package_root(),
        "sdk_init_order": [
            "FirebaseApp.initializeApp",
            "FirebaseAppCheck.installAppCheckProviderFactory",
            "UrlGuard.warnIfMissing",
            "AttrHub.prime",
        ],
        "sdk_decoys_pre": n_pre,
        "sdk_decoys_post": n_post,
        "js_storage_method": "BuildConfig sentinels (JS_SAFE_AREA_SENTINEL, JS_KEYBOARD_SENTINEL, JS_BRIDGE_NAME) — derived deterministically from gray.seed",
        "obfuscation_style": "XOR (CIPHER_VARIANT/MULT/ADD, per-seed) for endpoints; SharedPrefs keys + JS names are per-seed random tokens",
        "manifest_reshuffled": bool(manifest_changed),
        "strings_decoy_keys": decoy_keys,
        "colors_rgb_shifts": [
            {"name": name, "before": before, "after": after}
            for name, before, after in color_shifts
        ],
        "gradle_decoy_flags": flag_names,
        "gradle_deps_shuffled": deps_count,
        "decoy_kotlin_pkg": str(pkg_dir.relative_to(REPO)).replace("\\", "/"),
        "decoy_kotlin_files": decoy_files,
    }

    manifest_path = write_manifest(payload, seed, apply=True)
    print(f"\nUNIQUENESS MANIFEST written to: {manifest_path.relative_to(REPO)}")
    print(json.dumps(payload, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
