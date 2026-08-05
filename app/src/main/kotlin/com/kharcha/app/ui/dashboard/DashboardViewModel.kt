package com.kharcha.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharcha.app.dashboard.CategorySpend
import com.kharcha.app.dashboard.DailySpend
import com.kharcha.app.dashboard.DashboardAggregator
import com.kharcha.app.dashboard.MerchantSpend
import com.kharcha.data.CategoryDao
import com.kharcha.data.TransactionDao
import com.kharcha.parser.Currency
import com.kharcha.parser.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

data class DashboardUiState(
    val monthToDateSpend: Map<Currency, Money> = emptyMap(),
    val byCategory: List<CategorySpend> = emptyList(),
    val trend: List<DailySpend> = emptyList(),
    val topMerchants: List<MerchantSpend> = emptyList(),
)

/**
 * Drives the dashboard. Month-to-date is "the current calendar month in the
 * device's local timezone" (ruling 1) — [clock] and [zone] are injected
 * rather than read from [Clock.System]/[TimeZone.currentSystemDefault]
 * directly so tests are deterministic.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    transactionDao: TransactionDao,
    categoryDao: CategoryDao,
    private val clock: Clock,
    private val zone: TimeZone,
) : ViewModel() {

    val state: StateFlow<DashboardUiState> = combine(
        transactionDao.observeAll(),
        categoryDao.observeAll(),
    ) { transactions, categories ->
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

        DashboardUiState(
            monthToDateSpend = aggregate.monthToDateSpend,
            byCategory = aggregate.byCategory,
            trend = aggregate.trend,
            topMerchants = aggregate.topMerchants,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DashboardUiState(),
    )
}
