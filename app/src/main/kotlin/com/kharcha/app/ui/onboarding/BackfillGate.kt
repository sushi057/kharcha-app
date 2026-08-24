package com.kharcha.app.ui.onboarding

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.kharcha.app.ingest.BackfillState
import com.kharcha.app.ingest.BackfillWorker
import kotlinx.coroutines.flow.first

/**
 * Thin seam between [PermissionViewModel] and WorkManager so "enqueue backfill on grant" is
 * a plain function call in tests, not a Robolectric-only WorkManager interaction.
 * [isComplete] delegates to [BackfillState] — the one source of truth for "has backfill ever
 * finished" (task 12 does not add a second flag, per the ledger's ruling).
 */
interface BackfillGate {
    suspend fun isComplete(): Boolean
    fun enqueueOnce()

    /**
     * Runs a fresh scan of the SMS inbox even though the first-run import already
     * completed, and suspends until it finishes. Backs the inbox's "Sync now" action,
     * which needs to report when the scan is actually done, not when it was enqueued.
     */
    suspend fun rescan()
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

    /**
     * Shares [UNIQUE_WORK_NAME] with [enqueueOnce] and keeps [ExistingWorkPolicy.KEEP], so
     * a manual sync that lands while the first-run import is still running joins that run
     * instead of starting a second scan of the same inbox. Awaiting the enqueue before
     * watching the work state matters: the state flow would otherwise report the previous,
     * already-finished run and return immediately.
     */
    override suspend fun rescan() {
        val request = OneTimeWorkRequestBuilder<BackfillWorker>()
            .setInputData(workDataOf(BackfillWorker.KEY_FORCE to true))
            .build()
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
            .result.await()
        workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME)
            .first { infos -> infos.isNotEmpty() && infos.all { it.state.isFinished } }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "sms-backfill"
    }
}
