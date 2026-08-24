package com.kharcha.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharcha.app.di.IoDispatcher
import com.kharcha.data.CategoryDao
import com.kharcha.data.CategoryEntity
import com.kharcha.app.ui.theme.formatMinorUnitsPlain
import com.kharcha.data.RawMessageDao
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import javax.inject.Inject

/**
 * Priority a manually-accepted "always categorize" rule is inserted with.
 * Must beat every seed rule (see [com.kharcha.data.SeedData.RULES], whose
 * highest priority is 100) so a user's explicit correction always wins.
 */
private const val MANUAL_RULE_PRIORITY = 1000

/**
 * How the ledger is ordered. [Newest]/[Oldest] keep the list a diary — rows grouped
 * under the day they happened on. [Highest]/[Lowest] make it a ranking, which is a
 * different question ("what were my big ones?") and is drawn as a flat list, because
 * day headers over an amount-sorted list would show one row per header.
 */
enum class TransactionSort(val label: String) {
    Newest("Newest first"),
    Oldest("Oldest first"),
    Highest("Highest amount"),
    Lowest("Lowest amount"),
    ;

    /** True when rows should stay grouped under day headers. */
    val isChronological: Boolean get() = this == Newest || this == Oldest
}

data class TransactionsUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val searchQuery: String = "",
    val categoryFilter: Long? = null,
    val dateRangeStartEpochMillis: Long? = null,
    val dateRangeEndEpochMillis: Long? = null,
    val sort: TransactionSort = TransactionSort.Newest,
    /** When on, shows only the rows excluded from spending — the ones you waved off. */
    val excludedOnly: Boolean = false,
    /** Raw SMS body per `rawMessageId`, so the edit sheet can show the message itself. */
    val rawBodiesById: Map<Long, String> = emptyMap(),
) {
    /** Transactions after search/category/date filters are applied, in [sort] order. */
    val filteredTransactions: List<TransactionEntity>
        get() = transactions.filter { txn ->
            val matchesQuery = searchQuery.isBlank() ||
                txn.remark.contains(searchQuery, ignoreCase = true) ||
                (txn.merchant?.contains(searchQuery, ignoreCase = true) == true) ||
                formatMinorUnitsPlain(txn.amountMinorUnits).startsWith(searchQuery.trim())
            val matchesCategory = categoryFilter == null || txn.categoryId == categoryFilter
            val matchesStart = dateRangeStartEpochMillis == null ||
                txn.occurredAtEpochMillis >= dateRangeStartEpochMillis
            val matchesEnd = dateRangeEndEpochMillis == null ||
                txn.occurredAtEpochMillis <= dateRangeEndEpochMillis
            val matchesExcluded = !excludedOnly || txn.excludedFromSpending
            matchesQuery && matchesCategory && matchesStart && matchesEnd && matchesExcluded
        }.let { filtered ->
            when (sort) {
                // The DAO already returns newest-first; re-sorting explicitly means the
                // order is a property of this state rather than of the query behind it.
                TransactionSort.Newest -> filtered.sortedByDescending { it.occurredAtEpochMillis }
                TransactionSort.Oldest -> filtered.sortedBy { it.occurredAtEpochMillis }
                TransactionSort.Highest -> filtered.sortedByDescending { it.amountMinorUnits }
                TransactionSort.Lowest -> filtered.sortedBy { it.amountMinorUnits }
            }
        }

    /** True when anything other than the default ordering is narrowing the list. */
    val hasActiveFilters: Boolean
        get() = categoryFilter != null || excludedOnly || sort != TransactionSort.Newest ||
            dateRangeStartEpochMillis != null || dateRangeEndEpochMillis != null
}

/** Every knob the ledger view has, in one value so a change to one cannot drop another. */
private data class TransactionFilters(
    val query: String = "",
    val categoryId: Long? = null,
    val dateRangeStart: Long? = null,
    val dateRangeEnd: Long? = null,
    val sort: TransactionSort = TransactionSort.Newest,
    val excludedOnly: Boolean = false,
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val rawMessageDao: RawMessageDao,
    private val ruleDao: RuleDao,
    private val openDayRequests: OpenDayRequests,
    /**
     * The app's single definition of "which day did this transaction fall on".
     * Dashboard, Budgets and [com.kharcha.app.notify.BudgetNotifier] all use this
     * same Hilt-provided zone; the transactions list must not use a second one.
     */
    val zone: TimeZone,
    private val reparseService: ReparseService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val filters = MutableStateFlow(TransactionFilters())

    init {
        // A day tapped on the dashboard's spend chart arrives here. Applying it in the
        // ViewModel rather than through a nav argument keeps Transactions a single
        // bottom-nav destination whose back-stack state survives the jump.
        viewModelScope.launch {
            openDayRequests.requests.collect { request ->
                if (request != null) {
                    val start = request.date.atStartOfDayIn(zone).toEpochMilliseconds()
                    val end = request.date.plus(1, DateTimeUnit.DAY)
                        .atStartOfDayIn(zone).toEpochMilliseconds() - 1
                    filters.value = TransactionFilters(dateRangeStart = start, dateRangeEnd = end)
                    openDayRequests.consume()
                }
            }
        }
    }

    val state: StateFlow<TransactionsUiState> = combine(
        transactionDao.observeAll(),
        categoryDao.observeAll(),
        rawMessageDao.observeAll(),
        filters,
    ) { transactions, categories, rawMessages, filters ->
        TransactionsUiState(
            transactions = transactions,
            categories = categories,
            searchQuery = filters.query,
            categoryFilter = filters.categoryId,
            dateRangeStartEpochMillis = filters.dateRangeStart,
            dateRangeEndEpochMillis = filters.dateRangeEnd,
            sort = filters.sort,
            excludedOnly = filters.excludedOnly,
            rawBodiesById = rawMessages.associate { it.id to it.body },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TransactionsUiState(),
    )

    fun setSearchQuery(query: String) {
        filters.value = filters.value.copy(query = query)
    }

    fun setCategoryFilter(categoryId: Long?) {
        filters.value = filters.value.copy(categoryId = categoryId)
    }

    fun setDateRangeFilter(startEpochMillis: Long?, endEpochMillis: Long?) {
        filters.value = filters.value.copy(
            dateRangeStart = startEpochMillis,
            dateRangeEnd = endEpochMillis,
        )
    }

    fun setSort(sort: TransactionSort) {
        filters.value = filters.value.copy(sort = sort)
    }

    fun setExcludedOnly(excludedOnly: Boolean) {
        filters.value = filters.value.copy(excludedOnly = excludedOnly)
    }

    /** Drops every filter and sort back to the default view of the ledger. */
    fun clearFilters() {
        filters.value = TransactionFilters(query = filters.value.query)
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
