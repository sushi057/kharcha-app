package com.kharcha.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kharcha.app.dashboard.CategorySpend
import com.kharcha.app.ui.components.CardLabel
import com.kharcha.app.ui.components.DonutSlice
import com.kharcha.app.ui.components.DonutChart
import com.kharcha.app.ui.components.KharchaCard
import com.kharcha.app.ui.components.Swatch
import com.kharcha.app.ui.theme.CategoryVisuals
import com.kharcha.app.ui.theme.KharchaMoneyTextStyle
import com.kharcha.app.ui.theme.KharchaSpacing
import com.kharcha.app.ui.theme.LocalKharchaIsDark
import com.kharcha.app.ui.theme.formatMajorUnits

/**
 * Where the month went: a donut of the five largest categories with everything
 * else folded into "Other", and a legend beside it.
 *
 * The tail is folded rather than truncated. Showing only the top five and
 * stopping would draw a ring whose slices do not add up to the total on the hero
 * card, and a part-of-whole chart that is not a whole is a lie about the data.
 */
@Composable
fun WhereItWentCard(
    categories: List<CategorySpend>,
    modifier: Modifier = Modifier,
) {
    if (categories.isEmpty()) return

    val isDark = LocalKharchaIsDark.current
    val ranked = categories.sortedByDescending { it.total.minorUnits }
    val head = ranked.take(5)
    val tail = ranked.drop(5)
    val tailTotal = tail.sumOf { it.total.minorUnits }
    val total = ranked.sumOf { it.total.minorUnits }.coerceAtLeast(1L)

    data class Entry(val name: String, val minorUnits: Long, val color: androidx.compose.ui.graphics.Color)

    val entries = buildList {
        head.forEach {
            add(Entry(it.categoryName, it.total.minorUnits, CategoryVisuals.colorOrFallback(it.categoryName, isDark)))
        }
        if (tailTotal > 0L) {
            add(Entry("Other", tailTotal, CategoryVisuals.colorOrFallback("Other", isDark)))
        }
    }

    KharchaCard(modifier = modifier) {
        CardLabel("Where it went")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = KharchaSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(KharchaSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DonutChart(
                slices = entries.map { DonutSlice(it.minorUnits, it.color) },
                centerLabel = "Top",
                centerValue = "${entries.first().minorUnits * 100 / total}%",
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(KharchaSpacing.sm),
            ) {
                entries.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Swatch(entry.color)
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = formatMajorUnits(
                                com.kharcha.parser.Money(entry.minorUnits, categories.first().currency),
                            ),
                            style = KharchaMoneyTextStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
