package com.kharcha.app.ui.settings

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * A date range for export, with both start and end dates inclusive.
 * Epoch millis are computed at start-of-day in the given timezone.
 */
data class ExportDateRange(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val zone: TimeZone,
) {
    val startEpochMillis: Long get() = startDate.atStartOfDayIn(zone).toEpochMilliseconds()

    /**
     * The last instant of [endDate], not its first. The range is inclusive of the end
     * day, and the exporter filters with `<= endEpochMillis`; start-of-day here would
     * silently drop every transaction made on the final day of the range — which for
     * the default "This month" preset is *today*, the day the user most expects to see.
     */
    val endEpochMillis: Long
        get() = endDate.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds() - 1

    fun formatDisplay(): String {
        // e.g. "1 Jan – 6 Aug 2026"
        val sameYear = startDate.year == endDate.year
        val startStr = if (sameYear) {
            "${startDate.dayOfMonth} ${startDate.monthName()}"
        } else {
            "${startDate.dayOfMonth} ${startDate.monthName()} ${startDate.year}"
        }
        val endStr = "${endDate.dayOfMonth} ${endDate.monthName()} ${endDate.year}"
        return "$startStr – $endStr"
    }

    private fun LocalDate.monthName(): String = when (monthNumber) {
        1 -> "Jan"
        2 -> "Feb"
        3 -> "Mar"
        4 -> "Apr"
        5 -> "May"
        6 -> "Jun"
        7 -> "Jul"
        8 -> "Aug"
        9 -> "Sep"
        10 -> "Oct"
        11 -> "Nov"
        12 -> "Dec"
        else -> ""
    }
}

/**
 * Quick-select presets for export date ranges.
 * Each computes the range inclusively around the current date.
 */
sealed class ExportDatePreset(val label: String) {
    abstract fun toDateRange(clock: Clock, zone: TimeZone): ExportDateRange

    data object ThisMonth : ExportDatePreset("This month") {
        override fun toDateRange(clock: Clock, zone: TimeZone): ExportDateRange {
            val today = clock.now().toLocalDateTime(zone).date
            val start = LocalDate(today.year, today.monthNumber, 1)
            val end = today
            return ExportDateRange(start, end, zone)
        }
    }

    data object LastMonth : ExportDatePreset("Last month") {
        override fun toDateRange(clock: Clock, zone: TimeZone): ExportDateRange {
            val today = clock.now().toLocalDateTime(zone).date
            val firstOfThisMonth = LocalDate(today.year, today.monthNumber, 1)
            val lastOfLastMonth = firstOfThisMonth.minus(1, DateTimeUnit.DAY)
            val start = LocalDate(lastOfLastMonth.year, lastOfLastMonth.monthNumber, 1)
            return ExportDateRange(start, lastOfLastMonth, zone)
        }
    }

    data object Last3Months : ExportDatePreset("Last 3 months") {
        override fun toDateRange(clock: Clock, zone: TimeZone): ExportDateRange {
            val today = clock.now().toLocalDateTime(zone).date
            val start = today.minus(3, DateTimeUnit.MONTH)
                .let { LocalDate(it.year, it.monthNumber, 1) }
            return ExportDateRange(start, today, zone)
        }
    }

    data object ThisYear : ExportDatePreset("This year") {
        override fun toDateRange(clock: Clock, zone: TimeZone): ExportDateRange {
            val today = clock.now().toLocalDateTime(zone).date
            val start = LocalDate(today.year, 1, 1)
            return ExportDateRange(start, today, zone)
        }
    }

    data object AllTime : ExportDatePreset("All time") {
        override fun toDateRange(clock: Clock, zone: TimeZone): ExportDateRange {
            // Epoch 0 to today
            val today = clock.now().toLocalDateTime(zone).date
            val start = LocalDate(1970, 1, 1)
            return ExportDateRange(start, today, zone)
        }
    }
}

val ALL_EXPORT_PRESETS = listOf(
    ExportDatePreset.ThisMonth,
    ExportDatePreset.LastMonth,
    ExportDatePreset.Last3Months,
    ExportDatePreset.ThisYear,
    ExportDatePreset.AllTime,
)
