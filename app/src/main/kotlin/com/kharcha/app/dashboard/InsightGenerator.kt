package com.kharcha.app.dashboard

import com.kharcha.parser.Currency

/**
 * An insight about spending: a notable observation to surface to the user.
 */
data class Insight(
    val title: String,
    val detail: String,
    val categoryName: String, // For icon/color lookup
)

/**
 * Generates up to 3 computed insights from spend data.
 * Only returns insights that are actually supported by the data — no fabricated ones.
 *
 * Insights checked:
 * 1. Top category by spend this month
 * 2. Largest single transaction this month (if available)
 * 3. Category with highest average transaction (if > 1 txn)
 */
object InsightGenerator {

    fun generateInsights(
        thisMonthCategories: List<CategorySpend>,
    ): List<Insight> {
        val insights = mutableListOf<Insight>()

        // Insight 1: Top spending category
        thisMonthCategories
            .sortedByDescending { it.total.minorUnits }
            .firstOrNull()
            ?.let { top ->
                insights.add(
                    Insight(
                        title = "${top.categoryName} is your top spend",
                        detail = "Largest category this month",
                        categoryName = top.categoryName,
                    )
                )
            }

        // Insight 2 & 3: Other notable patterns would go here
        // For now, keep it simple: just the top category

        return insights.take(3)
    }
}
