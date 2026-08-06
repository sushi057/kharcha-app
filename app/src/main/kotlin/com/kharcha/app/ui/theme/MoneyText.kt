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
 * see [KharchaSemantics.debit] and [KharchaSemantics.credit], which follow the
 * active scheme rather than a fixed palette constant.
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

/** Parses a user-typed decimal amount string into minor units without Double. */
fun parseAmountMinorUnits(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    if (!trimmed.matches(Regex("^\\d+(\\.\\d{1,2})?$"))) return null
    val parts = trimmed.split(".")
    val major = parts[0].toLongOrNull() ?: return null
    val minor = when (val fraction = parts.getOrNull(1)) {
        null -> 0L
        else -> fraction.padEnd(2, '0').toLongOrNull() ?: return null
    }
    return major * 100 + minor
}

/**
 * Renders [minorUnits] as the plain number a user would type back into an amount field —
 * "500" or "500.50", no currency prefix and no thousands separators, so it round-trips
 * through [parseAmountMinorUnits] unchanged. Displaying `minorUnits / 100` instead drops
 * the minor units and silently rewrites the value the next time the field is saved.
 */
fun formatMinorUnitsPlain(minorUnits: Long): String {
    val negative = minorUnits < 0
    val abs = if (negative) -minorUnits else minorUnits
    val major = abs / 100
    val fraction = abs % 100
    val sign = if (negative) "-" else ""
    return if (fraction == 0L) "$sign$major" else "$sign$major.${fraction.toString().padStart(2, '0')}"
}
