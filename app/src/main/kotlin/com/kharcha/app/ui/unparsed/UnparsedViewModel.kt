package com.kharcha.app.ui.unparsed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharcha.data.CategoryDao
import com.kharcha.data.CategoryEntity
import com.kharcha.data.RawMessage
import com.kharcha.data.RawMessageDao
import com.kharcha.data.TransactionDao
import com.kharcha.data.TransactionEntity
import com.kharcha.parser.Currency
import com.kharcha.parser.Direction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UnparsedUiState(
    val messages: List<RawMessage> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
)

/**
 * Backs the unparsed inbox: raw `SBL_Alert` messages the parser didn't recognize (not an
 * ignored OTP/purchase-code, no linked transaction yet, not dismissed — see
 * [RawMessageDao.observeUnparsed] for the authoritative query). The screen offers "add as
 * transaction", which reuses the shared manual-entry sheet
 * ([com.kharcha.app.ui.transactions.TransactionEditSheet]) prefilled from the raw body, and
 * "dismiss", which hides a message without creating a transaction.
 */
@HiltViewModel
class UnparsedViewModel @Inject constructor(
    private val rawMessageDao: RawMessageDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
) : ViewModel() {

    val state: StateFlow<UnparsedUiState> = combine(
        rawMessageDao.observeUnparsed(),
        categoryDao.observeAll(),
    ) { messages, categories ->
        UnparsedUiState(messages = messages, categories = categories)
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
                    categoryIsManualOverride = categoryId != null,
                    excludedFromSpending = false,
                    isManualEntry = true,
                )
            )
        }
    }
}
