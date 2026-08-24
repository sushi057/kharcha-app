package com.kharcha.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.kharcha.app.ui.theme.KharchaMoneyTextStyle
import com.kharcha.app.ui.theme.KharchaPillShape
import com.kharcha.app.ui.theme.KharchaSemantics
import com.kharcha.app.ui.theme.formatSignedAmount
import com.kharcha.parser.Money

/**
 * A signed amount in the data face — the design's `.money`.
 *
 * Colour follows the sign only where the sign is the point. A debit in a list of
 * debits is drawn in plain `onSurface`, not in red: if every row is red, red has
 * stopped meaning anything. Credits get the sage green, because in a list of
 * outgoings the money that came *in* is the exception worth catching the eye.
 */
@Composable
fun AmountText(
    money: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = KharchaMoneyTextStyle,
    explicitPlus: Boolean = true,
    color: Color? = null,
) {
    val credit = money.minorUnits > 0
    Text(
        text = formatSignedAmount(money, explicitPlus = explicitPlus),
        modifier = modifier,
        style = style,
        color = color ?: if (credit) KharchaSemantics.credit else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
}

/**
 * The month-over-month delta pill on the hero card: "↓ 18%".
 *
 * Spending less than last month is [down] and reads sage; spending more reads
 * clay. This is the one place the app editorialises about a number, and it is
 * worth it — "NPR 23,480" alone answers nothing without something to compare to.
 */
@Composable
fun DeltaPill(
    percent: Int,
    down: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (down) KharchaSemantics.credit else KharchaSemantics.debit
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, KharchaPillShape)
            .padding(start = 7.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = if (down) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
            contentDescription = if (down) "down" else "up",
            tint = color,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}
