package com.kharcha.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
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
