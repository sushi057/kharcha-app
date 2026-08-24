package com.kharcha.app.ui.budgets

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.math.ceil

/**
 * Pure, tested functions for budget calculations: pace, status classification,
 * suggestion computation, and axis scaling. No DAO or Compose dependencies.
 */

/**
 * Computes how far through the month we are (0.0 to 1.0) and returns a sentence
 * like "You're 19% through August and 35% through your budget — running slightly ahead."
 *
 * Compares two percentages:
 * - [dayProgress]: (today's day - 1) / days in month (0.0 at start of month 1, 1.0 at end of month last day)
 * - [budgetProgress]: spent / budgeted (0.0 with no spend, 1.0 when budgeted is reached)
 *
 * Returns null if [budgetLimitMinorUnits] <= 0 or if [monthStart] is not actually the start
 * of the month (the caller must pass a valid date).
 */
/** How far from the even-spending line still counts as "on pace", in percentage points. */
private const val PACE_TOLERANCE_PERCENT = 5

fun computePace(
    today: LocalDate,
    monthStart: LocalDate,
    spentMinorUnits: Long,
    budgetLimitMinorUnits: Long,
): String? {
    if (budgetLimitMinorUnits <= 0) return null

    // Days into the month (0-indexed: 0 on the 1st, 1 on the 2nd, etc.)
    val dayOfMonth = today.dayOfMonth
    val daysInMonth = getMonthLength(today.year, today.monthNumber)
    val dayProgress = (dayOfMonth - 1).toDouble() / daysInMonth.toDouble()
    val budgetProgress = spentMinorUnits.toDouble() / budgetLimitMinorUnits.toDouble()

    val dayPercent = (dayProgress * 100).toInt()
    val budgetPercent = (budgetProgress * 100).toInt().coerceIn(0, 999) // Cap at 999 to handle over-budget
    val daysRemaining = daysInMonth - dayOfMonth + 1

    // "Ahead" means ahead of the spending schedule, i.e. burning the budget faster than
    // the month is elapsing — the case worth warning about. Spending more slowly than the
    // month passes is "behind", which is the good outcome.
    //
    // The tolerance matters: on an exact comparison the two percentages would virtually
    // never be equal, so "on pace" would never appear and every budget would read as a
    // warning. A few points either side of the schedule is simply on pace.
    val pace = when {
        budgetPercent - dayPercent > PACE_TOLERANCE_PERCENT -> "running ahead"
        dayPercent - budgetPercent > PACE_TOLERANCE_PERCENT -> "running behind"
        else -> "on pace"
    }

    // Month.name is the enum constant ("AUGUST"); shown to a person it must read "August".
    val monthLabel = monthStart.month.name.lowercase().replaceFirstChar { it.uppercase() }

    return "You're $dayPercent% through $monthLabel and $budgetPercent% through your budget — $pace."
}

/**
 * Number of days in a given month. Handles leap years for February.
 */
fun getMonthLength(year: Int, monthNumber: Int): Int {
    return when (monthNumber) {
        2 -> if (isLeapYear(year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
}

/**
 * Gregorian calendar leap year check.
 */
fun isLeapYear(year: Int): Boolean {
    return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
}

/**
 * Computes the 3-month average spend for a category, using the [monthlyHistory]
 * of the last 3 months. Returns the rounded-up value in minor units, or null if
 * no history is available (the category has no spend in the last 3 months).
 *
 * This is the suggested budget amount for that category — if a user has spent an
 * average of NPR 5,432 over the last 3 months, [suggestBudgetAmount] returns 5,432
 * (or rounded up if there's a fractional amount).
 */
fun suggestBudgetAmount(monthlyHistory: List<Long>): Long? {
    if (monthlyHistory.isEmpty()) return null
    val sum = monthlyHistory.sum()
    val average = sum.toDouble() / monthlyHistory.size.toDouble()
    return ceil(average).toLong()
}

/**
 * Classifies a budget's status into one of three categories:
 * - [BudgetStatus.NORMAL]: 0%–79% of budget used
 * - [BudgetStatus.NEAR_LIMIT]: 80%–99% of budget used
 * - [BudgetStatus.OVER_BUDGET]: 100%+ of budget used
 *
 * The thresholds align with the alert threshold system (see [BudgetNotifier]).
 * Returns null if [budgetLimitMinorUnits] <= 0 (no budget set).
 */
enum class BudgetStatus {
    NORMAL, NEAR_LIMIT, OVER_BUDGET
}

fun classifyBudgetStatus(
    spentMinorUnits: Long,
    budgetLimitMinorUnits: Long,
    alertThresholdPercent: Int = 80,
): BudgetStatus? {
    if (budgetLimitMinorUnits <= 0) return null

    val percentUsed = (spentMinorUnits * 100) / budgetLimitMinorUnits
    return when {
        percentUsed >= 100 -> BudgetStatus.OVER_BUDGET
        percentUsed >= alertThresholdPercent -> BudgetStatus.NEAR_LIMIT
        else -> BudgetStatus.NORMAL
    }
}


