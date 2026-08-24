package com.kharcha.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kharcha.app.dashboard.Insight
import com.kharcha.app.ui.components.KharchaCard
import com.kharcha.app.ui.theme.KharchaSemantics
import com.kharcha.app.ui.theme.KharchaSpacing

/**
 * Spending observations, one per card.
 *
 * Deliberately *not* a single "Insights" card with a list inside it. Each
 * observation is an independent statement about the month and gets its own
 * surface; stacking them inside one container made them read as a footnote
 * section that the eye skips, which for the only editorial content in the app is
 * exactly backwards.
 */
@Composable
fun InsightsList(
    insights: List<Insight>,
    modifier: Modifier = Modifier,
) {
    if (insights.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KharchaSpacing.md),
    ) {
        insights.forEachIndexed { index, insight ->
            InsightCard(insight = insight, warning = index == 0)
        }
    }
}

/**
 * [warning] draws the clay/alert treatment. The generator emits its strongest
 * observation first, so the lead insight is the one worth an alert glyph and the
 * rest are informational.
 */
@Composable
private fun InsightCard(
    insight: Insight,
    warning: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint = if (warning) KharchaSemantics.debit else KharchaSemantics.accent

    KharchaCard(modifier = modifier, padding = 12.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(tint.copy(alpha = 0.15f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (warning) Icons.Outlined.WarningAmber else Icons.Outlined.TrendingUp,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp),
                )
            }
            // Title and detail are one sentence flow, not a heading and a
            // subtitle: the insight is a claim ("Food is running hot") plus its
            // evidence ("NPR 5,164 in six days"), and splitting them into two
            // type sizes made the evidence look optional.
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(insight.title)
                    }
                    if (insight.detail.isNotBlank()) {
                        append("  ")
                        append(insight.detail)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
