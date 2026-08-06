package com.kharcha.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharcha.app.di.IoDispatcher
import com.kharcha.data.CategoryDao
import com.kharcha.data.CategoryEntity
import com.kharcha.data.ReparseService
import com.kharcha.data.RuleDao
import com.kharcha.data.RuleEntity
import com.kharcha.data.TransactionDao
import com.kharcha.data.TransactionEntity
import com.kharcha.parser.Currency
import com.kharcha.parser.Direction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import javax.inject.Inject

/**
 * Priority a manually-accepted "always categorize" rule is inserted with.
 * Must beat every seed rule (see [com.kharcha.data.SeedData.RULES], whose
 * highest priority is 100) so a user's explicit correction always wins.
 */
private const val MANUAL_RULE_PRIORITY = 1000

data class TransactionsUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val searchQuery: String = "",
    val categoryFilter: Long? = null,
    val dateRangeStartEpochMillis: Long? = null,
    val dateRangeEndEpochMillis: Long? = null,
) {
    /** Transactions after search/category/date filters are applied, newest first. */
    val filteredTransactions: List<TransactionEntity>
        get() = transactions.filter { txn ->
            val matchesQuery = searchQuery.isBlank() ||
                txn.remark.contains(searchQuery, ignoreCase = true) ||
                (txn.merchant?.contains(searchQuery, ignoreCase = true) == true)
            val matchesCategory = categoryFilter == null || txn.categoryId == categoryFilter
            val matchesStart = dateRangeStartEpochMillis == null ||
                txn.occurredAtEpochMillis >= dateRangeStartEpochMillis
            val matchesEnd = dateRangeEndEpochMillis == null ||
                txn.occurredAtEpochMillis <= dateRangeEndEpochMillis
            matchesQuery && matchesCategory && matchesStart && matchesEnd
        }
}

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val ruleDao: RuleDao,
    /**
     * The app's single definition of "which day did this transaction fall on".
     * Dashboard, Budgets and [com.kharcha.app.notify.BudgetNotifier] all use this
     * same Hilt-provided zone; the transactions list must not use a second one.
     */
    val zone: TimeZone,
    private val reparseService: ReparseService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val filters = MutableStateFlow(
        Triple<String, Long?, Pair<Long?, Long?>>("", null, null to null)
    )

    val state: StateFlow<TransactionsUiState> = combine(
        transactionDao.observeAll(),
        categoryDao.observeAll(),
        filters,
    ) { transactions, categories, (query, categoryFilter, dateRange) ->
        TransactionsUiState(
            transactions = transactions,
            categories = categories,
            searchQuery = query,
            categoryFilter = categoryFilter,
            dateRangeStartEpochMillis = dateRange.first,
            dateRangeEndEpochMillis = dateRange.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TransactionsUiState(),
    )

    fun setSearchQuery(query: String) {
        filters.value = filters.value.copy(first = query)
    }

    fun setCategoryFilter(categoryId: Long?) {
        filters.value = filters.value.copy(second = categoryId)
    }

    fun setDateRangeFilter(startEpochMillis: Long?, endEpochMillis: Long?) {
        filters.value = filters.value.copy(third = startEpochMillis to endEpochMillis)
    }

    fun setCategory(txnId: Long, categoryId: Long?) {
        viewModelScope.launch {
            val existing = transactionDao.getById(txnId) ?: return@launch
            transactionDao.update(
                existing.copy(categoryId = categoryId, categoryIsManualOverride = true)
            )
        }
    }

    fun setExcludedFromSpending(txnId: Long, excluded: Boolean) {
        viewModelScope.launch {
            val existing = transactionDao.getById(txnId) ?: return@launch
            transactionDao.update(existing.copy(excludedFromSpending = excluded))
        }
    }

    fun setMerchant(txnId: Long, merchant: String?) {
        viewModelScope.launch {
            val existing = transactionDao.getById(txnId) ?: return@launch
            transactionDao.update(existing.copy(merchant = merchant))
        }
    }

    fun deleteTransaction(txnId: Long) {
        viewModelScope.launch {
            val existing = transactionDao.getById(txnId) ?: return@launch
            transactionDao.delete(existing)
        }
    }

    /**
     * Called when the user accepts "Always categorize '<merchant>' as
     * <category>?". Inserts a prefix rule on the (SMS-length-limit-truncated)
     * merchant text at a priority above every seed rule, so it always wins — and then
     * re-parses history so the rule applies to the transactions the user already has,
     * not merely to future ones. Without that second step the user corrects a merchant,
     * accepts the prompt, and their 40 historical transactions from it stay
     * Uncategorized forever (spec success criterion 4).
     *
     * [ReparseService] leaves every `categoryIsManualOverride = true` row untouched, so
     * this cannot clobber a category the user set by hand. The whole rule-insert +
     * re-parse pass runs on [ioDispatcher], never the UI thread.
     */
    fun confirmAlwaysCategorize(merchant: String, categoryId: Long) {
        viewModelScope.launch(ioDispatcher) {
            ruleDao.insert(
                RuleEntity(
                    matchPattern = merchant,
                    matchesPrefix = true,
                    categoryId = categoryId,
                    priority = MANUAL_RULE_PRIORITY,
                )
            )
            reparseService.reparseAll()
        }
    }

    fun addManualTransaction(
        amountMinorUnits: Long,
        currency: Currency,
        direction: Direction,
        occurredAtEpochMillis: Long,
        remark: String,
        merchant: String?,
        categoryId: Long?,
        sourceAccount: String = "manual",
    ) {
        viewModelScope.launch {
            transactionDao.insert(
                TransactionEntity(
                    rawMessageId = null,
                    sourceAccount = sourceAccount,
                    amountMinorUnits = amountMinorUnits,
                    currency = currency,
                    direction = direction,
                    occurredAtEpochMillis = occurredAtEpochMillis,
                    remark = remark,
                    merchant = merchant,
                    balanceAfterMinorUnits = null,
                    categoryId = categoryId,
                    // Only a category the user actually picked counts as a manual
                    // override. A null categoryId means they left the picker alone, and
                    // flagging *that* as an override would freeze the row out of every
                    // future rule and every re-parse.
                    categoryIsManualOverride = categoryId != null,
                    excludedFromSpending = false,
                    isManualEntry = true,
                )
            )
        }
    }
}
