# Block Balance — release checklist

Everything below is done from the Play Console; the project itself is already
configured for it.

## Build configuration (already in place)

| Item | Value |
| --- | --- |
| Application id | `bp.goalsgames.blockbalance` (debug installs as `.debug`) |
| compileSdk / targetSdk | 36 |
| minSdk | 24 |
| versionCode / versionName | 1 / `1.0.0` |
| Release build | R8 enabled, resource shrinking enabled, keep rules for kotlinx.serialization |
| Permissions | `INTERNET` (WebView for the hosted legal pages), `VIBRATE` |
| Locales packaged | `en` |

## Before the first upload

1. **Signing.** Create an upload keystore outside the repository and point a
   local `keystore.properties` at it. `.gitignore` already excludes `*.jks`,
   `*.keystore` and `keystore.properties`, and no signing config is committed.
   Enroll in Play App Signing.
2. **Bundle.** Ship `bundleRelease` (AAB), not the APK. Verify it installs and
   launches on a real device from the internal testing track.
3. **Legal pages.** Publish the privacy policy at a public HTTPS URL and put that
   URL in the Play Console. Optionally set `PRIVACY_URL` / `SUPPORT_URL` in
   `LegalEndpoints.kt` so the in-app buttons load the hosted copies; while they
   are `null` the bundled pages are used and nothing points at a dead domain.
   Suggested paths once a domain exists:
   `https://<your-domain>/blockbalance/privacy-policy.html` and
   `https://<your-domain>/blockbalance/support.html` — the bundled files use the
   same names (`privacy-policy.html`, `support.html`).
4. **Support contact.** The Console requires an email address; use the same one
   as in `support.html`.

## Data Safety form

The app collects and transmits nothing, so the form is short:

- Data collected: **none**. No identifiers, no location, no contacts, no usage
  analytics, no crash reporting SDK.
- Data shared: **none**.
- Data encrypted in transit: not applicable (no data leaves the device).
- Deletion request mechanism: not applicable; "Reset progress" in Settings clears
  all local data.
- Account required: no.
- The `INTERNET` permission is only used to load the hosted legal pages in a
  WebView with JavaScript and DOM storage disabled. Say so if asked.

## Content and policy answers

- Ads: none. Do not tick "contains ads".
- In-app purchases: none.
- Target audience and content rating: the game uses a local, non-purchasable,
  non-cashable currency with no payout. Answer the *simulated gambling* questions
  honestly and avoid casino wording in the listing.
- Government apps / financial features / health: not applicable.

## Listing

Write the title, short description, full description and screenshots from
scratch. This is the part of the submission most exposed to the *Repetitive
content* policy — see the Google Play note in
[behaviour contract](behavior-contract.md). Do not reuse copy or screenshots from
any other title, and do not describe the game as a version of another game.

Assets needed: 512×512 icon, 1024×500 feature graphic, at least two phone
screenshots per orientation you support.

## Pre-launch checks

- `./gradlew test lint assembleDebug assembleRelease` — green.
- `./gradlew connectedAndroidTest` on an unlocked device.
- Play Console pre-launch report: no crashes, no accessibility blockers.
- Manual: background and return mid-run, long stack, date change, airplane mode,
  small and large screens, a few minutes of continuous play.
