package com.kharcha.app.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kharcha.app.ui.components.AmountText
import com.kharcha.app.ui.components.CategoryTile
import com.kharcha.app.ui.components.Mini
import com.kharcha.app.ui.theme.CategoryVisuals
import com.kharcha.app.ui.theme.KharchaPillShape
import com.kharcha.app.ui.theme.KharchaSemantics
import com.kharcha.app.ui.theme.KharchaSpacing
import com.kharcha.app.ui.theme.LocalKharchaIsDark
import com.kharcha.data.CategoryEntity
import com.kharcha.data.TransactionEntity
import com.kharcha.parser.Direction
import com.kharcha.parser.Money
import com.kharcha.parser.RemarkParser
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * One transaction: a category glyph, who it was with, and how much.
 *
 * The amount is drawn in the plain foreground for a debit and in sage for a
 * credit — not red-for-out, green-for-in. In a list where almost every row is an
 * outgoing, colouring them all red spends the alarm colour on the normal case
 * and leaves nothing to mark the exception. The minus sign carries the direction;
 * colour is reserved for the money that arrived.
 *
 * The raw SMS never appears here. It is evidence, and it lives in the detail
 * sheet where it can be read deliberately.
 */
@Composable
fun TransactionRow(
    transaction: TransactionEntity,
    category: CategoryEntity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    zone: TimeZone = TimeZone.currentSystemDefault(),
    categorizedByRule: Boolean = false,
) {
    val isDark = LocalKharchaIsDark.current
    val parsed = RemarkParser.parse(transaction.remark)
    val merchantDisplay = (transaction.merchant ?: parsed.merchant)?.toDisplayName() ?: "Unknown"

    // When no counterparty could be extracted the merchant line falls back to the
    // channel ("Mobile banking"), so repeating it underneath would print the same
    // words twice in one row.
    val channelDisplay = parsed.channel?.takeIf { !it.equals(merchantDisplay, ignoreCase = true) }

    val tint = category?.let { entity ->
        CategoryVisuals.getColor(entity.name, isDark)?.let(::Color) ?: Color(entity.colorArgb)
    } ?: CategoryVisuals.colorOrFallback("Other", isDark)

    val signedMoney = Money(
        if (transaction.direction == Direction.DEBIT) -transaction.amountMinorUnits else transaction.amountMinorUnits,
        transaction.currency,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = KharchaSpacing.cardPadding, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(
            color = tint,
            icon = CategoryVisuals.iconOrFallback(category?.name ?: "Other"),
            contentDescription = category?.name,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = merchantDisplay,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Mini(
                text = listOfNotNull(
                    channelDisplay,
                    formatTimeHHmm(transaction.occurredAtEpochMillis, zone),
                ).joinToString(" · "),
                maxLines = 1,
                modifier = Modifier.padding(top = 1.dp),
            )
            if (categorizedByRule && transaction.merchant != null) {
                RuleTag(merchant = merchantDisplay, categoryName = category?.name)
            }
        }

        AmountText(
            money = signedMoney,
            explicitPlus = true,
            color = if (transaction.excludedFromSpending) MaterialTheme.colorScheme.outline else null,
            style = if (transaction.excludedFromSpending) {
                com.kharcha.app.ui.theme.KharchaMoneyTextStyle.copy(
                    textDecoration = TextDecoration.LineThrough,
                )
            } else {
                com.kharcha.app.ui.theme.KharchaMoneyTextStyle
            },
        )
    }
}

/**
 * "rule · Pathao → Transport" — the quiet receipt that this row was categorised
 * automatically, so a wrong category is traceable to the rule that caused it
 * rather than looking like the app guessed.
 */
@Composable
private fun RuleTag(
    merchant: String,
    categoryName: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(top = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, KharchaPillShape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = null,
            tint = KharchaSemantics.accent,
            modifier = Modifier.size(9.dp),
        )
        Text(
            text = "rule · $merchant → ${categoryName ?: "?"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Turns the bank's shouting into a name: "HIMALAYAN JAVA COFFEE" becomes
 * "Himalayan Java Coffee".
 *
 * Only all-caps input is touched. A merchant the user typed, or one the parser
 * extracted with its own casing ("connectIPS", "eSewa"), is already in the form
 * its owner writes it and must survive untouched — title-casing everything would
 * turn "connectIPS" into "Connectips".
 */
internal fun String.toDisplayName(): String {
    if (any { it.isLowerCase() }) return this
    return split(" ")
        .joinToString(" ") { word ->
            when {
                word.isEmpty() -> word
                // Two letters or fewer that are all caps are almost always an
                // initialism — QR, AC, NEA, IPS — and read wrong in title case.
                word.length <= 3 && word.all { it.isLetter() } -> word
                else -> word.lowercase().replaceFirstChar { it.uppercase() }
            }
        }
}

/** Format timestamp as HH:mm in the given timezone. */
private fun formatTimeHHmm(epochMillis: Long, zone: TimeZone): String {
    val localDateTime = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone)
    return "%02d:%02d".format(localDateTime.hour, localDateTime.minute)
}
