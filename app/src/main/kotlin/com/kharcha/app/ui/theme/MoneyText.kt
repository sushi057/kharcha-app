package com.kharcha.app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.kharcha.parser.Money

/**
 * Formats [money] as "CUR 1,234.56" from minor units only — no floating
 * point is ever involved. This is the single source of truth for money
 * formatting in the app; [MoneyText] and every later UI task call this
 * rather than reimplementing it.
 */
fun formatMoney(money: Money): String {
    val minorUnits = money.minorUnits
    val isNegative = minorUnits < 0
    val absMinorUnits = if (isNegative) -minorUnits else minorUnits

    val majorUnits = absMinorUnits / 100
    val fraction = absMinorUnits % 100

    val groupedMajor = groupThousands(majorUnits)
    val fractionStr = fraction.toString().padStart(2, '0')

    val sign = if (isNegative) "-" else ""
    return "${money.currency.name} $sign$groupedMajor.$fractionStr"
}

private fun groupThousands(value: Long): String {
    val digits = value.toString()
    if (digits.length <= 3) return digits

    val builder = StringBuilder()
    val firstGroupLen = digits.length % 3
    val start = if (firstGroupLen == 0) 3 else firstGroupLen

    builder.append(digits, 0, start)
    var i = start
    while (i < digits.length) {
        builder.append(',')
        builder.append(digits, i, i + 3)
        i += 3
    }
    return builder.toString()
}

/**
 * Renders [money] using [formatMoney] with tabular figures so amounts line
 * up in a list. Pass [color] to color debit/credit amounts differently —
 * see [KharchaColors.debit] and [KharchaColors.credit].
 */
@Composable
fun MoneyText(
    money: Money,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    style: TextStyle = KharchaMoneyTextStyle,
) {
    Text(
        text = formatMoney(money),
        modifier = modifier,
        color = color,
        style = style,
    )
}
