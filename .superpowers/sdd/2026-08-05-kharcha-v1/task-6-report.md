# Task 6 report: SMS ingestion — receiver, worker, and backfill

Branch: `feat/kharcha-v1-task6-sms-ingestion`, off `feat/kharcha-v1` (Tasks 1-5, 7, 8 merged
in — Task 7 landed on the shared branch mid-task and was merged in via fast-forward, see
Deviations).

Commit: `c5b6f12` — `feat(app): ingest SMS via receiver, worker and historical backfill`

## Ingest flow

`MessageIngestor.ingest(sender, body, receivedAtEpochMillis)` (`app/src/main/kotlin/com/kharcha/app/ingest/MessageIngestor.kt`)
is the single place all pipeline logic lives:

1. Case-insensitive sender check against `ruleset.senderId` ("SBL_Alert") → `WRONG_SENDER`
   if it doesn't match. No DB touched yet.
2. `contentHashOf(sender, body, receivedAtEpochMillis)` (from `:data`) → build a `RawMessage`
   → `rawMessageDao.insertIgnoringDuplicates(...)`. A `-1L` result → `DUPLICATE`.
3. Otherwise run `ruleset.parse(body)`:
   - `Parsed` → map `ParsedTransaction` to `TransactionEntity` (categoryId left `null` for
     Task 7... it turns out Task 7 already landed, but this task still leaves categorization
     untouched — no call into `Categorizer`; that wiring belongs to whoever integrates Task 7
     into the ingest path) and `transactionDao.insert(...)` → `STORED`.
   - `Ignored` → `rawMessageDao.markIgnored(rawMessageId)` → `IGNORED`.
   - `Unrecognized` → nothing further; the raw message is already stored with `ignored = false`
     and no linked transaction → `UNPARSED`.

`ParsedTransaction.occurredAt` (a timezone-less `LocalDateTime`) is converted to epoch millis
via `TimeZone.currentSystemDefault()` — there was no existing convention for this in the
codebase, so I picked the device's local timezone since that's what the bank's SMS timestamp
means.

## What I added to `RawMessageDao`

```kotlin
@Query("UPDATE raw_messages SET ignored = 1 WHERE id = :id")
suspend fun markIgnored(id: Long)
```

This is exactly the method the brief calls out as deliberately missing. (Task 7, merged into
`feat/kharcha-v1` after this task started, separately added `getAll()` to the same DAO and
`getByRawMessageId()` to `TransactionDao` — the fakes in the test file implement both so the
merge didn't leave them unused/uncompilable.)

## Receiver stays thin

`SmsReceiver` (`app/src/main/kotlin/com/kharcha/app/ingest/SmsReceiver.kt`) does exactly three
things: calls `Telephony.Sms.Intents.getMessagesFromIntent(intent)`, concatenates the bodies of
all parts (multipart SMS arrive as multiple `SmsMessage` objects from the same sender) into one
string, and enqueues a `OneTimeWorkRequest<IngestWorker>` via `WorkManager.getInstance(context)`
with sender/body/timestamp as `Data`. No parsing, no DAO, no `MessageIngestor` reference at all
— it can't touch the database even by accident.

`IngestWorker` is a `@HiltWorker` `CoroutineWorker` that unpacks the three input-data fields and
calls `messageIngestor.ingest(...)`, discarding the specific `IngestOutcome` (WorkManager just
reports success/failure back to the OS).

## Backfill dedup and completion tracking

`BackfillWorker` queries `Telephony.Sms.Inbox` with `address LIKE 'SBL_Alert'` (SQLite's default
ASCII-case-insensitive collation, matching the ingestor's own case-insensitive sender check),
ordered by date ascending, and calls `messageIngestor.ingest(...)` per row via a `Cursor` loop
(`cursor.moveToNext()` already pages through the underlying `CursorWindow` in bounded chunks, so
there's no need for manual `LIMIT`/`OFFSET` paging on top of that).

Idempotency is free: every row goes through `MessageIngestor.ingest`, which dedups on content
hash. Re-running the worker after messages have already been ingested just returns `DUPLICATE`
for everything already seen.

Completion tracking is a `BackfillState` class (`app/src/main/kotlin/com/kharcha/app/ingest/BackfillState.kt`)
wrapping a Preferences `DataStore` (`Context.backfillDataStore`, new dependency — `androidx.datastore:datastore-preferences`
wasn't in the version catalog, added it) with one boolean key, `backfill_complete`. The worker
checks it first and short-circuits to `Result.success()` if already done, and sets it at the end
of a successful pass. This is an optimization, not a correctness mechanism — the content-hash
dedup is what actually makes re-runs safe — but it avoids re-scanning the entire SMS inbox on
every app start once the historical import has already happened once.

Nothing in this task actually triggers `BackfillWorker` after permission grant, because that
UI/permission flow is explicitly out of scope (Tasks 9-12). The worker and its DI wiring
(`@HiltWorker` constructor injection of `MessageIngestor` and `BackfillState`) are ready for
whichever later task owns the permission-grant callback to call
`WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<BackfillWorker>().build())`.

## DI wiring (not in the original brief's file list, but required for a working app)

Added `app/src/main/kotlin/com/kharcha/app/di/DataModule.kt`, a Hilt `@Module` that provides:
- `KharchaDatabase` via `Room.databaseBuilder(context, KharchaDatabase::class.java, "kharcha.db")`
  **with `.addCallback(KharchaDatabase.seedCallback)` attached** — Task 7 added this callback to
  `:data` to seed default categories/rules on first DB creation, but nothing in `:app` was
  wiring it in yet. Since this task is the one creating the real production `Room.databaseBuilder`
  call, I attached it here; without this a real device would launch to zero categories.
- `RawMessageDao`, `TransactionDao` from that database.
- `SenderRuleset` bound to `SblAlertRuleset` (the only ruleset that exists so far).
- `MessageIngestor` built from the above.
- `BackfillState` wrapping `context.backfillDataStore`.

Extended (not replaced) the existing `KharchaApplication`: it now implements
`Configuration.Provider` and injects `HiltWorkerFactory`, so `@HiltWorker`-annotated
`IngestWorker`/`BackfillWorker` get constructor injection instead of needing a no-arg
constructor. Manifest changes: registered `SmsReceiver` for
`android.provider.Telephony.SMS_RECEIVED` guarded by `BROADCAST_SMS`, and disabled
WorkManager's default `androidx.startup` auto-initialization (via `tools:node="remove"` on the
`WorkManagerInitializer` meta-data) since `Configuration.Provider` supplies on-demand
initialization instead — both approaches active simultaneously throws at runtime.

## Commands run (real output)

```
$ export ANDROID_HOME=/home/sushi/Android/Sdk && unset ANDROID_SDK_ROOT
$ ./gradlew :app:testDebugUnitTest
...
BUILD SUCCESSFUL in 4s
48 actionable tasks: 6 executed, 42 up-to-date
```
(`app/build/test-results/.../TEST-com.kharcha.app.ingest.MessageIngestorTest.xml`:
`tests="5" skipped="0" failures="0" errors="0"`)

```
$ ./gradlew :app:assembleDebug
...
BUILD SUCCESSFUL in 20s
63 actionable tasks: 24 executed, 39 up-to-date
```

```
$ ./gradlew testDebugUnitTest test
...
BUILD SUCCESSFUL in 15s
109 actionable tasks: 44 executed, 17 from cache, 48 up-to-date
```
(whole-project sanity check: `:parser`, `:data`, `:app` debug and release unit tests all pass,
nothing else regressed.)

## Deviations from the brief

- Brief's file list didn't mention a DI module, but `MessageIngestor`/`IngestWorker`/
  `BackfillWorker` all need real constructor arguments in production (`RawMessageDao`,
  `TransactionDao`, `SenderRuleset`, `BackfillState`) and the app already commits to Hilt
  (`@HiltAndroidApp` existed since Task 1). Added `app/src/main/kotlin/com/kharcha/app/di/DataModule.kt`
  to close that gap — otherwise the app wouldn't actually run.
- Added `androidx.datastore:datastore-preferences` to the version catalog and `:app` — not
  previously a dependency anywhere in the project — plus `androidx.room:room-runtime`/`room-ktx`
  and `kotlinx-coroutines-test` to `:app` (needed for `Room.databaseBuilder` and `runTest`
  respectively; the test dependency was missing and the brief's own test file requires it).
- Mid-task, `feat/kharcha-v1`'s tip moved forward to include Task 7 (rule-based categorization,
  `KharchaDatabase.seedCallback`, `SeedData`, plus `RawMessageDao.getAll()` /
  `TransactionDao.getByRawMessageId()`). I fast-forward-merged that in and updated the two fakes
  in `MessageIngestorTest.kt` to implement the two new DAO methods so the module still compiles.
  No conflicts with `MessageIngestor` logic itself. Per the coordinator's follow-up, I also wired
  `KharchaDatabase.seedCallback` into the new `Room.databaseBuilder` call in `DataModule` — this
  wasn't in the original Task 6 brief but is squarely this task's responsibility since it's the
  one creating the real database instance.
- `BackfillWorker`'s cursor loop is a plain `while (cursor.moveToNext())` rather than explicit
  `LIMIT`/`OFFSET` paging — `Cursor`/`CursorWindow` already pages the result set internally in
  bounded windows, so manual paging on top would just be redundant bookkeeping. Documented this
  reasoning inline in the worker.
- Used `LIKE` rather than `=` for the backfill's `Telephony.Sms.Inbox` sender filter so it's
  case-insensitive, matching `MessageIngestor`'s own case-insensitive sender check — the brief
  says sender matching is case-insensitive but the SQL query itself isn't specified.

## Concerns for later tasks

- **Task 7 integration**: `MessageIngestor` currently never calls into `Categorizer`
  (Task 7, already merged into `:data`). Every `TransactionEntity` it inserts has
  `categoryId = null`. Someone needs to decide whether `MessageIngestor` itself should call
  `Categorizer` after insert, or whether categorization stays a separate pass (Task 7's
  `ReparseService` already exists for reprocessing). I left this alone per this task's explicit
  scope ("Do NOT implement: categorization (Task 7)") even though Task 7 landed on the shared
  branch before I finished — didn't want to reach into another task's territory without being
  asked.
- **Task 11 (budget notifications)**: `IngestWorker`'s `Result` is always `success()` regardless
  of `IngestOutcome`; there's no signal fan-out (e.g. a budget-threshold check) after a `STORED`
  transaction. Whoever wires notifications will need a hook — either have `MessageIngestor`
  return enough info to act on, or have `IngestWorker` branch on the returned `IngestOutcome`.
- **Task 12 (unparsed inbox UI)**: relies on `RawMessage.ignored = false` AND no linked
  `TransactionEntity` (via `rawMessageId`) to define "unparsed." That invariant holds as
  implemented — `Unrecognized` results never call `transactionDao.insert`, and `Ignored` results
  set `ignored = true` — but there is no DB-level constraint enforcing it, so any future
  code path that inserts a `RawMessage` outside `MessageIngestor` could silently break the
  unparsed-inbox definition. Worth a comment or a test in `:data` if Task 12 wants a guarantee
  stronger than "convention."
- **No device/emulator testing was possible** (per constraints) — `SmsReceiver`'s
  `getMessagesFromIntent`/multipart concatenation, the manifest's `BROADCAST_SMS`-permission
  receiver registration, and `BackfillWorker`'s actual `Telephony.Sms.Inbox` query are all
  unverified beyond compiling and `assembleDebug` succeeding. They should get a real-device or
  at minimum Robolectric-backed smoke test before Task 9-12 build UI on top of the assumption
  that ingestion actually works end-to-end on-device.

## Fix: wire categorization into `MessageIngestor` (post-review)

Review verdict: the categorization gap (`categoryId` always `null`) was **not** a legitimate
scope boundary — the "Do NOT implement: categorization (Task 7)" line in the original task
description governed Task 7's own scope, not Task 6's, and by the time this task's `Parsed`
branch runs, Task 7's `Categorizer` was already merged into `:data` and callable. Leaving
`categoryId = null` unconditionally broke the product's headline promise ("a new SMS appears
as a categorized transaction without opening the app").

### What changed

`MessageIngestor` (`app/src/main/kotlin/com/kharcha/app/ingest/MessageIngestor.kt`) now takes a
fourth constructor argument, `ruleDao: RuleDao` (plain Kotlin interface from `:data`, so the
class stays fake-able with no Room/Android types). On a `Parsed` result, before building the
`TransactionEntity`, a new private `categorize(transaction: ParsedTransaction): Long?` helper:

1. Calls `ruleDao.observeAll().first()` to snapshot the current rule set.
2. Builds a fresh `Categorizer(rules)` (from `:data`, already tested in `CategorizerTest`) and
   calls `categorize(remark, merchant)`.
3. Wraps the whole thing in `try/catch (_: Exception) { null }` — categorization failure must
   never fail ingestion; an uncategorized transaction beats a lost one. An unmatched remark
   naturally returns `null` from `Categorizer` itself, which is the correct non-fatal outcome,
   not an exception path.

The resulting `Long?` is passed into `categoryId` on the built `TransactionEntity`, and
`categoryIsManualOverride` stays hardcoded `false` — auto-assigned categories are explicitly
not manual overrides, so Task 7's `ReparseService` can later recompute them freely.

Rebuilding `Categorizer` from scratch on every `ingest()` call (rather than caching it) is
deliberate: rules can change between messages (user edits a rule, Task 7's re-parse changes
seed data, etc.) and `MessageIngestor` has no lifecycle hook to know when to invalidate a
cached instance. Given `Categorizer`'s construction cost is a `sortedWith` over what will
realistically be a handful of rules, this is not worth optimizing pre-emptively.

`app/src/main/kotlin/com/kharcha/app/di/DataModule.kt` now also provides `RuleDao` (via
`database.ruleDao()`) and threads it into the `MessageIngestor` `@Provides` function.

### Tests added

`app/src/test/kotlin/com/kharcha/app/ingest/MessageIngestorTest.kt`:

- New `FakeRuleDao(rules: List<RuleEntity>)` implementing `RuleDao` with `observeAll()`
  backed by `flowOf(rules)` — in-memory, no Room.
- `newIngestor()` now takes optional `transactionDao`/`rules` parameters and always wires a
  `FakeRuleDao`; default `seededRules` mirror `SeedData.RULES`'s `WTax.Pd -> Fees` rule
  (`categoryId = 6L`, matching `SeedData.FEES_ID`) plus a merchant-matching rule for the
  existing `qrPayment` fixture (`categoryId = 1L`, matching `SeedData.CATEGORIES`'s first
  entry, "Food & Dining"). Note the QR fixture's rule matches on `"HANKOOK"` rather than
  `"QR Payment"` — `SblAlertRuleset` extracts `merchant = "JAWALAKHEL HANKOOK SARANG RESTAU"`
  for a `"QR Payment to <merchant>"` remark, and `Categorizer` matches against `merchant` when
  present, not `remark`.
- Three new cases, all asserting `IngestOutcome.STORED` plus inspecting the single stored
  `TransactionEntity`:
  - `a QR payment matching a seeded rule is categorized on ingest` — `categoryId == 1L`
    (Food & Dining) and `categoryIsManualOverride == false`.
  - `a WTax Pd message is categorized as Fees` — a fresh family-A-shaped SMS with remark
    `"WTax.Pd on Interest"`, asserting `categoryId == 6L` (Fees) and
    `categoryIsManualOverride == false`.
  - `an unmatched remark is stored uncategorized` — a family-A-shaped SMS with a remark no
    rule matches, asserting `categoryId == null` and the outcome is still `STORED` (not a
    failure).
- All 5 original assertions unchanged (same bodies, same expected `IngestOutcome`s).

### Commands run (real output)

```
$ export ANDROID_HOME=/home/sushi/Android/Sdk && unset ANDROID_SDK_ROOT
$ ./gradlew :app:testDebugUnitTest --tests '*MessageIngestorTest*'
...
BUILD SUCCESSFUL in 3s
48 actionable tasks: 4 executed, 44 up-to-date
```
(`app/build/test-results/testDebugUnitTest/TEST-com.kharcha.app.ingest.MessageIngestorTest.xml`:
`tests="8" skipped="0" failures="0" errors="0"` — 5 original + 3 new.)

```
$ ./gradlew :app:assembleDebug
...
BUILD SUCCESSFUL in 1s
63 actionable tasks: 4 executed, 59 up-to-date
```

### Concerns carried forward

Unchanged from the original report: the discarded `IngestOutcome` in `IngestWorker` (no hook
yet for Task 11's budget-notification fan-out), the unparsed-inbox invariant holding by
convention rather than a DB constraint, and the device-unverified receiver/backfill code paths.
No new concerns introduced by this fix — categorization failure is caught and degrades to
`categoryId = null`, which was already the correct/expected outcome for an unmatched remark.

## Fix: stop re-querying rules per message during backfill (second post-review round)

Review verdict on the categorization fix: **ADDRESSED**, all five conditions verified,
mutation-tested by injecting a throw into the `try/catch` to confirm it's load-bearing. But it
surfaced a new Important finding: `BackfillWorker`'s per-row loop called the single-message
`ingest(...)`, which built a fresh `Categorizer` — one `ruleDao.observeAll().first()` (a full
`SELECT * FROM rules`) plus an `O(n log n)` sort — on every historical row. For a first-run
backfill scanning a user's entire SMS history that's N synchronous DB round-trips where 1 would
do, and it's exactly the kind of thing that makes a first-run experience feel broken.

### What changed

`MessageIngestor` (`app/src/main/kotlin/com/kharcha/app/ingest/MessageIngestor.kt`) now exposes
three public members instead of one:

- `suspend fun ingest(sender, body, receivedAtEpochMillis): IngestOutcome` — unchanged
  signature, unchanged behavior. Internally now just delegates to the four-arg overload with
  `categorizer = null`, which keeps its old semantics: fetch the current rules and build a
  fresh `Categorizer` on every call, so the live single-message path (`IngestWorker`) always
  reflects a rule the user added a moment ago.
- `suspend fun ingest(sender, body, receivedAtEpochMillis, categorizer: Categorizer?): IngestOutcome`
  — the new batch-friendly entry point. When `categorizer` is non-null, it's used directly
  instead of touching `ruleDao` at all.
- `suspend fun loadCategorizer(): Categorizer` — reads the rule set once (`ruleDao.observeAll().first()`)
  and builds a `Categorizer` from it. Batch callers call this once per run, then pass the
  result into every `ingest(...)` call in the batch.

`categorize(transaction, categorizer)` was updated to use the passed-in `Categorizer` when
present, only falling back to a fresh `ruleDao` query when `categorizer == null` (the
single-message path). The `try/catch` degrading to `categoryId = null` on any failure is
unchanged and still wraps both paths.

`BackfillWorker` (`app/src/main/kotlin/com/kharcha/app/ingest/BackfillWorker.kt`) now calls
`messageIngestor.loadCategorizer()` once before the cursor loop, and passes that single
`Categorizer` instance into every `messageIngestor.ingest(sender, body, receivedAtEpochMillis, categorizer)`
call inside `while (cursor.moveToNext())`. One rule-table read and one sort per backfill run,
regardless of how many historical messages it processes.

### Test added

`app/src/test/kotlin/com/kharcha/app/ingest/MessageIngestorTest.kt`:

- `FakeRuleDao` now tracks `observeAllCallCount`, incremented on every `observeAll()` call.
- `newIngestor()` gained an optional `ruleDao: FakeRuleDao` parameter so tests can hold a
  reference to the fake and inspect its call count after exercising the ingestor.
- New test `a batch ingested with a pre-built categorizer only queries rules once`: calls
  `loadCategorizer()` once (asserting `observeAllCallCount == 1` immediately after), then
  ingests three different messages — a QR payment, a WTax.Pd message, and a second WTax.Pd-like
  message — all passing the same pre-built `categorizer`. Asserts `observeAllCallCount` is
  still `1` after all three (proving the batch path doesn't re-query), that all three
  transactions were stored, and that each landed in the category its matching rule implies
  (Food & Dining, Fees, Fees) — proving the shared `Categorizer` instance still categorizes
  correctly across the whole batch, not just that the query count is low.
- All 8 prior assertions (5 original + 3 from the earlier categorization fix) unchanged.

### Commands run (real output)

```
$ export ANDROID_HOME=/home/sushi/Android/Sdk && unset ANDROID_SDK_ROOT
$ ./gradlew :app:testDebugUnitTest --tests '*MessageIngestorTest*'
...
BUILD SUCCESSFUL in 6s
48 actionable tasks: 11 executed, 37 up-to-date
```
(`app/build/test-results/testDebugUnitTest/TEST-com.kharcha.app.ingest.MessageIngestorTest.xml`:
`tests="9" skipped="0" failures="0" errors="0"` — 8 prior + 1 new.)

```
$ ./gradlew :app:testDebugUnitTest
...
BUILD SUCCESSFUL in 1s
48 actionable tasks: 1 executed, 47 up-to-date
```

```
$ ./gradlew :app:assembleDebug
...
BUILD SUCCESSFUL in 1s
63 actionable tasks: 3 executed, 60 up-to-date
```

### Concerns carried forward

Same three as before (discarded `IngestOutcome` for Task 11, unparsed-inbox invariant by
convention, device-unverified receiver/backfill paths). No new concerns — the live
single-message path's per-call rule fetch is intentional (correctness for a just-added rule),
and the batch path now does the minimum possible DB work per run.
