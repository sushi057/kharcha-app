package com.kharcha.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kharcha.app.chart.axisMaxForValues
import com.kharcha.app.chart.formatAxisLabel
import com.kharcha.app.dashboard.DailySpend
import com.kharcha.app.dashboard.buildDailySpendChartModel
import com.kharcha.app.ui.components.CardHeader
import com.kharcha.app.ui.components.DailyBars
import com.kharcha.app.ui.components.KharchaCard
import com.kharcha.app.ui.components.Mini
import com.kharcha.app.ui.theme.KharchaSemantics
import kotlinx.datetime.LocalDate

/**
 * Daily spend for one month, as bars against a snapped axis.
 *
 * Bars rather than a line, because daily spend is discrete and gappy: a line
 * between Tuesday and Thursday draws a Wednesday value that never existed. Every
 * calendar day gets a slot including the zero-spend ones, which is what keeps
 * the horizontal axis a real calendar rather than a list of the days you
 * happened to spend on.
 *
 * [dailySpend] must already be filtered to a single currency.
 */
@Composable
fun SpendTrendChart(
    monthDate: LocalDate,
    dailySpend: List<DailySpend>,
    modifier: Modifier = Modifier,
    /** Called with the tapped day when the reader asks to see its transactions. */
    onOpenDay: ((LocalDate) -> Unit)? = null,
) {
    val chartData = remember(monthDate, dailySpend) {
        buildDailySpendChartModel(monthDate, dailySpend)
    }
    if (chartData.isEmpty()) return

    val axisMax = remember(chartData) {
        axisMaxForValues(chartData.map { it.amountMinorUnits })
    }
    val peak = remember(chartData) { chartData.maxByOrNull { it.amountMinorUnits } }
    // Cleared whenever the month changes, so a tap on the 14th of August does not
    // stay highlighted as "the 14th" after paging back to July.
    var selectedIndex by remember(monthDate) { mutableStateOf<Int?>(null) }
    val selected = selectedIndex?.let(chartData::getOrNull)

    KharchaCard(modifier = modifier) {
        CardHeader(
            label = "Daily spend",
            // The scale, stated once. Without it the bars are shapes rather than
            // quantities and a 300-rupee day looks like a 30,000-rupee one.
            caption = if (peak != null && peak.amountMinorUnits > 0) {
                "Peak NPR ${formatAxisLabel(peak.amountMinorUnits / 100)} · " +
                    "${peak.dayOfMonth} ${monthDate.shortMonth()}"
            } else {
                "No spend yet"
            },
        )

        DailyBars(
            values = chartData.map { it.amountMinorUnits },
            axisMax = axisMax,
            barColor = KharchaSemantics.debit,
            modifier = Modifier.padding(top = 10.dp),
            selectedIndex = selectedIndex,
            onSelect = { index ->
                // Tapping the selected bar again clears it, so there is always a way
                // back to the undimmed month.
                selectedIndex = if (selectedIndex == index) null else index
            },
        )

        if (selected == null) {
            Mini(
                text = if (onOpenDay == null) {
                    "1 – ${chartData.size} ${monthDate.shortMonth()}"
                } else {
                    "1 – ${chartData.size} ${monthDate.shortMonth()} · tap a bar for a day"
                },
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            // The readout replaces the range caption rather than joining it: two lines
            // of scale text under a chart that is already labelled is noise.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .then(
                        if (onOpenDay == null) {
                            Modifier
                        } else {
                            Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable(role = Role.Button) {
                                    onOpenDay(
                                        LocalDate(
                                            monthDate.year,
                                            monthDate.monthNumber,
                                            selected.dayOfMonth,
                                        )
                                    )
                                }
                        },
                    )
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "NPR ${formatAxisLabel(selected.amountMinorUnits / 100)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Mini(
                        text = "${selected.dayOfMonth} ${monthDate.shortMonth()} ${monthDate.year}",
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                if (onOpenDay != null) {
                    Text(
                        text = "View transactions →",
                        style = MaterialTheme.typography.labelLarge,
                        color = KharchaSemantics.accent,
                    )
                }
            }
        }
    }
}
