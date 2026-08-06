package com.kharcha.app.dashboard

import com.kharcha.app.ui.theme.KharchaNeutrals
import com.kharcha.data.CategoryEntity
import com.kharcha.data.TransactionEntity
import com.kharcha.parser.Currency
import com.kharcha.parser.Direction
import com.kharcha.parser.Money
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Label shown for transactions with a null `categoryId` — see ruling 3 in the task brief. */
const val UNCATEGORIZED_CATEGORY_NAME = "Uncategorized"

data class CategorySpend(
    val categoryId: Long?,
    val categoryName: String,
    val colorArgb: Int,
    val currency: Currency,
    val total: Money,
)

data class DailySpend(
    val date: LocalDate,
    val currency: Currency,
    val total: Money,
)

data class MerchantSpend(
    val merchant: String,
    val currency: Currency,
    val total: Money,
)

data class DashboardAggregate(
    val monthToDateSpend: Map<Currency, Money>,
    val byCategory: List<CategorySpend>,
    val trend: List<DailySpend>,
    val topMerchants: List<MerchantSpend>,
)

/**
 * Pure aggregation over transactions for the dashboard. Deliberately kept free
 * of ViewModel/Flow plumbing so it is directly unit-testable and reusable by
 * Task 11 (budgets), which needs the same month-to-date discretionary spend.
 *
 * Discretionary spend excludes: transactions flagged `excludedFromSpending`,
 * all credits, and any transaction whose category is flagged `isIncome` or
 * `isFee`. A null `categoryId` still counts as spend, bucketed as
 * [UNCATEGORIZED_CATEGORY_NAME]. Currencies are never summed together.
 */
object DashboardAggregator {

    /** The neutral ramp's mid step, not a second copy of its hex value. */
    private val UNCATEGORIZED_COLOR_ARGB = KharchaNeutrals.Neutral50
    private const val DEFAULT_TOP_MERCHANT_COUNT = 5

    fun aggregate(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        monthStartEpochMillis: Long,
        monthEndExclusiveEpochMillis: Long,
        zone: TimeZone,
        topMerchantCount: Int = DEFAULT_TOP_MERCHANT_COUNT,
    ): DashboardAggregate {
        val categoriesById = categories.associateBy { it.id }

        val monthTransactions = transactions.filter {
            it.occurredAtEpochMillis >= monthStartEpochMillis &&
                it.occurredAtEpochMillis < monthEndExclusiveEpochMillis
        }

        val eligible = monthTransactions.filter { txn ->
            !txn.excludedFromSpending &&
                txn.direction == Direction.DEBIT &&
                txn.categoryId?.let { categoriesById[it] }?.let { !it.isIncome && !it.isFee } ?: true
        }

        // Currencies are keyed off every transaction seen this month, not just
        // eligible ones, so an all-income/all-excluded month still reports an
        // explicit zero for that currency rather than omitting it entirely.
        val currenciesThisMonth = monthTransactions.map { it.currency }.toSet()
        val monthToDateSpend = currenciesThisMonth.associateWith { currency ->
            Money(
                eligible.filter { it.currency == currency }.sumOf { it.amountMinorUnits },
                currency,
            )
        }

        val byCategory = eligible
            .groupBy { it.categoryId to it.currency }
            .map { (key, txns) ->
                val (categoryId, currency) = key
                val category = categoryId?.let { categoriesById[it] }
                CategorySpend(
                    categoryId = categoryId,
                    categoryName = category?.name ?: UNCATEGORIZED_CATEGORY_NAME,
                    colorArgb = category?.colorArgb ?: UNCATEGORIZED_COLOR_ARGB,
                    currency = currency,
                    total = Money(txns.sumOf { it.amountMinorUnits }, currency),
                )
            }
            .sortedByDescending { it.total.minorUnits }

        val trend = eligible
            .groupBy { txn ->
                val date = Instant.fromEpochMilliseconds(txn.occurredAtEpochMillis)
                    .toLocalDateTime(zone).date
                date to txn.currency
            }
            .map { (key, txns) ->
                val (date, currency) = key
                DailySpend(date, currency, Money(txns.sumOf { it.amountMinorUnits }, currency))
            }
            .sortedWith(compareBy({ it.currency.name }, { it.date.toString() }))

        val topMerchants = eligible
            .filter { !it.merchant.isNullOrBlank() }
            .groupBy { (it.merchant as String) to it.currency }
            .map { (key, txns) ->
                val (merchant, currency) = key
                MerchantSpend(merchant, currency, Money(txns.sumOf { it.amountMinorUnits }, currency))
            }
            .sortedByDescending { it.total.minorUnits }
            .groupBy { it.currency }
            .flatMap { (_, list) -> list.take(topMerchantCount) }

        return DashboardAggregate(monthToDateSpend, byCategory, trend, topMerchants)
    }
}
