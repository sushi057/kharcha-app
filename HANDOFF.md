# Kharcha — handoff

Last updated: 2026-08-05. Local-only repo, no remote by design.

## What this is

An Android expense tracker for Siddhartha Bank. The bank has no API, so the app
reads its SMS alerts (`SBL_Alert`), parses them into transactions, categorizes
them, and visualizes the result. Everything is on-device.

## Resuming work

Tell a fresh session:

> Read `.superpowers/sdd/2026-08-05-kharcha-v1/progress.md` and continue
> executing `docs/superpowers/plans/2026-08-05-kharcha-v1.md` with
> superpowers:subagent-driven-development. Check `.claude/worktrees/` for
> uncommitted work first.

The three files that carry all the context:

| File | What it holds |
|---|---|
| `docs/superpowers/specs/2026-08-05-kharcha-sms-expense-tracker-design.md` | The design: message formats, architecture, success criteria |
| `docs/superpowers/plans/2026-08-05-kharcha-v1.md` | 12 tasks, each with the exact code and tests to write |
| `.superpowers/sdd/2026-08-05-kharcha-v1/progress.md` | The ledger: completions, findings, rulings, open items |

The ledger is authoritative over anyone's recollection. Tasks with a
`Task <N>: complete` line are done — do not redo them.

## Environment (already set up, WSL2)

- JDK 21 at `~/.local/opt/jdk-21`, pinned in `~/.gradle/gradle.properties`.
  The system only has a JRE — `javac` is not on PATH without this.
- Linux Android SDK at `~/Android/Sdk` (build-tools 35.0.0, platform 35,
  platform-tools). `ANDROID_HOME` in the shell profile points at the *Windows*
  SDK under `/mnt/c/...`, which Gradle-in-WSL cannot use. **Export
  `ANDROID_HOME=/home/sushi/Android/Sdk` before every Gradle invocation.**
- `local.properties` carries `sdk.dir` and is gitignored.

Verify a clean checkout with:

```bash
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :parser:test :data:testDebugUnitTest :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

## Architecture

- **`:parser`** — pure Kotlin, zero Android dependencies. This is deliberate and
  load-bearing: it keeps the parsing logic fast to test. Do not add an Android
  dependency here.
- **`:data`** — Room. Every raw SMS is kept forever in `raw_messages` so history
  can be re-parsed when rules improve. Flat package `com.kharcha.data`.
- **`:app`** — Compose UI plus ingestion (BroadcastReceiver → WorkManager).

## Invariants — violating any of these is a defect

- **Money is `Long` minor units.** Never `Double`, never `Float`, anywhere,
  including intermediate arithmetic. NPR 2,984.00 is `298400L`.
- **Currency is explicit on every monetary value.** NPR and USD both occur; they
  are never summed or converted.
- **Parse failure is a returned value, never an exception.** `parse()` runs
  inside an SMS broadcast pipeline and must not throw on any input.
- **A partially-bad message is `Unrecognized`, never a guess.** A wrong
  transaction is worse than a missing one.
- **Re-parse preserves manual overrides.** A user's hand-set category survives
  rule improvements. If this breaks, users silently lose work.

## Message formats (from real SMS)

Three families, all from `SBL_Alert`:

1. **NPR account** — `Dear SUVASH, AC 0###15164761, NPR 2,984.00 withdrawn on
   17/07/2026 12:10:01 for QR Payment to JAWALAKHEL HANKOOK SARANG RESTAU`
   Date `dd/MM/yyyy HH:mm:ss`. The `for …` tail is the remark and **is truncated
   by the SMS length limit** — merchant matching must be prefix-tolerant.
2. **USD card** — `SBL Card ***5367 used at SPACESHIP.COM* NRXD3L, US for USD
   1.98 on 02.08.26 23:28 Authid 512208 Remaining Balance after txn USD 241.22.`
   Note the *different* date format `dd.MM.yy HH:mm`, and a balance the NPR
   messages don't carry.
3. **Ignored** — OTPs and purchase codes. Matched explicitly and discarded
   **before** any transaction pattern is tried, because the purchase-code
   message contains a date, a currency and an amount and would otherwise
   plausibly parse as a transaction that never happened.

## Known open items

- **Play Store**: `RECEIVE_SMS`/`READ_SMS` are restricted permissions. This is a
  sideload/personal build; shipping publicly would need a policy exemption.
- **Instrumented tests**: the plan specifies `connectedAndroidTest` for Room and
  Compose tests. No device was attached, so those were written as Robolectric
  JVM tests instead. They should be run against a real device before shipping.
- Deferred minor findings are listed in the ledger and should be triaged by the
  final whole-branch review before merge.

## Deliberate deviations from the plan

- **No generic `QR Payment → Food & Dining` seed rule.** The plan hinted at one;
  it was omitted on purpose. QR payments span every category (the same prefix
  covers a restaurant and a Miniso shopping trip), so the rule would confidently
  miscategorize most spending. Uncategorized-until-corrected is better, and
  correcting one offers to save it as a rule.
