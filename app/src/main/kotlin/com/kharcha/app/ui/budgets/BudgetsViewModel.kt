package com.kharcha.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharcha.app.dashboard.DashboardAggregator
import com.kharcha.data.BudgetDao
import com.kharcha.data.BudgetEntity
import com.kharcha.data.CategoryDao
import com.kharcha.data.TransactionDao
import com.kharcha.parser.Currency
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/**
 * One row per (non-income, non-fee category, currency-with-spend-or-budget) — matching
 * [DashboardAggregator.byCategory]'s own keying, so a category with spend in both NPR and
 * USD surfaces two independent rows rather than one currency silently overwriting the
 * other. [limitMinorUnits] is `null` when this particular currency has no budget yet —
 * that's a normal, unconfigured state, not an error, and a budget in one currency never
 * applies to another currency's spend for the same category. [isOverBudget] is surfaced
 * separately from color (ruling: "over-budget must be signalled by an explicit label, not
 * color alone") so [BudgetsScreen] can render an explicit "Over budget" label.
 *
 * [last6MonthsHistory] contains spend in minor units for this (category, currency) pair
 * for the last 6 months (oldest first), used for the history chart and budget suggestion.
 */
data class BudgetRow(
    val categoryId: Long,
    val categoryName: String,
    val colorArgb: Int,
    val currency: Currency,
    val budgetId: Long?,
    val limitMinorUnits: Long?,
    val spentMinorUnits: Long,
    val alertThresholdPercent: Int,
    val last6MonthsHistory: List<Long> = emptyList(),
) {
    val isOverBudget: Boolean get() = limitMinorUnits != null && spentMinorUnits >= limitMinorUnits
}

/**
 * Summary data for the overall budgets card:
 * - [totalSpentMinorUnits]: sum of all currency-weighted spend this month
 * - [totalBudgetedMinorUnits]: sum of all budgets (if any) in the user's primary currency (NPR)
 * - [today]: current date, used for pace calculation and days-remaining
 * - [monthStart]: first day of the current month, used for pace calculation
 */
data class BudgetsSummary(
    val totalSpentMinorUnits: Long = 0L,
    val totalBudgetedMinorUnits: Long = 0L,
    val today: LocalDate? = null,
    val monthStart: LocalDate? = null,
)

data class BudgetsUiState(
    val rows: List<BudgetRow> = emptyList(),
    val summary: BudgetsSummary = BudgetsSummary(),
)

/**
 * Drives the budgets screen: one row per spendable (category, currency) pair, merging its
 * current-month spend (via [DashboardAggregator], reused rather than reimplemented — same
 * month boundary as the dashboard, ruling 2) with its [BudgetEntity] if one exists for that
 * currency. [clock] and [zone] are injected for deterministic tests, matching
 * [com.kharcha.app.ui.dashboard.DashboardViewModel].
 */
@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgetDao: BudgetDao,
    categoryDao: CategoryDao,
    transactionDao: TransactionDao,
    private val clock: Clock,
    private val zone: TimeZone,
) : ViewModel() {

    val state: StateFlow<BudgetsUiState> = combine(
        budgetDao.observeAll(),
        categoryDao.observeAll(),
        transactionDao.observeAll(),
    ) { budgets, categories, transactions ->
        val today = clock.now().toLocalDateTime(zone).date
        val monthStart = LocalDate(today.year, today.monthNumber, 1)
        val monthEndExclusive = monthStart.plus(1, DateTimeUnit.MONTH)

        val aggregate = DashboardAggregator.aggregate(
            transactions = transactions,
            categories = categories,
            monthStartEpochMillis = monthStart.atStartOfDayIn(zone).toEpochMilliseconds(),
            monthEndExclusiveEpochMillis = monthEndExclusive.atStartOfDayIn(zone).toEpochMilliseconds(),
            zone = zone,
        )
        val spendByCategoryAndCurrency = aggregate.byCategory.associateBy { it.categoryId to it.currency }

        // Six-month spend history per (category, currency) pair, oldest first.
        //
        // Every pair gets exactly HISTORY_MONTHS entries, with an explicit zero for a
        // month that saw no spend. Appending only the months that *had* spend would both
        // shorten the list and — because the history chart's bars are positional — shift
        // every remaining bar onto the wrong month.
        val monthlyAggregates = (HISTORY_MONTHS - 1 downTo 0).map { monthOffset ->
            val historyMonthStart = monthStart.plus(-monthOffset, DateTimeUnit.MONTH)
            val historyMonthEnd = historyMonthStart.plus(1, DateTimeUnit.MONTH)
            DashboardAggregator.aggregate(
                transactions = transactions,
                categories = categories,
                monthStartEpochMillis = historyMonthStart.atStartOfDayIn(zone).toEpochMilliseconds(),
                monthEndExclusiveEpochMillis = historyMonthEnd.atStartOfDayIn(zone).toEpochMilliseconds(),
                zone = zone,
            )
        }

        val monthlyHistory: Map<Pair<Long, Currency>, List<Long>> = monthlyAggregates
            .flatMap { aggregate ->
                aggregate.byCategory.mapNotNull { spend ->
                    spend.categoryId?.let { it to spend.currency }
                }
            }
            .toSet()
            .associateWith { key ->
                monthlyAggregates.map { aggregate ->
                    aggregate.byCategory
                        .firstOrNull { it.categoryId == key.first && it.currency == key.second }
                        ?.total?.minorUnits
                        ?: 0L
                }
            }

        val rows = categories
            .filter { !it.isIncome && !it.isFee }
            .flatMap { category ->
                val budget = budgets.find { it.categoryId == category.id }
                // One row per currency that actually has spend this month for this
                // category (matching DashboardAggregator.byCategory's own keying — never
                // sum or convert across currencies), plus the budget's own currency even
                // if that currency has zero spend so far, so a freshly-set budget doesn't
                // disappear before any spend lands in it. A category with neither spend
                // nor a budget still gets one empty NPR row, matching prior behaviour for
                // an entirely unused category.
                val spendCurrencies = aggregate.byCategory
                    .filter { it.categoryId == category.id }
                    .map { it.currency }
                    .toSet()
                val currencies = (spendCurrencies + listOfNotNull(budget?.currency))
                    .ifEmpty { setOf(Currency.NPR) }

                currencies.map { currency ->
                    val spentMinorUnits = spendByCategoryAndCurrency[category.id to currency]?.total?.minorUnits ?: 0L
                    // A budget only ever applies to its own currency — a category with an
                    // NPR budget but USD spend must not show the NPR limit against the USD
                    // spend row.
                    val budgetForCurrency = budget?.takeIf { it.currency == currency }
                    BudgetRow(
                        categoryId = category.id,
                        categoryName = category.name,
                        colorArgb = category.colorArgb,
                        currency = currency,
                        budgetId = budgetForCurrency?.id,
                        limitMinorUnits = budgetForCurrency?.monthlyLimitMinorUnits,
                        spentMinorUnits = spentMinorUnits,
                        alertThresholdPercent = budgetForCurrency?.alertThresholdPercent ?: DEFAULT_THRESHOLD_PERCENT,
                        last6MonthsHistory = monthlyHistory[category.id to currency] ?: List(HISTORY_MONTHS) { 0L },
                    )
                }
            }
            .sortedWith(compareBy({ it.categoryName }, { it.currency.name }))

        // Compute summary: total spent (NPR only) and total budgeted (NPR only)
        val totalSpent = rows
            .filter { it.currency == Currency.NPR }
            .sumOf { it.spentMinorUnits }
        val totalBudgeted = rows
            .filter { it.currency == Currency.NPR && it.limitMinorUnits != null }
            .sumOf { it.limitMinorUnits ?: 0L }

        BudgetsUiState(
            rows = rows,
            summary = BudgetsSummary(
                totalSpentMinorUnits = totalSpent,
                totalBudgetedMinorUnits = totalBudgeted,
                today = today,
                monthStart = monthStart,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = BudgetsUiState(),
    )

    /**
     * Inserts a new budget for (categoryId, currency), or updates the existing one for that
     * same currency — never duplicates it, and never clobbers a budget in a *different*
     * currency for the same category. Matching by `categoryId` alone would be wrong now
     * that a category can have independent rows per currency (e.g. an NPR budget plus a
     * USD-spend row with no budget yet): editing the USD row must not overwrite the NPR
     * budget entity by mistake.
     */
    fun setBudget(categoryId: Long, limitMinorUnits: Long, currency: Currency, alertThresholdPercent: Int) {
        viewModelScope.launch {
            val existing = budgetDao.observeAll().first()
                .find { it.categoryId == categoryId && it.currency == currency }
            if (existing != null) {
                budgetDao.update(
                    existing.copy(
                        monthlyLimitMinorUnits = limitMinorUnits,
                        currency = currency,
                        alertThresholdPercent = alertThresholdPercent,
                    )
                )
            } else {
                budgetDao.insert(
                    BudgetEntity(
                        categoryId = categoryId,
                        monthlyLimitMinorUnits = limitMinorUnits,
                        currency = currency,
                        alertThresholdPercent = alertThresholdPercent,
                    )
                )
            }
        }
    }

    fun deleteBudget(budgetId: Long) {
        viewModelScope.launch {
            budgetDao.getById(budgetId)?.let { budgetDao.delete(it) }
        }
    }

    companion object {
        const val DEFAULT_THRESHOLD_PERCENT = 80

        /** Months of spend history shown per category, including the current one. */
        const val HISTORY_MONTHS = 6
    }
}
