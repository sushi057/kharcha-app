package com.kharcha.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.kharcha.app.ui.components.IconAction
import com.kharcha.app.ui.components.KharchaAppBar
import com.kharcha.app.ui.components.ThemeToggleAction
import com.kharcha.app.ui.theme.KharchaSpacing
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime

/**
 * The dashboard: summary before detail.
 *
 * Card order is deliberate and is the answer to "nothing on this screen reads
 * first". It descends from the number you check daily (hero), through the shape
 * of the month (cash flow, daily spend), to its composition (categories,
 * recurring), and ends with the app's own commentary. Anything that needs a
 * second of thought sits below anything that needs none.
 *
 * The month lives in the app bar rather than in a card, because it scopes every
 * card below it — putting it in the content would make it look like one more
 * card's worth of data.
 */
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
    /**
     * Where "View transactions" on a tapped spend bar goes. Null on screens that have
     * nowhere to send it (previews, tests), which turns the chart back into a read-only
     * one rather than leaving a dead link on it.
     */
    onOpenDay: ((LocalDate) -> Unit)? = null,
    /**
     * Opens Settings. Null in previews and tests, which hides the gear rather than
     * leaving a control that does nothing when tapped.
     */
    onOpenSettings: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        KharchaAppBar(
            title = state.currentMonth.monthTitle(),
            actions = {
                IconAction(
                    icon = Icons.Outlined.ChevronLeft,
                    contentDescription = "Previous month",
                    onClick = viewModel::previousMonth,
                )
                IconAction(
                    icon = Icons.Outlined.ChevronRight,
                    contentDescription = "Next month",
                    onClick = viewModel::nextMonth,
                )
                ThemeToggleAction()
                onOpenSettings?.let {
                    IconAction(
                        icon = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        onClick = it,
                    )
                }
            },
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = KharchaSpacing.screenGutter),
            contentPadding = PaddingValues(bottom = KharchaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KharchaSpacing.md),
        ) {
            item(key = "hero") {
                HeroCard(
                    monthToDateSpend = state.monthToDateSpend,
                    deltaPercent = state.deltaPercent,
                    comparisonCutoff = state.comparisonCutoff,
                    cumulativeSpend = state.cumulativeSpend,
                )
            }

            item(key = "cash-flow") {
                CashFlowCard(
                    inAmount = state.monthToDateIncome,
                    outAmount = state.monthToDateSpend,
                    periodLabel = state.periodLabel(),
                )
            }

            if (state.trend.isNotEmpty()) {
                item(key = "spend-chart") {
                    SpendTrendChart(
                        monthDate = state.currentMonth,
                        dailySpend = state.trend,
                        onOpenDay = onOpenDay,
                    )
                }
            }

            if (state.byCategory.isNotEmpty()) {
                item(key = "where-it-went") {
                    WhereItWentCard(categories = state.byCategory)
                }
            }

            if (state.recurringCharges.isNotEmpty()) {
                item(key = "recurring") {
                    RecurringCard(recurringCharges = state.recurringCharges)
                }
            }

            if (state.insights.isNotEmpty()) {
                item(key = "insights") {
                    InsightsList(insights = state.insights)
                }
            }
        }
    }
}

/**
 * "August" for the month you are in, "August 2025" for one you are not. The
 * year is noise while it is the current one and essential the moment it is not.
 */
private fun LocalDate.monthTitle(): String {
    val name = month.name.lowercase().replaceFirstChar { it.uppercase() }
    val currentYear = kotlinx.datetime.Clock.System.now()
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).year
    return if (year == currentYear) name else "$name $year"
}

/** "Aug 1 – 6": the span the cash-flow numbers actually cover. */
private fun DashboardUiState.periodLabel(): String? {
    if (daysElapsed <= 0) return null
    return "${currentMonth.shortMonth()} 1 – $daysElapsed"
}
