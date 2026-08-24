package com.kharcha.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharcha.app.ui.components.CardLabel
import com.kharcha.app.ui.components.DeltaPill
import com.kharcha.app.ui.components.KharchaCard
import com.kharcha.app.ui.components.Mini
import com.kharcha.app.ui.components.Sparkline
import com.kharcha.app.ui.theme.KharchaSemantics
import com.kharcha.app.ui.theme.KharchaSpacing
import com.kharcha.app.ui.theme.formatMajorUnits
import com.kharcha.parser.Money
import kotlinx.datetime.LocalDate

/**
 * The one thing on the dashboard that is read first: this month's spend, at
 * 42sp, above everything else.
 *
 * The amount is drawn in the plain foreground rather than in the debit red. Red
 * here would be the app having an opinion about a number it cannot judge —
 * 23,480 is not bad news, it is just the number — and it would also spend the
 * screen's loudest colour on the element that already has the screen's loudest
 * size.
 *
 * The currency rides as a small prefix inside the same line, so "NPR" is stated
 * once for the whole screen and every list below can drop it.
 */
@Composable
fun HeroCard(
    monthToDateSpend: Money,
    modifier: Modifier = Modifier,
    deltaPercent: Int? = null,
    comparisonCutoff: LocalDate? = null,
    cumulativeSpend: List<Long> = emptyList(),
) {
    KharchaCard(modifier = modifier) {
        CardLabel("Spent this month")

        Text(
            text = buildAnnotatedString {
                withStyle(
                    MaterialTheme.typography.displayLarge.toSpanStyle().copy(
                        fontSize = 19.sp,
                        letterSpacing = 0.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    append("${monthToDateSpend.currency.name}  ")
                }
                append(formatMajorUnits(monthToDateSpend))
            },
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 9.dp, bottom = 2.dp),
        )

        if (deltaPercent != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KharchaSpacing.sm),
                modifier = Modifier.padding(top = KharchaSpacing.sm),
            ) {
                DeltaPill(percent = kotlin.math.abs(deltaPercent), down = deltaPercent < 0)
                if (comparisonCutoff != null) {
                    Mini("vs. ${comparisonCutoff.dayOfMonth} ${comparisonCutoff.shortMonth()} last month")
                }
            }
        }

        if (cumulativeSpend.size >= 2) {
            Sparkline(
                values = cumulativeSpend,
                color = KharchaSemantics.accent,
                modifier = Modifier.padding(top = KharchaSpacing.md),
            )
        }
    }
}

/** "Aug" — the three-letter form the captions use. */
internal fun LocalDate.shortMonth(): String =
    month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
