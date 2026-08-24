package com.kharcha.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharcha.app.dashboard.CategorySpend
import com.kharcha.app.dashboard.DailySpend
import com.kharcha.app.dashboard.DashboardAggregator
import com.kharcha.app.dashboard.Insight
import com.kharcha.app.dashboard.InsightGenerator
import com.kharcha.app.dashboard.RecurringCharge
import com.kharcha.app.dashboard.RecurringDetector
import com.kharcha.data.CategoryDao
import com.kharcha.data.TransactionDao
import com.kharcha.parser.Currency
import com.kharcha.parser.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

data class DashboardUiState(
    /**
     * Defaults to the epoch only because a data class needs *some* default; the real
     * initial value is built from the clock in [DashboardViewModel.state] so the app
     * bar never flashes "January 1970" on the frame before the DAO's first emission.
     */
    val currentMonth: LocalDate = LocalDate(1970, 1, 1),
    val monthToDateSpend: Money = Money(0L, Currency.NPR),
    val monthToDateIncome: Money = Money(0L, Currency.NPR),
    val byCategory: List<CategorySpend> = emptyList(),
    val trend: List<DailySpend> = emptyList(),
    val recurringCharges: List<RecurringCharge> = emptyList(),
    val insights: List<Insight> = emptyList(),
    /**
     * The same span of days in the previous month — the 1st to the 6th, if today
     * is the 6th. Comparing a partial month against a whole one is the classic
     * way to make every month look like an improvement until the 28th.
     */
    val previousPeriodSpend: Money = Money(0L, Currency.NPR),
    /** How far into the month the data runs: 6, if today is the 6th. */
    val daysElapsed: Int = 0,
    /** Running total of spend, one entry per elapsed day — the hero sparkline. */
    val cumulativeSpend: List<Long> = emptyList(),
    /** The last day covered by the comparison, for the caption under the delta. */
    val comparisonCutoff: LocalDate? = null,
) {
    /**
     * Percent change against [previousPeriodSpend], or null when there is nothing
     * to compare against — a first month, or a previous period with no spend at
     * all, where "+∞%" would be technically true and completely useless.
     */
    val deltaPercent: Int?
        get() {
            val previous = previousPeriodSpend.minorUnits
            if (previous <= 0L) return null
            val change = (monthToDateSpend.minorUnits - previous) * 100 / previous
            return change.toInt()
        }
}

/**
 * Drives the dashboard. Month-to-date is "the current calendar month in the
 * device's local timezone" (ruling 1) — [clock] and [zone] are injected
 * rather than read from [Clock.System]/[TimeZone.currentSystemDefault]
 * directly so tests are deterministic.
 *
 * The dashboard shows NPR spend only; USD is visible in Transactions.
 * Supports month navigation via [previousMonth] and [nextMonth] methods.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    transactionDao: TransactionDao,
    categoryDao: CategoryDao,
    private val clock: Clock,
    private val zone: TimeZone,
) : ViewModel() {

    private val selectedMonth: MutableStateFlow<LocalDate> = MutableStateFlow(
        clock.now().toLocalDateTime(zone).date.let { LocalDate(it.year, it.monthNumber, 1) }
    )

    val state: StateFlow<DashboardUiState> = combine(
        transactionDao.observeAll(),
        categoryDao.observeAll(),
        selectedMonth,
    ) { transactions, categories, monthStart ->
        val monthEndExclusive = monthStart.plus(1, DateTimeUnit.MONTH)

        val monthStartMillis = monthStart.atStartOfDayIn(zone).toEpochMilliseconds()
        val monthEndMillis = monthEndExclusive.atStartOfDayIn(zone).toEpochMilliseconds()

        val aggregate = DashboardAggregator.aggregate(
            transactions = transactions,
            categories = categories,
            monthStartEpochMillis = monthStartMillis,
            monthEndExclusiveEpochMillis = monthEndMillis,
            zone = zone,
        )

        // Filter to NPR only
        val nprSpend = aggregate.monthToDateSpend[Currency.NPR] ?: Money(0L, Currency.NPR)
        val nprCategories = aggregate.byCategory.filter { it.currency == Currency.NPR }
        val nprTrend = aggregate.trend.filter { it.currency == Currency.NPR }

        // Compute cash flow: in (credits) and out (debits, excluding excluded)
        val monthTransactions = transactions.filter {
            it.occurredAtEpochMillis >= monthStartMillis && it.occurredAtEpochMillis < monthEndMillis
        }

        val nprIncome = Money(
            monthTransactions
                .filter { it.direction == com.kharcha.parser.Direction.CREDIT && it.currency == Currency.NPR }
                .sumOf { it.amountMinorUnits },
            Currency.NPR,
        )

        // Detect recurring charges
        val recurring = RecurringDetector.detectRecurring(transactions, zone)

        // Generate insights
        val insights = InsightGenerator.generateInsights(nprCategories)

        // How much of this month the data actually covers. For the month you are
        // living in that is "up to today"; for any month in the past it is the
        // whole month, because that month is finished.
        val today = clock.now().toLocalDateTime(zone).date
        val daysElapsed = if (today.year == monthStart.year && today.monthNumber == monthStart.monthNumber) {
            today.dayOfMonth
        } else {
            daysInMonth(monthStart)
        }

        val spendByDay = nprTrend.associate { it.date.dayOfMonth to it.total.minorUnits }
        var running = 0L
        val cumulative = (1..daysElapsed).map { day ->
            running += spendByDay[day] ?: 0L
            running
        }

        // The equivalent window one month back, cut at the same day so the two
        // periods are the same length.
        val previousMonthStart = monthStart.plus(-1, DateTimeUnit.MONTH)
        val previousCutoff = previousMonthStart.plus(
            daysElapsed.coerceAtMost(daysInMonth(previousMonthStart)),
            DateTimeUnit.DAY,
        )
        val previousSpend = Money(
            transactions
                .filter {
                    it.currency == Currency.NPR &&
                        it.direction == com.kharcha.parser.Direction.DEBIT &&
                        it.occurredAtEpochMillis >= previousMonthStart.atStartOfDayIn(zone).toEpochMilliseconds() &&
                        it.occurredAtEpochMillis < previousCutoff.atStartOfDayIn(zone).toEpochMilliseconds()
                }
                .sumOf { it.amountMinorUnits },
            Currency.NPR,
        )

        DashboardUiState(
            currentMonth = monthStart,
            monthToDateSpend = nprSpend,
            monthToDateIncome = nprIncome,
            byCategory = nprCategories,
            trend = nprTrend,
            recurringCharges = recurring,
            insights = insights,
            previousPeriodSpend = previousSpend,
            daysElapsed = daysElapsed,
            cumulativeSpend = cumulative,
            comparisonCutoff = previousCutoff.plus(-1, DateTimeUnit.DAY),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DashboardUiState(currentMonth = selectedMonth.value),
    )

    fun previousMonth() {
        selectedMonth.value = selectedMonth.value.plus(-1, DateTimeUnit.MONTH)
    }

    fun nextMonth() {
        selectedMonth.value = selectedMonth.value.plus(1, DateTimeUnit.MONTH)
    }

    fun resetToCurrentMonth() {
        selectedMonth.value = clock.now().toLocalDateTime(zone).date.let { LocalDate(it.year, it.monthNumber, 1) }
    }
}

/**
 * Days in the calendar month containing [date]. Derived by stepping to the first
 * of the next month and back one day, so the leap-year rule lives in the date
 * library rather than in a hand-written table here.
 */
internal fun daysInMonth(date: LocalDate): Int {
    val firstOfMonth = LocalDate(date.year, date.monthNumber, 1)
    return firstOfMonth.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
}
