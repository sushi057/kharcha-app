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
