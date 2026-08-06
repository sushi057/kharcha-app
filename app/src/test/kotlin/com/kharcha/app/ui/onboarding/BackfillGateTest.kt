package com.kharcha.app.ui.onboarding

import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.kharcha.app.ingest.BackfillState
import com.kharcha.app.ingest.backfillDataStore
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Reviewer's cheap finding on `BackfillGate.kt:27`: a plain `OneTimeWorkRequest` with no
 * unique work name. The Kotlin-side [BackfillState] gate cannot cover a concurrent grant —
 * two permission-result callbacks landing close together each enqueued their own
 * `BackfillWorker`, and both would scan the whole inbox. A unique work name with
 * `ExistingWorkPolicy.KEEP` closes that at the WorkManager level.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackfillGateTest {

    private lateinit var context: android.content.Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    @Test
    fun `enqueueing twice leaves exactly one backfill work request`() {
        val gate = WorkManagerBackfillGate(context, BackfillState(context.backfillDataStore))

        gate.enqueueOnce()
        gate.enqueueOnce()

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WorkManagerBackfillGate.UNIQUE_WORK_NAME)
            .get()

        assertEquals(
            1,
            infos.size,
            "backfill must be enqueued as unique work so a concurrent grant cannot start two inbox scans",
        )
    }
}
