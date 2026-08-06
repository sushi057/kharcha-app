### Task 5: Room schema and repository

**Files:**
- Create: `data/src/main/kotlin/com/kharcha/data/entity/RawMessage.kt`, `TransactionEntity.kt`, `CategoryEntity.kt`, `RuleEntity.kt`, `BudgetEntity.kt`
- Create: `data/src/main/kotlin/com/kharcha/data/dao/RawMessageDao.kt`, `TransactionDao.kt`, `CategoryDao.kt`, `RuleDao.kt`, `BudgetDao.kt`
- Create: `data/src/main/kotlin/com/kharcha/data/KharchaDatabase.kt`, `Converters.kt`
- Test: `data/src/androidTest/kotlin/com/kharcha/data/DedupTest.kt`

**Interfaces:**
- Consumes: `:parser` types (`:data` depends on `:parser`).
- Produces:
  - `RawMessage(id, sender, body, receivedAtEpochMillis, contentHash: String, ignored: Boolean = false, dismissed: Boolean = false)` with a **unique index on `contentHash`**. `ignored` marks OTP/purchase-code messages so they never surface in the unparsed inbox (Task 12); `dismissed` is set when the user dismisses an unparsed message.
  - `TransactionEntity(id, rawMessageId: Long?, sourceAccount, amountMinorUnits: Long, currency: Currency, direction, occurredAtEpochMillis, remark, merchant: String?, balanceAfterMinorUnits: Long?, categoryId: Long?, categoryIsManualOverride: Boolean, excludedFromSpending: Boolean, isManualEntry: Boolean)`
  - `CategoryEntity(id, name, colorArgb: Int, isIncome: Boolean, isFee: Boolean)`
  - `RuleEntity(id, matchPattern, matchesPrefix: Boolean, categoryId, priority: Int)`
  - `BudgetEntity(id, categoryId, monthlyLimitMinorUnits: Long, currency, alertThresholdPercent: Int)`
  - `fun contentHashOf(sender: String, body: String, receivedAtEpochMillis: Long): String` — SHA-256 hex.
  - `RawMessageDao.insertIgnoringDuplicates(message: RawMessage): Long` — returns `-1L` when the hash already exists.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.kharcha.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@RunWith(AndroidJUnit4::class)
class DedupTest {
    private lateinit var db: KharchaDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), KharchaDatabase::class.java
        ).build()
    }

    @After fun tearDown() = db.close()

    @Test
    fun `inserting the same message twice stores one row`() = runTest {
        val dao = db.rawMessageDao()
        val body = "Dear SUVASH, AC 0###15164761, NPR 8.00 withdrawn on 03/08/2026 11:32:05 for cIPS Fund Trf Charge"
        val msg = RawMessage(
            sender = "SBL_Alert", body = body, receivedAtEpochMillis = 1_754_000_000_000L,
            contentHash = contentHashOf("SBL_Alert", body, 1_754_000_000_000L)
        )
        assertNotEquals(-1L, dao.insertIgnoringDuplicates(msg))
        assertEquals(-1L, dao.insertIgnoringDuplicates(msg))
        assertEquals(1, dao.count())
    }
}
```

- [ ] **Step 2: Run and watch it fail**

Run: `./gradlew :data:connectedAndroidTest --tests '*DedupTest*'` (or via a connected device/emulator)
Expected: FAIL — unresolved reference `KharchaDatabase`.

- [ ] **Step 3: Implement schema**

Entities and DAOs as specified above. `insertIgnoringDuplicates` uses `@Insert(onConflict = OnConflictStrategy.IGNORE)`. `Converters` maps `Currency` and `Direction` enums to strings. Export the Room schema to `data/schemas` and commit it. Seed categories are added in Task 7, not here.

- [ ] **Step 4: Run and watch it pass**

Run: `./gradlew :data:connectedAndroidTest --tests '*DedupTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(data): add Room schema with content-hash dedup"
```

---

