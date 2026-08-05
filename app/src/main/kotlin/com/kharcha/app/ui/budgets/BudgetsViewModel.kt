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
) {
    val isOverBudget: Boolean get() = limitMinorUnits != null && spentMinorUnits >= limitMinorUnits
}

data class BudgetsUiState(val rows: List<BudgetRow> = emptyList())

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
                    )
                }
            }
            .sortedWith(compareBy({ it.categoryName }, { it.currency.name }))

        BudgetsUiState(rows)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = BudgetsUiState(),
    )

    /** Inserts a new budget for [categoryId], or updates the existing one — never duplicates it. */
    fun setBudget(categoryId: Long, limitMinorUnits: Long, currency: Currency, alertThresholdPercent: Int) {
        viewModelScope.launch {
            val existing = budgetDao.observeAll().first().find { it.categoryId == categoryId }
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
    }
}
