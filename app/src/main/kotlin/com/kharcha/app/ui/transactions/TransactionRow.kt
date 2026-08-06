package com.kharcha.app.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kharcha.data.CategoryEntity
import com.kharcha.data.TransactionEntity
import com.kharcha.app.ui.theme.KharchaSemantics
import com.kharcha.app.ui.theme.KharchaSpacing
import com.kharcha.app.ui.theme.MoneyText
import com.kharcha.parser.Direction
import com.kharcha.parser.Money

/**
 * One row in the transactions list. Debit/credit are distinguished by both
 * color and a leading +/- sign — color alone is not accessible. Excluded
 * transactions render with a strikethrough amount and a muted label so they
 * stay visible but read as out of the totals.
 */
@Composable
fun TransactionRow(
    transaction: TransactionEntity,
    category: CategoryEntity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val amountColor = if (transaction.direction == Direction.DEBIT) {
        KharchaSemantics.debit
    } else {
        KharchaSemantics.credit
    }
    val sign = if (transaction.direction == Direction.DEBIT) "-" else "+"
    val money = Money(transaction.amountMinorUnits, transaction.currency)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = KharchaSpacing.md, vertical = KharchaSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.merchant ?: transaction.remark,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(KharchaSpacing.xs))
            Row {
                if (category != null) {
                    CategoryChip(category = category)
                }
                if (transaction.excludedFromSpending) {
                    Spacer(modifier = Modifier.width(KharchaSpacing.xs))
                    Text(
                        text = "Excluded",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row {
            Text(
                text = sign,
                style = MaterialTheme.typography.bodyLarge,
                color = amountColor,
            )
            MoneyText(
                money = money,
                color = amountColor,
                style = MaterialTheme.typography.bodyLarge.let { base ->
                    if (transaction.excludedFromSpending) {
                        base.copy(textDecoration = TextDecoration.LineThrough)
                    } else {
                        base
                    }
                },
            )
        }
    }
}

@Composable
private fun CategoryChip(category: CategoryEntity, modifier: Modifier = Modifier) {
    Text(
        text = category.name,
        style = MaterialTheme.typography.labelMedium,
        color = Color(category.colorArgb),
        modifier = modifier
            .background(
                color = Color(category.colorArgb).copy(alpha = 0.16f),
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = KharchaSpacing.xs, vertical = 2.dp),
    )
}
