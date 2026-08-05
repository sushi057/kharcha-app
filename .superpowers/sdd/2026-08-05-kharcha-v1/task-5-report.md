# Task 5 Report: Room schema and repository

## Branch
`feat/kharcha-v1-task5`, branched from `feat/kharcha-v1` (HEAD was `20a2ef1`).

## Note on brief location
`.superpowers/sdd/2026-08-05-kharcha-v1/task-5-brief.md` did not exist in this worktree
(worktrees don't share untracked files with the main checkout). It was present in the
main checkout at `/home/sushi/Code/projects/kharcha-app/.superpowers/sdd/2026-08-05-kharcha-v1/`
and was read from there, then copied into this worktree for reference.

## Schema created
Package `com.kharcha.data` (all entities/DAOs/database use this single package, not
`com.kharcha.data.entity`/`com.kharcha.data.dao`, despite the brief's file-path hints —
the brief's verbatim `DedupTest` has no imports for `RawMessage`, `contentHashOf`, or
`KharchaDatabase`, which only compiles if they share the test's package. Kotlin doesn't
require directory layout to match package, so files still live under `entity/` and `dao/`
subdirectories per the brief's file list, just declaring `package com.kharcha.data`).

- `entity/RawMessage.kt` — `RawMessage(id, sender, body, receivedAtEpochMillis, contentHash, ignored = false, dismissed = false)`, unique index on `contentHash`. Also hosts top-level `contentHashOf(sender, body, receivedAtEpochMillis)` — SHA-256 hex via `java.security.MessageDigest`.
- `entity/TransactionEntity.kt` — all fields from the brief; foreign keys to `RawMessage` (SET_NULL) and `CategoryEntity` (SET_NULL), indices on both FK columns.
- `entity/CategoryEntity.kt`, `entity/RuleEntity.kt` (FK to Category, CASCADE), `entity/BudgetEntity.kt` (FK to Category, CASCADE) — as specified.
- `Converters.kt` — maps `com.kharcha.parser.Currency` and `com.kharcha.parser.Direction` enums to/from `String` via `.name`/`valueOf`.
- `dao/RawMessageDao.kt` — only `insertIgnoringDuplicates` (`@Insert(onConflict = OnConflictStrategy.IGNORE)`, returns `-1L` on conflict) and `count()`, per the brief and the "don't write beyond what's listed" constraint.
- `dao/TransactionDao.kt`, `CategoryDao.kt`, `RuleDao.kt`, `BudgetDao.kt` — minimal CRUD (`insert`/`update`/`delete`/`getById` all `suspend`, plus one `observeAll(): Flow<List<...>>` each) — no business logic, just persistence access for later tasks to build on.
- `KharchaDatabase.kt` — `@Database(version = 1, exportSchema = true)` over all five entities, `@TypeConverters(Converters::class)`, abstract accessors for all five DAOs.

Schema exported to `data/schemas/com.kharcha.data.KharchaDatabase/1.json` (committed).
Wired via `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` in `data/build.gradle.kts`.

## Robolectric config
`data/src/test/kotlin/com/kharcha/data/DedupTest.kt` — brief's test body copied verbatim
except the runner: `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [33])` in place
of `@RunWith(AndroidJUnit4::class)` (Robolectric 4.13, project compileSdk 35, picked 33 as a
safely-supported Robolectric SDK jar). Unused `AndroidJUnit4` import dropped since the
Robolectric runner doesn't need it.

Dependencies added to `gradle/libs.versions.toml` and `data/build.gradle.kts` (none of these
existed before): `junit:junit:4.13.2` (Robolectric requires JUnit4, not JUnit5),
`org.junit.vintage:junit-vintage-engine` (runs JUnit4 tests under the JUnit5 platform runner
already used by `:data`), `androidx.test:core:1.6.1`, `androidx.test.ext:junit:1.2.1`
(for `ApplicationProvider`), `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0` (for
`runTest`). `data/build.gradle.kts` test task now does
`useJUnitPlatform { includeEngines("junit-jupiter", "junit-vintage") }`.

## Commands run
```
export ANDROID_HOME=/home/sushi/Android/Sdk && unset ANDROID_SDK_ROOT
./gradlew :data:testDebugUnitTest
```
- First run (before implementation): FAILED — `compileDebugUnitTestKotlin` errored with
  `Unresolved reference 'KharchaDatabase'` / `RawMessage` / `contentHashOf` (red, as expected).
- Second run (after implementation): `BUILD SUCCESSFUL in 1m 51s`.
  `data/build/test-results/testDebugUnitTest/TEST-com.kharcha.data.DedupTest.xml`:
  `tests="1" skipped="0" failures="0" errors="0"`, testcase
  `"inserting the same message twice stores one row"` present with no failure element.

Note: had to `unset ANDROID_SDK_ROOT` alongside `export ANDROID_HOME` — the shell's
pre-existing `ANDROID_SDK_ROOT` pointed at a Windows path and Gradle refused to proceed
with two conflicting SDK locations set.

## Deviations from the brief
1. Test is Robolectric-based JVM test under `data/src/test/`, not an instrumented test
   under `data/src/androidTest/` — per task instructions (no device/emulator attached).
2. All production files declare `package com.kharcha.data` rather than
   `com.kharcha.data.entity` / `com.kharcha.data.dao`, to keep the brief's `DedupTest`
   compiling verbatim with no added imports. Directory layout still matches the brief's
   file list.
3. Added test-only dependencies (junit4, vintage engine, androidx-test-core/ext-junit,
   kotlinx-coroutines-test) not present in the original version catalog — necessary to run
   any Robolectric/JUnit4 test at all; no production dependency changes beyond this.
4. `RawMessageDao` deliberately kept to exactly `insertIgnoringDuplicates` + `count()`
   (the two methods the test exercises) rather than adding query/observe methods for
   `ignored`/`dismissed`, honoring the "DAOs the brief lists" constraint — Tasks 6/12 will
   need to add their own query methods for those columns.

## Concerns for later tasks
- **Task 6** (ingestion, sets `ignored`): will need a `RawMessageDao` update method for
  `ignored` (e.g. `setIgnored(id, ignored)`) — not added here since it's out of this task's
  DAO list; add it in Task 6 rather than reaching into `RawMessage` fields directly.
- **Task 7** (categorization, seed data): `CategoryEntity`/`RuleEntity` tables exist but are
  empty — no seed rows, per the explicit ambiguity resolution. Task 7's `SeedData` needs to
  insert at least one default category before `TransactionEntity.categoryId` /
  `RuleEntity.categoryId` foreign keys can be exercised.
- **Task 12** (unparsed inbox, reads `ignored`/`dismissed`): will similarly need its own
  query (e.g. `SELECT * FROM raw_messages WHERE ignored = 0 AND dismissed = 0`) added to
  `RawMessageDao` — the column exists and is indexed only via the primary/unique-hash index,
  no separate index on `(ignored, dismissed)` was added since usage pattern wasn't specified;
  worth revisiting if Task 12's inbox query is slow at scale.
- Room schema export directory (`data/schemas`) is wired only for the KSP arg; I added a
  `sourceSets["androidTest"].assets.srcDirs("$projectDir/schemas")` line for future
  instrumented-test schema validation, but since there's no `androidTest` source set in use
  yet (Robolectric substitutes for it here), this is unexercised — confirm it still works
  once/if real instrumented tests are added on a device.

## Fix: contentHashOf delimiter collision (post-review)

Code review found `contentHashOf` (`data/src/main/kotlin/com/kharcha/data/entity/RawMessage.kt`)
hashed `sender`, `body`, `receivedAtEpochMillis` via three separate `digest.update()` calls
with no delimiter between fields, so `contentHashOf("AB", "C", 1) == contentHashOf("A", "BC", 1)`
— any two `(sender, body)` pairs where the concatenation is byte-identical at the same
timestamp collide, silently dropping a genuinely different message as a duplicate.

**Fix**: each field is now prefixed with its own length and separated with a space byte
before being fed into the digest, so field boundaries are unambiguous:

```kotlin
fun contentHashOf(sender: String, body: String, receivedAtEpochMillis: Long): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val delimiter = " ".toByteArray(Charsets.UTF_8)
    digest.update(sender.length.toString().toByteArray(Charsets.UTF_8))
    digest.update(delimiter)
    digest.update(sender.toByteArray(Charsets.UTF_8))
    digest.update(delimiter)
    digest.update(body.length.toString().toByteArray(Charsets.UTF_8))
    digest.update(delimiter)
    digest.update(body.toByteArray(Charsets.UTF_8))
    digest.update(delimiter)
    digest.update(receivedAtEpochMillis.toString().toByteArray(Charsets.UTF_8))
    return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
}
```

**Regression test added** (`data/src/test/kotlin/com/kharcha/data/DedupTest.kt`), existing
`inserting the same message twice stores one row` test left unchanged:

```kotlin
@Test
fun `contentHashOf does not collide across a sender-body boundary shift`() {
    assertNotEquals(
        contentHashOf("AB", "C", 1),
        contentHashOf("A", "BC", 1)
    )
}
```

**Command run**:
```
export ANDROID_HOME=/home/sushi/Android/Sdk && unset ANDROID_SDK_ROOT
./gradlew :data:testDebugUnitTest
```
Output: `BUILD SUCCESSFUL in 11s` (20 actionable tasks: 7 executed, 13 up-to-date).

`data/build/test-results/testDebugUnitTest/TEST-com.kharcha.data.DedupTest.xml`:
```xml
<testsuite name="com.kharcha.data.DedupTest" tests="2" skipped="0" failures="0" errors="0" timestamp="2026-08-05T10:56:53" hostname="sushi" time="3.627">
  <testcase name="contentHashOf does not collide across a sender-body boundary shift" classname="com.kharcha.data.DedupTest" time="2.063"/>
  <testcase name="inserting the same message twice stores one row" classname="com.kharcha.data.DedupTest" time="1.563"/>
</testsuite>
```
Both tests pass, 0 failures, 0 errors.
