# Block Balance — behaviour contract

This document is the specification the Kotlin implementation is written against.
It describes *what* the game does, in numbers, so the behaviour can be reviewed
and re-verified without reading the whole codebase.

Nothing here is derived from another project's source code. The rules were
written as a behavioural spec first, then implemented from that spec.

- Application name: **Block Balance**
- Application id: `bp.goalsgames.blockbalance`
- min SDK 24, target/compile SDK 36, Kotlin/JVM 17
- Single activity (`StageActivity`), Compose for screens, `SurfaceView` + `Canvas`
  for the yard
- No audio, no ads, no in-app purchases, no analytics, no network calls
- The only currency is local and has no cash value

## 1. Run loop

A *run* is one stack attempt. Its state lives only in memory: closing the app
during a run abandons it, and the stake is already spent.

| Phase | Meaning | Allowed input |
| --- | --- | --- |
| `READY` | Yard empty, risk tier and stake editable | STACK (starts a run), tier, stake |
| `SWINGING` | Block hangs on the hoist | STACK or a tap on the yard (releases), BANK IT (after floor 1) |
| `RELEASING` | Block is in the air | none |
| `BANKED` | Payout locked in, panel showing | COLLECT |
| `TOPPLED` | Stack collapsed | none, auto-returns to `READY` |

Rules:

1. Starting a run charges the stake immediately and releases the first block
   without a second tap, so a run always opens with a drop.
2. The hoist swings for presentation only. **Release timing does not affect the
   outcome** — the verdict is rolled by `RoundMachine` at the moment of release.
3. A block that holds adds one floor; a block that misses ends the run.
4. Banking is available from floor 1 onward and pays `floor(stake × combo)`.
5. A topple shows `x0`, then clears the yard back to `READY` after 750 ms with no
   player action needed.
6. Risk tier is locked while a run is live; stake edits are ignored while live.
7. If the drop animation never reports back (surface destroyed mid-drop), a 6 s
   guard applies the already-rolled verdict so a run can never get stuck.

## 2. Risk tiers

`holdChance` is the probability a released block holds. A holding block's step
multiplier is drawn uniformly from `minStep..maxStep` and rounded to two
decimals. Steps multiply into the combo.

| Tier | Hold chance | Step range | Swing cycle (base → fastest) |
| --- | --- | --- | --- |
| Steady | 0.880 | 0.85 – 1.45 | 1.55 s → 0.75 s |
| Swift | 0.775 | 0.70 – 1.95 | 1.20 s → 0.60 s |
| Risky | 0.625 | 0.60 – 2.70 | 0.92 s → 0.48 s |
| Chaos | 0.485 | 0.50 – 3.80 | 0.66 s → 0.38 s |

The swing cycle shortens by 0.03/0.04/0.05/0.06 s per placed floor and clamps at
the tier's fastest value.

Because the step range starts below 1.0, a surviving block can still shrink the
combo. That is intentional: it keeps early banking a real decision.

## 3. Economy

| Rule | Value |
| --- | --- |
| Currency label | `CR` (local only, no cash value) |
| Starting balance | 1 000 |
| Stake range | 10 – 100 000, clamped to the balance |
| Stake buttons | ±10, ±50, ±250, ±1 000, plus `x2` and `ALL IN` |
| Payout | `floor(stake × combo)` |
| Multiplier precision | two decimals, shown value equals paid value |
| XP per held block | 6 |
| XP per banked run | 14 |

Attempting to start a run without enough credits shows an inline "not enough
credits" note for 3 s and a heavy haptic tap; nothing is charged.

## 4. Ranks

- Rank 1 to 60.
- XP to leave rank *r*: `60 + (r − 1) × 45`.
- Reaching rank *r* pays `100 + r × 50` credits.
- Crossing several ranks in one award pays each of them.
- Ranks 2, 4 and 7 gift the block styles Timber Lodge, Brick Loft and Hex Cabin.

## 5. Store

Four block styles and four sky moods. Styles are cosmetic; sky moods regrade the
one painted backdrop with a translucent vertical wash, which also dims the
pavement and thins the clouds.

| Block style | Price | Gifted at rank |
| --- | --- | --- |
| Sunny Flat | 0 (owned) | — |
| Timber Lodge | 800 | 2 |
| Brick Loft | 1 500 | 4 |
| Hex Cabin | 2 500 | 7 |

| Sky mood | Price | Rank required |
| --- | --- | --- |
| Daylight | 0 (owned) | — |
| Sunset | 1 500 | — |
| Dusk | 3 000 | 3 |
| Midnight | 6 000 | 6 |

Buying an owned item just equips it. A purchase is refused, silently and without
charging, when credits are short or the rank requirement is not met. With
"shuffle block styles" on, every block picks a random owned style.

## 6. Daily reward

Seven-day ladder: 100, 150, 200, 300, 450, 700, 1 200 credits.

- One claim per local calendar day.
- Claiming on the day right after the previous claim advances the slot.
- A missed day resets to slot 1; finishing slot 7 wraps back to slot 1.

## 7. Daily quests

Three quests per local day, picked deterministically from a twelve-entry pool by
seeding on the day index, so the set is stable for the day and changes at
midnight. The picker prefers one quest per kind.

Quest kinds and the counter each one reads:

| Kind | Counter |
| --- | --- |
| Blocks placed | blocks held today |
| Runs banked | runs banked today |
| Bank streak | best back-to-back bank streak today |
| Floor reached | best floor in a single run today |
| Credits banked | credits banked today |

Only the claim flag is stored; progress is always derived from the day counters,
so the two cannot drift apart. Rewards range 200–450 credits. Day counters and
the quest set reset on the first profile write of a new day.

## 8. Weekly ranks (offline)

- Fourteen rivals with scores generated from the ISO week index and the selected
  metric, so the table is stable all week and reshuffles at the rollover.
- Metrics: best height, best single payout, best bank streak — each the player's
  best for the current week.
- Everything is generated on the device. Nothing is uploaded and no account is
  needed.

## 9. Persistence

Stored with DataStore Preferences under the profile schema in `PlayerProfile`:
credits, XP, selected tier, last stake, haptics flag, shuffle flag, owned and
equipped cosmetics, daily-reward day and streak, quest day plus claim flags, day
counters, ISO week plus weekly bests, and lifetime bests.

- Every field has a safe default, so a missing or partially written store reads
  back as a fresh profile rather than failing.
- A live run is deliberately not persisted.
- Calendar rollovers are applied on the next profile write, not by a timer.
- "Reset progress" clears the store back to defaults.

## 10. Yard rendering and input

- Fixed simulation step capped at 1/30 s per frame, driven by a dedicated render
  thread that stops with the surface and on pause.
- World Y grows downward: the pavement is 0 and the stack climbs into negative Y.
- The camera follows the top of the stack with smoothing and a small lead.
- Screen shake on landing, collapse and banking; dust, sparks and coin flecks;
  landed blocks wobble briefly and keep a small random lean and offset.
- Geometry is presentation only. A held block is always *shown* as held, and a
  missed block always tumbles away — the verdict comes from the rules, never from
  a collision test.
- STACK and a tap anywhere on the yard send the same command. Input is ignored
  while a block is in the air.

## 11. Screens

Boot (asset preload, minimum 2.4 s) → Home → Play, Store, Quests, Ranks,
Daily reward dialog, Settings, Privacy, Support. Back always unwinds one step
and leaves the app from Home.

Gameplay is portrait-locked; everything else follows the sensor.

## 12. Privacy and support

Both pages ship inside the APK (`assets/legal/privacy-policy.html`,
`assets/legal/support.html`) and open in an in-app WebView with JavaScript and
DOM storage off. `LegalEndpoints` holds optional public URLs; while they are
`null` the bundled pages are used, so no button ever points at a dead domain.
When a URL is set, the remote page is preferred, the bundled page is the offline
fallback, and links open in the system browser.

## 13. Verification checklist

| Behaviour | How it is checked |
| --- | --- |
| Phase transitions, stake limits, verdict rolling | `RoundMachineTest` |
| Multiplier rounding and payout floor | `RoundMachineTest`, `FormattingTest` |
| Rank curve, rewards, gifts | `MetaRulesTest` |
| Daily reward slots, streak reset and wrap | `MetaRulesTest` |
| Quest determinism and variety | `MetaRulesTest` |
| ISO week and rollover maths | `MetaRulesTest` |
| Weekly table stability and ordering | `MetaRulesTest` |
| Profile mutations, rollovers, purchases, claims | `ProfileRulesTest` |
| DataStore round-trip | `ProfileStoreTest` (instrumentation) |
| Navigation into and back out of every screen | `ShellNavigationTest` (instrumentation) |

Manual passes to run before a release: several screen sizes, background and
return mid-run, a long stack (camera and memory), a date change (daily reward
and quests), airplane mode (legal pages), and a few minutes of continuous play
to watch the render thread.

## 14. Google Play note

A clean-room implementation removes the code-similarity problem. It does **not**
guarantee approval:

- Play's *Repetitive content / spam* policy targets apps that duplicate an
  existing app's experience, regardless of how the code was written. A game that
  reproduces another published game's mechanics one-to-one can still be rejected
  on that basis.
- The app has no ads, no purchases and no data collection, so Data Safety is a
  short form, but the store listing must still be filled in honestly.
- Simulated-gambling framing is a risk area. The game uses a local, non-cashable
  currency with no purchase path and no payout, which is the safer side of that
  line, but wording in the listing and screenshots should avoid implying real
  gambling.

Treat the listing text, screenshots and title as the part of the submission that
most needs to be original.
