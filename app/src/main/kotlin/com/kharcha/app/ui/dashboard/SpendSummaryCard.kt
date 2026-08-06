package com.kharcha.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.kharcha.app.ui.theme.KharchaSemantics
import com.kharcha.app.ui.theme.KharchaSpacing
import com.kharcha.app.ui.theme.MoneyText
import com.kharcha.parser.Currency
import com.kharcha.parser.Money

/**
 * The dashboard's single elevated surface (per the design brief: "at most
 * ONE elevated surface for the headline figure"). Shows month-to-date
 * discretionary spend, one line per currency present — NPR and USD are
 * always shown as separate figures, never summed.
 */
@Composable
fun SpendSummaryCard(
    monthToDateSpend: Map<Currency, Money>,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(KharchaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KharchaSpacing.xs),
        ) {
            Text(
                text = "Spent this month",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (monthToDateSpend.isEmpty()) {
                Text(
                    text = "No spending yet",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Start,
                )
            } else {
                monthToDateSpend.entries
                    .sortedBy { it.key.name }
                    .forEach { (_, money) ->
                        MoneyText(
                            money = money,
                            style = MaterialTheme.typography.displayLarge,
                            color = KharchaSemantics.debit,
                        )
                    }
            }
        }
    }
}
