package com.kharcha.app.dashboard

import kotlinx.datetime.LocalDate

/**
 * Represents a single day's spend data for drawing the daily spend bar chart.
 * Every calendar day in the month gets a slot, including days with zero spend.
 */
data class DaySpendData(
    val dayOfMonth: Int,
    val amountMinorUnits: Long,
)

/**
 * Builds chart model from raw daily spend. Every calendar day from 1 to [daysInMonth]
 * gets a slot, even if there's no spend on that day (amountMinorUnits = 0).
 */
fun buildDailySpendChartModel(
    monthDate: LocalDate,
    dailySpend: List<DailySpend>,
): List<DaySpendData> {
    val daysInMonth = daysInMonth(monthDate)
    val spendByDay = dailySpend.associateBy { it.date.dayOfMonth }

    return (1..daysInMonth).map { day ->
        DaySpendData(
            dayOfMonth = day,
            amountMinorUnits = spendByDay[day]?.total?.minorUnits ?: 0L,
        )
    }
}

/**
 * Returns the number of days in the given month.
 */
private fun daysInMonth(date: LocalDate): Int {
    return when (date.monthNumber) {
        2 -> if (isLeapYear(date.year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
}

private fun isLeapYear(year: Int): Boolean {
    return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
}
