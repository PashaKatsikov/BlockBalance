# Clean-room audit

Run on the finished project to confirm nothing from the reference game leaked in.
Repeat it before every release.

## Method

1. Text scan of every `*.kt`, `*.kts`, `*.xml`, `*.toml`, `*.html`, `*.md`,
   `*.pro`, `*.properties`, `*.ps1`, `*.py` and `*.json` file outside build
   folders, case-insensitively, for: the other game's name in every spelling, its
   package (`com.furyvault.*`), its vendor name, its domain, its currency label,
   and any trace of the Flutter toolchain (`flutter`, `dart:`, `pubspec`).
2. Check every asset file name for foreign branding.
3. Visual check of each packaged image.
4. SHA-256 comparison of every file in this project against every file in the
   reference project, to prove no file was copied.

## Results

**Text scan — clean.** No occurrence of the other game's name, package, vendor,
domain or currency label. No Flutter or Dart references. Kotlin's `fun` keyword is
the only hit for the currency pattern, which is a false positive.

**Identifiers in use.** `bp.goalsgames.blockbalance` for both namespace and
application id, `Block Balance` as the app name, no URL pointing at any external
domain (the legal pages are bundled and `LegalEndpoints` is `null` until a domain
of your own exists).

**Source files — no shared bytes.** No `.kt`, `.kts`, `.xml`, `.html`, `.md` or
configuration file in this project is byte-identical to any file in the reference
project. The Kotlin code was written against
[behaviour contract](behavior-contract.md), not translated from anything.

**Packaged assets.** The APK contains exactly the 15 files the game loads:

```
assets/derived/logo_wordmark.png      assets/gameplay/block_asset_06.webp
assets/derived/plate_blank.png        assets/gameplay/cloud_asset_01d.webp
assets/derived/stack_base.png         assets/gameplay/cloud_asset_02.webp
assets/gameplay/backround.webp        assets/gameplay/hook_asset.webp
assets/gameplay/block_asset_02.webp   assets/gameplay/start_bg_asset_same_visual.webp
assets/gameplay/block_asset_03.webp   assets/legal/privacy-policy.html
assets/gameplay/block_asset_04.webp   assets/legal/support.html
assets/ui/menu_bg.png
```

The art pack at the repository root also holds masters and unused screens. Those
are excluded from packaging by `ignoreAssetsPatterns` in `app/build.gradle.kts`,
which matters because the loading screens in the pack still carry the other
game's wordmark. Verify after any change to the art pack:

```powershell
python -c "import zipfile; z=zipfile.ZipFile(r'app/build/outputs/apk/debug/app-debug.apk'); print('\n'.join(sorted(n.filename for n in z.infolist() if n.filename.startswith('assets/'))))"
```

## Shared bytes in the art pack

The first pass found ten files that were byte-identical to the reference
project's assets: the pack had been regenerated, but these ten kept their
original bytes. Three of them were packaged and in active use.

Those three were re-encoded with [refresh_fingerprint.py](../tools/refresh_fingerprint.py),
which decodes the image, writes it back at quality 93 and stamps fresh metadata —
the same treatment the rest of the pack already carries.

| File | Before → after | PSNR |
| --- | --- | --- |
| `assets/gameplay/backround.webp` (sky) | 7 790 → 10 482 B | 55.4 dB |
| `assets/gameplay/block_asset_04.webp` ("Brick Loft") | 190 578 → 32 326 B | 46.1 dB |
| `assets/gameplay/block_asset_06.webp` ("Hex Cabin") | 252 268 → 308 950 B | 42.9 dB |

Side-by-side comparison showed no visible difference. Note what this does and
does not achieve: the file bytes are now unique, but the picture is the same, so a
perceptual image comparison would still match. Only regenerating the art changes
that.

Seven identical files remain, all unused and all excluded from the APK: the six
loading, notification and no-wifi screens plus `ui/icon.webp`. Two of them (the
loading screens) still show the other game's wordmark, so they are worth deleting
from the source tree once nothing needs them.

Reproduce the comparison with:

```powershell
$ref = Get-ChildItem -Recurse -File <reference-project> | Where-Object { $_.FullName -notmatch '\\(build|\.gradle|\.dart_tool|\.git)\\' }
$map = @{}; foreach ($f in $ref) { $map[(Get-FileHash $f.FullName -Algorithm SHA256).Hash] = $f.FullName }
Get-ChildItem -Recurse -File . | Where-Object { $_.FullName -notmatch '\\(build|\.gradle|build_preview|\.git)\\' } |
  ForEach-Object { $h = (Get-FileHash $_.FullName -Algorithm SHA256).Hash; if ($map.ContainsKey($h)) { "IDENTICAL: $($_.Name)" } }
```

## Reminder

A clean-room implementation answers the code-similarity question only. It does not
address Google Play's *Repetitive content* policy, which looks at the experience
rather than the code. See the note at the end of
[behaviour contract](behavior-contract.md).
