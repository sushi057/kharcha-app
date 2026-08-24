package com.kharcha.app.ui.settings

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportDateRangeTest {

    private val zone = TimeZone.UTC

    // August 6, 2026
    private val fixedNow = LocalDate(2026, 8, 6).atStartOfDayIn(zone)
    private val fixedClock = FixedClock(fixedNow)

    @Test
    fun `this month preset includes entire current month`() {
        val range = ExportDatePreset.ThisMonth.toDateRange(fixedClock, zone)

        assertEquals(LocalDate(2026, 8, 1), range.startDate)
        assertEquals(LocalDate(2026, 8, 6), range.endDate)
    }

    @Test
    fun `last month preset covers entire previous month`() {
        val range = ExportDatePreset.LastMonth.toDateRange(fixedClock, zone)

        assertEquals(LocalDate(2026, 7, 1), range.startDate)
        assertEquals(LocalDate(2026, 7, 31), range.endDate)
    }

    @Test
    fun `last month handles year rollover correctly`() {
        val decemberClock = FixedClock(LocalDate(2026, 1, 15).atStartOfDayIn(zone))
        val range = ExportDatePreset.LastMonth.toDateRange(decemberClock, zone)

        assertEquals(LocalDate(2025, 12, 1), range.startDate)
        assertEquals(LocalDate(2025, 12, 31), range.endDate)
    }

    @Test
    fun `last 3 months goes back 3 months from today`() {
        val range = ExportDatePreset.Last3Months.toDateRange(fixedClock, zone)

        // From May 1 to Aug 6
        assertEquals(LocalDate(2026, 5, 1), range.startDate)
        assertEquals(LocalDate(2026, 8, 6), range.endDate)
    }

    @Test
    fun `this year starts at January 1`() {
        val range = ExportDatePreset.ThisYear.toDateRange(fixedClock, zone)

        assertEquals(LocalDate(2026, 1, 1), range.startDate)
        assertEquals(LocalDate(2026, 8, 6), range.endDate)
    }

    @Test
    fun `all time starts at epoch`() {
        val range = ExportDatePreset.AllTime.toDateRange(fixedClock, zone)

        assertEquals(LocalDate(1970, 1, 1), range.startDate)
        assertEquals(LocalDate(2026, 8, 6), range.endDate)
    }

    @Test
    fun `date range format displays correctly with same year`() {
        val range = ExportDateRange(
            LocalDate(2026, 8, 1),
            LocalDate(2026, 8, 6),
            zone,
        )

        assertEquals("1 Aug – 6 Aug 2026", range.formatDisplay())
    }

    @Test
    fun `date range format displays correctly across years`() {
        val range = ExportDateRange(
            LocalDate(2025, 12, 15),
            LocalDate(2026, 1, 15),
            zone,
        )

        assertEquals("15 Dec 2025 – 15 Jan 2026", range.formatDisplay())
    }

    @Test
    fun `start epoch millis is start of day`() {
        val range = ExportDateRange(
            LocalDate(2026, 8, 1),
            LocalDate(2026, 8, 6),
            zone,
        )

        val expectedStart = LocalDate(2026, 8, 1).atStartOfDayIn(zone).toEpochMilliseconds()
        assertEquals(expectedStart, range.startEpochMillis)
    }

    @Test
    fun `end epoch millis is the last instant of the end date, so the final day is exported`() {
        val range = ExportDateRange(
            LocalDate(2026, 8, 1),
            LocalDate(2026, 8, 6),
            zone,
        )

        // The exporter filters `occurredAt <= endEpochMillis`. Start-of-day here would
        // drop everything spent on 6 August — which, for the default "This month"
        // preset, is today.
        val noonOnTheLastDay = LocalDate(2026, 8, 6).atStartOfDayIn(zone).toEpochMilliseconds() +
            12 * 60 * 60 * 1000L
        val startOfTheNextDay = LocalDate(2026, 8, 7).atStartOfDayIn(zone).toEpochMilliseconds()

        assertTrue(noonOnTheLastDay <= range.endEpochMillis)
        assertEquals(startOfTheNextDay - 1, range.endEpochMillis)
    }
}
