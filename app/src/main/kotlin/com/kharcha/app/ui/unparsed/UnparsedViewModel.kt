package com.kharcha.app.ui.unparsed

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharcha.app.export.ExportWriter
import com.kharcha.app.export.RawMessageExporter
import com.kharcha.app.ui.onboarding.BackfillGate
import com.kharcha.app.ui.settings.ExportState
import com.kharcha.data.CategoryDao
import com.kharcha.data.CategoryEntity
import com.kharcha.data.RawMessage
import com.kharcha.data.RawMessageDao
import com.kharcha.data.TransactionDao
import com.kharcha.data.TransactionEntity
import com.kharcha.parser.Currency
import com.kharcha.parser.Direction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

data class UnparsedUiState(
    val needsReview: List<RawMessage> = emptyList(),
    val ignored: List<RawMessage> = emptyList(),
    /** Messages the user waved away from review — kept visible so a mis-tap is reversible. */
    val dismissed: List<RawMessage> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val lastSyncAtEpochMillis: Long? = null,
    val isSyncing: Boolean = false,
) {
    // For backwards compatibility in tests
    val messages: List<RawMessage> get() = needsReview
}

/**
 * Backs the inbox: raw `SBL_Alert` messages split into two sections:
 * - Needs review: messages the parser didn't recognize (ParseResult.Unrecognized)
 * - Ignored: messages the parser rejected as irrelevant (ParseResult.Ignored with reason)
 *
 * The screen offers "add as transaction", which reuses the shared manual-entry sheet
 * ([com.kharcha.app.ui.transactions.TransactionEditSheet]) prefilled from the raw body,
 * "dismiss" for needs-review messages, and "restore" for ignored messages (in case they
 * were wrongly classified).
 *
 * Sync re-scans the SMS inbox for new SBL_Alert messages using the ingest machinery.
 */
@HiltViewModel
class UnparsedViewModel @Inject constructor(
    private val rawMessageDao: RawMessageDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val backfillGate: BackfillGate,
) : ViewModel() {

    private val syncState = MutableStateFlow<Long?>(null) // Last sync timestamp
    private val isSyncing = MutableStateFlow(false)

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val exporter = RawMessageExporter()
    private val zone = TimeZone.currentSystemDefault()

    val state: StateFlow<UnparsedUiState> = combine(
        rawMessageDao.observeUnparsed(),
        rawMessageDao.observeIgnored(),
        rawMessageDao.observeDismissed(),
        categoryDao.observeAll(),
        combine(syncState, isSyncing) { lastSync, syncing -> lastSync to syncing },
    ) { needsReview, ignored, dismissed, categories, (lastSync, syncing) ->
        UnparsedUiState(
            needsReview = needsReview,
            ignored = ignored,
            dismissed = dismissed,
            categories = categories,
            lastSyncAtEpochMillis = lastSync,
            isSyncing = syncing,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = UnparsedUiState(),
    )

    fun dismiss(rawId: Long) {
        viewModelScope.launch {
            rawMessageDao.markDismissed(rawId)
        }
    }

    /** Puts a dismissed message back into "needs review". */
    fun undismiss(rawId: Long) {
        viewModelScope.launch {
            rawMessageDao.undismiss(rawId)
        }
    }

    fun restore(rawId: Long) {
        viewModelScope.launch {
            rawMessageDao.restore(rawId)
        }
    }

    /**
     * Re-scans the SMS inbox through [BackfillGate], which owns the ingest machinery, and
     * only records the sync time once that scan has actually finished. Anything the scan
     * imports reaches the UI through the DAO flows this state is built from, so there is
     * nothing to refresh here by hand.
     */
    fun sync() {
        viewModelScope.launch {
            isSyncing.value = true
            try {
                backfillGate.rescan()
                syncState.value = System.currentTimeMillis()
            } finally {
                isSyncing.value = false
            }
        }
    }

    /**
     * Suggested filename for the raw-message dump, e.g. `kharcha-messages-2026-08-06.json`.
     */
    fun exportFilename(nowEpochMillis: Long = System.currentTimeMillis()): String {
        val date = Instant.fromEpochMilliseconds(nowEpochMillis).toLocalDateTime(zone).date
        return "kharcha-messages-$date.json"
    }

    /**
     * Dumps every raw message — needs-review, ignored, dismissed and already-parsed alike —
     * to the location the user picked, for off-device debugging of the parser.
     */
    fun exportAllMessagesTo(context: Context, targetUri: Uri) {
        _exportState.value = ExportState.InProgress
        viewModelScope.launch {
            try {
                val messages = rawMessageDao.getAll()
                val content = withContext(Dispatchers.Default) {
                    exporter.toJson(messages, zone)
                }
                ExportWriter(context).write(targetUri, content, EXPORT_MIME_TYPE)
                _exportState.value = ExportState.Success("Exported ${messages.size} messages")
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(
                    "Export failed: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    fun clearExportState() {
        _exportState.value = ExportState.Idle
    }

    /**
     * Inserts a manual transaction linked to [rawId], using the values the user confirmed
     * in the prefilled manual-entry sheet. Linking `rawMessageId` is what removes the
     * message from the unparsed inbox — see [RawMessageDao.observeUnparsed] — no separate
     * "resolved" flag is needed.
     */
    fun createManualTransactionFrom(
        rawId: Long,
        amountMinorUnits: Long,
        merchant: String,
        remark: String,
        categoryId: Long?,
        currency: Currency = Currency.NPR,
        direction: Direction = Direction.DEBIT,
        occurredAtEpochMillis: Long = System.currentTimeMillis(),
    ) {
        viewModelScope.launch {
            transactionDao.insert(
                TransactionEntity(
                    rawMessageId = rawId,
                    sourceAccount = "manual",
                    amountMinorUnits = amountMinorUnits,
                    currency = currency,
                    direction = direction,
                    occurredAtEpochMillis = occurredAtEpochMillis,
                    remark = remark,
                    merchant = merchant,
                    balanceAfterMinorUnits = null,
                    categoryId = categoryId,
                    // See TransactionsViewModel.addManualTransaction: null means "the user
                    // never chose", which must not be recorded as a manual override.
                    categoryIsManualOverride = categoryId != null,
                    excludedFromSpending = false,
                    isManualEntry = true,
                )
            )
        }
    }

    private companion object {
        const val EXPORT_MIME_TYPE = "application/json"
    }
}
