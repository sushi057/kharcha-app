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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kharcha.app.dashboard.RecurringCharge
import com.kharcha.app.ui.components.CardHeader
import com.kharcha.app.ui.components.CategoryTile
import com.kharcha.app.ui.components.KharchaFlushCard
import com.kharcha.app.ui.components.Mini
import com.kharcha.app.ui.components.hairline
import com.kharcha.app.ui.theme.CategoryVisuals
import com.kharcha.app.ui.theme.KharchaMoneyTextStyle
import com.kharcha.app.ui.theme.KharchaSpacing
import com.kharcha.app.ui.theme.LocalKharchaIsDark
import com.kharcha.app.ui.theme.formatMajorUnits
import com.kharcha.parser.Money

/**
 * The charges that come round again: subscriptions, the internet bill, rent.
 *
 * This is the part of the month that is already decided, and the header states
 * the monthly total, because "what is committed before I spend anything" is the
 * number this card exists to answer.
 */
@Composable
fun RecurringCard(
    recurringCharges: List<RecurringCharge>,
    modifier: Modifier = Modifier,
) {
    if (recurringCharges.isEmpty()) return

    val isDark = LocalKharchaIsDark.current
    val currency = recurringCharges.first().currency
    val monthlyTotal = Money(recurringCharges.sumOf { it.amountMinorUnits }, currency)

    KharchaFlushCard(modifier = modifier) {
        CardHeader(
            label = "Recurring",
            caption = "${currency.name} ${formatMajorUnits(monthlyTotal)} / mo",
            modifier = Modifier.padding(
                start = KharchaSpacing.cardPadding,
                end = KharchaSpacing.cardPadding,
                top = KharchaSpacing.cardPadding,
                bottom = 10.dp,
            ),
        )

        recurringCharges.forEach { charge ->
            HorizontalDivider(color = hairline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = KharchaSpacing.cardPadding, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                CategoryTile(
                    color = CategoryVisuals.colorOrFallback(charge.merchant, isDark),
                    icon = CategoryVisuals.iconOrFallback(charge.merchant),
                    contentDescription = null,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = charge.merchant,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Mini(
                        text = "Monthly · next ${charge.nextOccurrenceDate.dayOfMonth} " +
                            charge.nextOccurrenceDate.shortMonth(),
                        maxLines = 1,
                    )
                }
                Text(
                    text = formatMajorUnits(Money(charge.amountMinorUnits, charge.currency)),
                    style = KharchaMoneyTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
