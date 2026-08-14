# Publication checklist

Run this before every store upload. Items marked **[auto]** are checked by
`tools/apk_fingerprint.py` (produces `.uniqueness/apk_hashes.json` — do not
commit). Items marked **[manual]** require a human eye and cannot be
delegated to a script. Items marked **[gray-part]** are already covered by the
per-project `gray.properties` + `rebrand.py` + `uniqueness_extend.py` pass, but
must still be reviewed to confirm the derivations landed on the APK.

## Static uniqueness — APK content

- [ ] **[gray-part]** `gray.seed` is a fresh cryptographic seed. Never reused
  across projects. `gradlew graySeed` produced it; the previous value is not
  in git history or in any backup.
- [ ] **[gray-part]** `python tools/uniqueness_extend.py --apply` has been run
  on the current seed. `.uniqueness/<seed_prefix>.json` exists locally.
- [ ] **[gray-part]** `python tools/uniqueness_dictionaries.py --apply` has
  been run. `app/proguard-dict/` contains three text files; each is >100 lines
  and each is different from the same file in previous projects.
- [ ] **[gray-part]** `python tools/uniqueness_pngs.py --apply` has been run.
  PNG masters have unique bit content vs prior builds (compare via
  `sha256sum` on any 3 arbitrary files, they must all differ).
- [ ] **[gray-part]** `python tools/uniqueness_ast.py --apply` has been run
  and the project still builds. Regenerate if a build breaks.
- [ ] **[auto]** `python tools/apk_fingerprint.py <apk-path>` shows the TLSH
  distance to every previously shipped APK is `> 100`. If not, rotate the
  seed and rebuild.

## Fingerprint — build system

- [ ] **[manual]** `applicationId` in `gray.bundleId` does not follow the
  portfolio's obvious pattern (e.g. `com.<company>.<seq>N`). Prefer inverted
  domains that vary per project (`com.<theme>.<verb>` etc.).
- [ ] **[manual]** `versionCode` is not the same integer in every project's
  first release. Start from a randomised base per project.
- [ ] **[manual]** `keystore/keystore.properties` points at a **different**
  keystore file than the previous project. Never reuse a keystore across two
  submissions. Keep the mapping (project → keystore) in an out-of-repo
  ledger, not in git.
- [ ] **[manual]** The Chrome UA `major.build.patch` derived by the build
  differs from at least the last three projects. `gradlew grayReport` prints
  it — the field is `chrome UA`.

## Network / attribution

- [ ] **[gray-part]** `gray.configEndpoint` is a **new** hostname or, at
  minimum, a new subdomain. Do not re-use `config.example.com` across two
  projects — the first-launch POST goes to it, and 200 apps hitting the same
  host is instant clustering.
- [ ] **[gray-part]** `gray.appsFlyerKey` was issued for **this** project.
  Copying a key from another app breaks attribution and clusters the pair.
- [ ] **[gray-part]** `gray.firebaseProject` is a **new** Firebase project.
  `app/google-services.json` was generated for the new project — check
  `project_info.project_id` inside the file matches `gray.firebaseProject`.
- [ ] **[gray-part]** `gray.allowedHosts` is filled in (never ship an empty
  allowlist — the build logs a warning).
- [ ] **[manual]** The WebView payload URLs returned by the config endpoint
  are on hosts none of the other projects use. Trace one call end-to-end.

## R8 / minification

- [ ] **[auto]** Release APK's `META-INF/MANIFEST.MF` and the byte content
  under `resources.arsc` differ from every previously shipped release APK by
  more than 40% of bytes (rough heuristic; TLSH check subsumes this).
- [ ] **[gray-part]** `-repackageclasses` and `-adaptresourcefilenames` are
  active in `proguard-rules.pro` (added by
  `tools/uniqueness_dictionaries.py`).
- [ ] **[manual]** Open the release APK in JADX or apkanalyzer — every class
  under the top-level package uses names picked from the current project's
  class-dictionary.txt (i.e. the R8 mapping.txt drew from the right file).

## Store listing (this is a common ban trigger)

- [ ] **[manual]** Launcher icon perceptual hash (pHash) differs from every
  previous project by more than 15 bits. Use `imagehash` in Python to check:
  `imagehash.phash(Image.open("app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp"))`.
- [ ] **[manual]** Feature graphic (`1024×500`) is not the launcher icon on
  a coloured background. Compose it separately.
- [ ] **[manual]** Screenshots are captured on the actual device build. Do
  not reuse mockups. Resolutions vary between projects when possible.
- [ ] **[manual]** Store description is written from scratch for this game.
  Do not translate or paraphrase the previous project's copy. Length,
  section headings and bullet layout should all differ.
- [ ] **[manual]** Category is chosen by the game's actual mechanic, not by
  what happened to work on the previous submission.
- [ ] **[manual]** Age rating questionnaire answered independently — if the
  content genuinely differs, the answers must too.

## Account / operations

- [ ] **[manual]** Developer account has < 20 active apps. Add a fresh
  account before crossing the line.
- [ ] **[manual]** The IP publishing this submission has not, in the last
  60 days, submitted anything that was subsequently banned or removed.
- [ ] **[manual]** The upload machine (or CI runner) has not itself been the
  origin of a banned submission — clean profile / clean VM if in doubt.
- [ ] **[manual]** Keystore backups are stored **outside** the git repo, on
  encrypted media, with the mapping ledger.

## Post-release

- [ ] Update the portfolio ledger with: bundleId, keystore path, Firebase
  project id, AppsFlyer key, config endpoint host, TLSH hash of the shipped
  APK, and the date. Store the ledger in a place that survives a machine
  wipe. Never in this repo.
