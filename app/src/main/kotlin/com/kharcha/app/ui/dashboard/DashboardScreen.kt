package com.kharcha.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.kharcha.app.dashboard.MerchantSpend
import com.kharcha.app.ui.theme.KharchaColors
import com.kharcha.app.ui.theme.KharchaSpacing
import com.kharcha.app.ui.theme.KharchaTypography
import com.kharcha.app.ui.theme.MoneyText

/**
 * The dashboard: month-to-date spend per currency, a category breakdown, a
 * daily trend chart and top merchants. Deliberately not a grid of cards —
 * typographic hierarchy and dividers carry the structure, with [SpendSummaryCard]
 * as the only elevated surface.
 */
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val currencies = remember(state) { state.monthToDateSpend.keys.sortedBy { it.name } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = KharchaSpacing.md),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = KharchaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(KharchaSpacing.lg),
    ) {
        item {
            SpendSummaryCard(monthToDateSpend = state.monthToDateSpend)
        }

        for (currency in currencies) {
            val trendForCurrency = state.trend.filter { it.currency == currency }
            if (trendForCurrency.isNotEmpty()) {
                item(key = "trend-header-$currency") {
                    SectionHeader(title = "${currency.name} trend")
                }
                item(key = "trend-chart-$currency") {
                    SpendTrendChart(dailySpend = trendForCurrency)
                }
            }

            val categoriesForCurrency = state.byCategory.filter { it.currency == currency }
            if (categoriesForCurrency.isNotEmpty()) {
                item(key = "category-header-$currency") {
                    SectionHeader(title = "${currency.name} by category")
                }
                item(key = "category-breakdown-$currency") {
                    CategoryBreakdown(categories = categoriesForCurrency)
                }
            }

            val merchantsForCurrency = state.topMerchants.filter { it.currency == currency }
            if (merchantsForCurrency.isNotEmpty()) {
                item(key = "merchant-header-$currency") {
                    SectionHeader(title = "${currency.name} top merchants")
                }
                items(merchantsForCurrency, key = { "merchant-${currency}-${it.merchant}" }) { merchant ->
                    TopMerchantRow(merchant)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column {
        Text(
            text = title,
            style = KharchaTypography.titleLarge,
            color = KharchaColors.onBackground,
        )
        HorizontalDivider(
            color = KharchaColors.outline,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = KharchaSpacing.xs),
        )
    }
}

@Composable
private fun TopMerchantRow(merchant: MerchantSpend) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = KharchaSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = merchant.merchant,
            style = KharchaTypography.bodyMedium,
            color = KharchaColors.onSurfaceVariant,
        )
        MoneyText(money = merchant.total, color = KharchaColors.onSurfaceVariant)
    }
}
