package com.kharcha.app.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharcha.app.ui.components.hairline
import com.kharcha.app.ui.theme.KharchaMonoSmallTextStyle
import com.kharcha.app.ui.theme.KharchaSemantics
import com.kharcha.app.ui.theme.KharchaSpacing
import com.kharcha.app.ui.theme.formatSignedAmount
import com.kharcha.parser.Currency
import com.kharcha.parser.Money
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The day separator in the transactions list.
 *
 * It is a different *surface* from the rows it introduces — tonal fill, hairlines
 * top and bottom, mono uppercase label — not just differently-styled text on the
 * same background. That is the fix for the v1 list, where headers and rows were
 * both left-aligned text on the same colour and the eye could not tell which was
 * which while scrolling.
 *
 * It also carries the day's signed subtotal, which is the question you actually
 * have when scanning by day: not "what did I buy on Tuesday" but "what did
 * Tuesday cost".
 */
@Composable
fun DayHeader(
    epochMillis: Long,
    signedSubtotalMinorUnits: Long,
    currency: Currency,
    zone: TimeZone = TimeZone.currentSystemDefault(),
    modifier: Modifier = Modifier,
) {
    val date = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone).date
    val dayOfWeek = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }

    val hairlineColor = hairline

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .drawBehind {
                drawLine(hairlineColor, Offset(0f, 0f), Offset(size.width, 0f), 1f)
                drawLine(hairlineColor, Offset(0f, size.height), Offset(size.width, size.height), 1f)
            }
            .padding(
                start = KharchaSpacing.cardPadding,
                end = KharchaSpacing.cardPadding,
                top = 9.dp,
                bottom = 6.dp,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$dayOfWeek ${date.dayOfMonth} $month".uppercase(),
            style = KharchaMonoSmallTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatSignedAmount(Money(signedSubtotalMinorUnits, currency)),
            style = KharchaMonoSmallTextStyle.copy(letterSpacing = 0.sp),
            color = if (signedSubtotalMinorUnits >= 0) {
                KharchaSemantics.credit
            } else {
                MaterialTheme.colorScheme.outline
            },
        )
    }
}
