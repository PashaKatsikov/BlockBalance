"""Renders the current gameplay geometry with the real shipped sprites.

This is a deterministic desktop check for scale and crane rigging. It mirrors
StageMetrics and StagePainter closely enough to expose clipping, extra ropes,
bad strap attachment and HUD distortion without requiring an Android device.

Usage:
    python tools/render_gameplay_preview.py
"""

from __future__ import annotations

import math
import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageOps

ROOT = Path(__file__).resolve().parents[1]
METRICS = ROOT / "app/src/main/kotlin/bp/goalsgames/blockbalance/stage/StageMetrics.kt"
OUT = ROOT / "build_preview/gameplay-preview.png"

WIDTH = 472
HEIGHT = 1024
WAVE = 0.72  # Near the side of travel, where rigging errors are easiest to see.


def metric(name: str) -> float:
    text = METRICS.read_text(encoding="utf-8")
    match = re.search(rf"const val {name}\s*=\s*([0-9.]+)f", text)
    if not match:
        raise RuntimeError(f"Could not read {name} from {METRICS}")
    return float(match.group(1))


def asset(path: str) -> Image.Image:
    candidates = (ROOT / "assets" / path, ROOT / "app/src/main/assets" / path)
    for candidate in candidates:
        if candidate.exists():
            return Image.open(candidate).convert("RGBA")
    raise FileNotFoundError(path)


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    names = (
        "C:/Windows/Fonts/arialbd.ttf" if bold else "C:/Windows/Fonts/arial.ttf",
        "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf",
    )
    for name in names:
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def paste_rect(canvas: Image.Image, sprite: Image.Image, box: tuple[float, float, float, float]) -> None:
    left, top, right, bottom = (round(value) for value in box)
    if right <= left or bottom <= top:
        return
    resized = sprite.resize((right - left, bottom - top), Image.Resampling.LANCZOS)
    canvas.alpha_composite(resized, (left, top))


def paste_centered(canvas: Image.Image, sprite: Image.Image, x: float, y: float) -> None:
    canvas.alpha_composite(sprite, (round(x - sprite.width / 2), round(y - sprite.height / 2)))


def rounded(
    draw: ImageDraw.ImageDraw,
    box: tuple[int, int, int, int],
    fill: str,
    outline: str | None = None,
    radius: int = 10,
) -> None:
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=1)


def centered_text(
    draw: ImageDraw.ImageDraw,
    box: tuple[int, int, int, int],
    text: str,
    size: int,
    fill: str,
    bold: bool = True,
) -> None:
    face = font(size, bold)
    bounds = draw.textbbox((0, 0), text, font=face)
    width = bounds[2] - bounds[0]
    height = bounds[3] - bounds[1]
    x = (box[0] + box[2] - width) / 2
    y = (box[1] + box[3] - height) / 2 - bounds[1]
    draw.text((x, y), text, font=face, fill=fill)


def render() -> Image.Image:
    world_width = metric("WORLD_WIDTH")
    min_world_height = metric("MIN_WORLD_HEIGHT")
    street_below = metric("STREET_BELOW")
    block_width = metric("BLOCK_WIDTH")
    base_width = metric("BASE_WIDTH")
    pivot_above = metric("PIVOT_ABOVE")
    rope_length = metric("HOOK_RADIUS")
    hook_width = metric("HOOK_WIDTH")
    hook_height = metric("HOOK_HEIGHT")
    strap_drop = metric("STRAP_DROP")
    max_sway_x = metric("MAX_SWAY_X")
    max_hook_tilt = metric("MAX_HOOK_TILT_RAD")
    deck_fraction = metric("DECK_FRACTION")

    ppu = min(WIDTH / world_width, HEIGHT / min_world_height)
    half_width = WIDTH / 2
    half_height = HEIGHT / 2
    viewport_half_height = half_height / ppu
    deck_world = viewport_half_height * 2 * deck_fraction
    camera_y = street_below + deck_world - viewport_half_height
    pivot_y = camera_y - pivot_above

    def sx(world_x: float) -> float:
        return half_width + world_x * ppu

    def sy(world_y: float) -> float:
        return half_height + (world_y - camera_y) * ppu

    sky = asset("gameplay/backround.webp")
    canvas = ImageOps.fit(sky, (WIDTH, HEIGHT), method=Image.Resampling.LANCZOS)

    # A few real clouds, at fixed locations so previews are reproducible.
    cloud = asset("gameplay/cloud_asset_02.webp")
    for x, y, scale, opacity in ((75, 210, 0.55, 145), (380, 430, 0.42, 125)):
        sprite = cloud.copy()
        sprite.thumbnail((round(WIDTH * scale), round(HEIGHT * 0.18)), Image.Resampling.LANCZOS)
        sprite.putalpha(sprite.getchannel("A").point(lambda value: value * opacity // 255))
        paste_centered(canvas, sprite, x, y)

    street = asset("gameplay/start_bg_asset_same_visual.webp")
    street_width = max(world_width, WIDTH / ppu) * 1.02
    street_height = street_width / (street.width / street.height)
    street_bottom = street_below
    street_center = street_bottom - street_height / 2
    paste_rect(
        canvas,
        street,
        (
            sx(-street_width / 2),
            sy(street_center - street_height / 2),
            sx(street_width / 2),
            sy(street_center + street_height / 2),
        ),
    )

    base = asset("derived/stack_base.png")
    base_height = base_width / (base.width / base.height)
    paste_rect(
        canvas,
        base,
        (
            sx(-base_width / 2),
            sy(-base_height),
            sx(base_width / 2),
            sy(0),
        ),
    )

    block = asset("gameplay/block_asset_03.webp")
    block_height = block_width / (block.width / block.height)
    hoist_x = max_sway_x * WAVE
    hook_tilt = max_hook_tilt * WAVE

    def along_x(distance: float) -> float:
        return hoist_x + distance * 0

    def along_y(distance: float) -> float:
        return pivot_y + distance

    hook_center_x = sx(along_x(rope_length))
    hook_center_y = sy(along_y(rope_length))
    pivot_x = sx(hoist_x)
    pivot_screen_y = sy(pivot_y)

    draw = ImageDraw.Draw(canvas)
    cable_width = max(2, round(ppu * 0.055))
    draw.line((pivot_x, pivot_screen_y, hook_center_x, hook_center_y), fill="#2b2f3a", width=cable_width)

    hook_source = asset("gameplay/hook_asset.webp")
    split = round(hook_source.height * 0.52)
    hook = hook_source.crop((0, split, hook_source.width, hook_source.height))
    hook = hook.resize(
        (round(hook_width * ppu), round(hook_height * ppu)),
        Image.Resampling.LANCZOS,
    )
    hook = hook.rotate(-math.degrees(hook_tilt), expand=True, resample=Image.Resampling.BICUBIC)
    paste_centered(canvas, hook, hook_center_x, hook_center_y)

    hook_tip_distance = rope_length + hook_height / 2
    block_distance = hook_tip_distance + strap_drop + block_height / 2
    block_center_x = sx(along_x(block_distance))
    block_center_y = sy(along_y(block_distance))
    block_top = block_center_y - block_height * ppu / 2
    strap_corner = block_width * 0.32 * ppu
    hook_tip_x = sx(along_x(hook_tip_distance))
    hook_tip_y = sy(along_y(hook_tip_distance))
    draw.line(
        (hook_tip_x, hook_tip_y, block_center_x - strap_corner, block_top),
        fill="#2b2f3a",
        width=cable_width,
    )
    draw.line(
        (hook_tip_x, hook_tip_y, block_center_x + strap_corner, block_top),
        fill="#2b2f3a",
        width=cable_width,
    )
    paste_rect(
        canvas,
        block,
        (
            block_center_x - block_width * ppu / 2,
            block_top,
            block_center_x + block_width * ppu / 2,
            block_center_y + block_height * ppu / 2,
        ),
    )

    # Current compact READY HUD.
    draw.rectangle((0, 0, WIDTH, 40), fill="#101a2ce8")
    centered_text(draw, (8, 4, 40, 36), "☰", 16, "#e8eefc")
    draw.text((WIDTH - 94, 5), "ID: BB000001", font=font(10), fill="#a9bada")
    draw.text((WIDTH - 82, 20), "1 100 CR", font=font(12, True), fill="#e8eefc")
    rounded(draw, (WIDTH - 40, 48, WIDTH - 8, 80), "#2f86e0", "#ffffff66", 16)
    centered_text(draw, (WIDTH - 40, 48, WIDTH - 8, 80), "•••", 9, "#ffffff")

    deck_top = HEIGHT - 102
    draw.rectangle((0, deck_top, WIDTH, HEIGHT), fill="#101a2cf2")
    gap = 5
    pad = 5
    stake_top = deck_top + pad
    stake_bottom = stake_top + 38
    third = (WIDTH - pad * 2 - gap * 2) / 3
    all_in = (pad, stake_top, round(pad + third), stake_bottom)
    stepper = (round(pad + third + gap), stake_top, round(pad + third * 2 + gap), stake_bottom)
    double = (round(pad + third * 2 + gap * 2), stake_top, WIDTH - pad, stake_bottom)
    rounded(draw, all_in, "#2374c8", "#ffffff55", 8)
    rounded(draw, stepper, "#060b16", "#2b3f66", 8)
    rounded(draw, double, "#2374c8", "#ffffff55", 8)
    centered_text(draw, all_in, "ALL IN", 12, "#ffffff")
    centered_text(draw, stepper, "−     100     +", 15, "#ffffff")
    centered_text(draw, double, "x2", 12, "#ffffff")

    build_box = (pad, stake_bottom + gap, WIDTH - pad, HEIGHT - pad)
    build = asset("gameplay/button_asset.webp")
    build = ImageOps.fit(
        build,
        (build_box[2] - build_box[0], build_box[3] - build_box[1]),
        method=Image.Resampling.LANCZOS,
    )
    canvas.alpha_composite(build, (build_box[0], build_box[1]))

    # Visible diagnostic dots are outside the production UI.
    diagnostic = ImageDraw.Draw(canvas)
    diagnostic.ellipse(
        (hook_tip_x - 3, hook_tip_y - 3, hook_tip_x + 3, hook_tip_y + 3),
        fill="#ff3b30",
    )
    diagnostic.text((8, 84), "TECH PREVIEW · red dot = hook tip", font=font(11, True), fill="#ffffff")
    return canvas


if __name__ == "__main__":
    OUT.parent.mkdir(parents=True, exist_ok=True)
    render().convert("RGB").save(OUT, quality=95)
    print(OUT)
