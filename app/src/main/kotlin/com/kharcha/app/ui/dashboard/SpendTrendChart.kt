package com.kharcha.app.ui.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kharcha.app.dashboard.DailySpend
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf

/**
 * Daily discretionary spend for a single currency across the month. Renders
 * one line — [dailySpend] must already be filtered to one [com.kharcha.parser.Currency]
 * by the caller, since NPR and USD are never plotted on the same scale.
 *
 * Money is converted to Float only here, at the very last step before it's
 * handed to vico for rendering — never earlier, and the result is never fed
 * back into any arithmetic. Excluded transactions never reach [dailySpend]
 * (see [com.kharcha.app.dashboard.DashboardAggregator]), so a large excluded
 * transfer cannot distort this chart's scale.
 */
@Composable
fun SpendTrendChart(
    dailySpend: List<DailySpend>,
    modifier: Modifier = Modifier,
) {
    val sorted = remember(dailySpend) { dailySpend.sortedBy { it.date.toString() } }
    val entries = remember(sorted) {
        sorted.mapIndexed { index, daily ->
            entryOf(index.toFloat(), daily.total.minorUnits / 100f)
        }
    }
    val producer = remember(entries) { ChartEntryModelProducer(entries) }

    Chart(
        chart = lineChart(),
        chartModelProducer = producer,
        startAxis = rememberStartAxis(),
        bottomAxis = rememberBottomAxis(),
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
    )
}
