package com.kharcha.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kharcha.app.dashboard.CategorySpend
import com.kharcha.app.ui.theme.KharchaColors
import com.kharcha.app.ui.theme.KharchaSpacing
import com.kharcha.app.ui.theme.KharchaTypography
import com.kharcha.app.ui.theme.MoneyText

/**
 * The month's spend by category, for one currency. A divided list, not a
 * grid of cards — a small color dot ties each row back to the category's
 * accent color without introducing another elevated surface.
 */
@Composable
fun CategoryBreakdown(
    categories: List<CategorySpend>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        categories.forEachIndexed { index, spend ->
            if (index > 0) {
                HorizontalDivider(color = KharchaColors.outline)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = KharchaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(spend.colorArgb), CircleShape),
                    )
                    Text(
                        text = spend.categoryName,
                        style = KharchaTypography.bodyLarge,
                        color = KharchaColors.onSurface,
                        modifier = Modifier.padding(start = KharchaSpacing.sm),
                    )
                }
                MoneyText(money = spend.total, color = KharchaColors.onSurface)
            }
        }
    }
}
