package com.kharcha.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kharcha.parser.SblAlertRuleset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The brief specifies this as an instrumented test (`connectedAndroidTest`),
 * but there is no device or emulator in this environment. It is written as
 * a Robolectric test instead, in-memory Room, matching DedupTest's setup;
 * the assertions and intent are unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReparseTest {
    private lateinit var db: KharchaDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), KharchaDatabase::class.java
        ).addCallback(KharchaDatabase.seedCallback).build()
    }

    @After fun tearDown() = db.close()

    @Test
    fun `reparse preserves manual category overrides`() = runTest {
        val rawDao = db.rawMessageDao()
        val txnDao = db.transactionDao()
        val categoryDao = db.categoryDao()

        // Give the categorizer a "dining" category to manually assign, plus
        // the categoryId the parser would auto-select for a plain QR payment
        // (none of the seed rules match, so auto-categorization yields null).
        val diningId = categoryDao.insert(
            CategoryEntity(name = "Test Dining", colorArgb = 0xFFAA5533.toInt(), isIncome = false, isFee = false)
        )

        // Ingest a QR payment SMS.
        val body = "Dear SUVASH, AC 0###15164761, NPR 250.00 withdrawn on 03/08/2026 11:32:05 " +
            "for QR Payment to JAWALAKHEL HANKOOK SARANG RESTAU"
        val receivedAt = 1_754_000_000_000L
        val raw = RawMessage(
            sender = "SBL_Alert", body = body, receivedAtEpochMillis = receivedAt,
            contentHash = contentHashOf("SBL_Alert", body, receivedAt)
        )
        val rawId = rawDao.insertIgnoringDuplicates(raw)

        val reparseService = ReparseService(rawDao, txnDao, SblAlertRuleset, Categorizer(emptyList()))

        // First pass creates the transaction (auto-assigned category = null,
        // since no seed rule matches this remark).
        reparseService.reparseAll()

        val created = txnDao.getByRawMessageId(rawId)
        requireNotNull(created)

        // User manually recategorizes it as dining.
        txnDao.update(created.copy(categoryId = diningId, categoryIsManualOverride = true))

        // Re-parse again (e.g. because rules changed). The manual override
        // must survive, and no duplicate transaction should be created.
        reparseService.reparseAll()
        reparseService.reparseAll()

        val afterReparse = txnDao.getByRawMessageId(rawId)
        requireNotNull(afterReparse)
        assertEquals(diningId, afterReparse.categoryId)
        assertTrue(afterReparse.categoryIsManualOverride)

        val all = txnDao.observeAll().first()
        assertEquals(1, all.size)
    }
}
