package com.kharcha.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kharcha.app.ui.components.AmountText
import com.kharcha.app.ui.components.CardHeader
import com.kharcha.app.ui.components.KharchaCard
import com.kharcha.app.ui.components.ProgressTrack
import com.kharcha.app.ui.components.hairline
import com.kharcha.app.ui.theme.KharchaMoneyBigTextStyle
import com.kharcha.app.ui.theme.KharchaSemantics
import com.kharcha.app.ui.theme.KharchaSpacing
import com.kharcha.parser.Currency
import com.kharcha.parser.Money

/**
 * In, out, and what is left of the two.
 *
 * The two bars share one scale — both are measured against the larger of the two
 * amounts — so the *lengths* are comparable. Scaling each bar to its own value
 * would fill both tracks completely and say nothing at all.
 */
@Composable
fun CashFlowCard(
    inAmount: Money,
    outAmount: Money,
    modifier: Modifier = Modifier,
    periodLabel: String? = null,
) {
    val netAmount = Money(inAmount.minorUnits - outAmount.minorUnits, Currency.NPR)
    val scale = maxOf(inAmount.minorUnits, outAmount.minorUnits, 1L)

    KharchaCard(modifier = modifier) {
        CardHeader(label = "Cash flow", caption = periodLabel)

        Column(
            modifier = Modifier.padding(top = 13.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            CashFlowRow(
                label = "In",
                amount = inAmount,
                fraction = inAmount.minorUnits.toFloat() / scale,
                color = KharchaSemantics.credit,
            )
            CashFlowRow(
                label = "Out",
                amount = outAmount,
                fraction = outAmount.minorUnits.toFloat() / scale,
                color = KharchaSemantics.debit,
            )

            HorizontalDivider(color = hairline)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Net",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                AmountText(
                    money = netAmount,
                    style = KharchaMoneyBigTextStyle,
                    color = if (netAmount.minorUnits >= 0) KharchaSemantics.credit else KharchaSemantics.debit,
                )
            }
        }
    }
}

@Composable
private fun CashFlowRow(
    label: String,
    amount: Money,
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // "In" is money arriving and "Out" money leaving; both are stored as
            // positive magnitudes, so the sign is stated by the row rather than
            // inferred from the stored value.
            AmountText(
                money = if (label == "Out") Money(-amount.minorUnits, amount.currency) else amount,
                color = if (label == "Out") MaterialTheme.colorScheme.onSurface else KharchaSemantics.credit,
            )
        }
        ProgressTrack(
            fraction = fraction,
            color = color,
            modifier = Modifier.padding(top = KharchaSpacing.xs),
        )
    }
}
