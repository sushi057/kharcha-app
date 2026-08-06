# Kharcha — handoff

Last updated: 2026-08-06. Local-only repo, no remote by design.

**v1 is COMPLETE.** All 12 tasks are implemented, reviewed and merged to
`master`. The final whole-branch review is clean. What remains is device
verification — see "Installing on your phone" below.

## What this is

An Android expense tracker for Siddhartha Bank. The bank has no API, so the app
reads its SMS alerts (`SBL_Alert`), parses them into transactions, categorizes
them, and visualizes the result. Everything is on-device.

## Installing on your phone

A signed-with-the-debug-key APK is ready at `~/kharcha-install/kharcha-v1.apk`
(20 MB, `com.kharcha.app`, minSdk 26). Rebuild it any time with:

```bash
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:assembleDebug   # -> app/build/outputs/apk/debug/app-debug.apk
```

To install, either plug the phone in with USB debugging on and run
`~/Android/Sdk/platform-tools/adb install -r ~/kharcha-install/kharcha-v1.apk`,
or copy the APK to the phone and tap it (you'll have to allow install from
unknown sources). This is a personal sideload build — `RECEIVE_SMS`/`READ_SMS`
are Play-restricted, so it is not Play-Store shippable without a policy
exemption.

On first launch the app explains why it needs SMS access, then requests
`RECEIVE_SMS`/`READ_SMS` (and `POST_NOTIFICATIONS` on Android 13+). Granting
enqueues a one-time backfill of your existing inbox. Denying leaves the app
fully usable in manual-entry-only mode with a banner offering to re-request.

### What to check first, since no device was ever attached

Every instrumented test was written as a Robolectric JVM test. These paths have
never run on real hardware:

- The permission walkthrough on a fresh install.
- Backfill against your phone's real SMS provider (OEM providers vary).
- A live SMS arriving and appearing as a categorized transaction without
  opening the app.
- Budget notifications actually posting.
- Multipart (>160 char) SMS reassembly — `SmsReceiver` assumes parts arrive in
  order.
- The Room 1→2 migration against a pre-existing database (only matters if you
  install over an earlier build).

If a bank message fails to parse, it lands in the **Unparsed** tab rather than
being dropped — that tab is the early warning that the bank changed its format.

## Resuming work

v1 is done; there is no plan left to execute. For new work, the context is:

| File | What it holds |
|---|---|
| `.superpowers/sdd/2026-08-05-kharcha-v1/progress.md` | The v1 ledger: every completion, finding, ruling and deliberate deviation |
| `.superpowers/sdd/2026-08-05-kharcha-v1/final-fix-report.md` | The final fix wave (its prose misnarrates the work as pre-existing — trust the diff in `6e8a23f`, not the report) |

The other two files that carry the original context:

| File | What it holds |
|---|---|
| `docs/superpowers/specs/2026-08-05-kharcha-sms-expense-tracker-design.md` | The design: message formats, architecture, success criteria |
| `docs/superpowers/plans/2026-08-05-kharcha-v1.md` | 12 tasks, each with the exact code and tests to write |

The ledger is authoritative over anyone's recollection.

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
- **Deferred minors**: 17 were triaged by the final whole-branch review. Two were
  must-fix and are fixed; the other 15 ship as-is and are listed in the ledger
  with rulings.
- **Light mode** is now genuinely supported (the final review caught Dashboard
  and Budgets drawing the dark palette's constants against the light scheme,
  which made headings invisible). It has been fixed and unit-tested for
  contrast, but never seen on a screen — worth an eyeball on the phone.

## Deliberate deviations from the plan

- **No generic `QR Payment → Food & Dining` seed rule.** The plan hinted at one;
  it was omitted on purpose. QR payments span every category (the same prefix
  covers a restaurant and a Miniso shopping trip), so the rule would confidently
  miscategorize most spending. Uncategorized-until-corrected is better, and
  correcting one offers to save it as a rule.
- **`BackfillWorker` deliberately does not fire budget notifications.** Importing
  months of history at once would spam alerts. Consequence: if the backfill alone
  pushes a category over budget and no live transaction follows that month, no
  notification fires — but the Budgets screen still shows "Over budget", because
  that is computed live from spend rather than from alert state.
- **Backfill/live-receiver dedup uses a second gate.** The receiver hashes the
  SMSC timestamp and the backfill hashes the provider's reception time, so the
  same message has two different content hashes and the unique index cannot
  catch it. A secondary `(sender, body, ±10 min)` check runs only when the hash
  misses. This cannot swallow a real repeat purchase: SBL_Alert bodies embed a
  second-resolution timestamp, and card messages carry a unique `Authid`, so two
  genuinely distinct transactions never share a byte-identical body.
