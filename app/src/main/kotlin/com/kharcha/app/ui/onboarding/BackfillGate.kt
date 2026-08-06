package com.kharcha.app.ui.onboarding

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.kharcha.app.ingest.BackfillState
import com.kharcha.app.ingest.BackfillWorker

/**
 * Thin seam between [PermissionViewModel] and WorkManager so "enqueue backfill on grant" is
 * a plain function call in tests, not a Robolectric-only WorkManager interaction.
 * [isComplete] delegates to [BackfillState] — the one source of truth for "has backfill ever
 * finished" (task 12 does not add a second flag, per the ledger's ruling).
 */
interface BackfillGate {
    suspend fun isComplete(): Boolean
    fun enqueueOnce()
}

class WorkManagerBackfillGate(
    private val context: Context,
    private val backfillState: BackfillState,
) : BackfillGate {
    override suspend fun isComplete(): Boolean = backfillState.isComplete()

    /**
     * Unique work, not a bare enqueue. [BackfillState] is a Kotlin-side gate and cannot
     * cover a concurrent grant — two permission-result callbacks landing close together
     * each enqueued their own [BackfillWorker], and both would scan the whole inbox.
     * [ExistingWorkPolicy.KEEP] makes WorkManager itself the arbiter: the second request
     * is dropped while the first is still pending or running.
     */
    override fun enqueueOnce() {
        val request = OneTimeWorkRequestBuilder<BackfillWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "sms-backfill"
    }
}
