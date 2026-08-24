package com.kharcha.app.ui.transactions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tapping "Today" must leave "Today" looking selected.
 *
 * The chip row used to render every preset as unselected and show one generic "Date
 * range ✕" chip for whatever range was active, so the user pressed Today and saw a chip
 * naming the mechanism instead of the choice they had just made.
 */
class DatePresetTest {

    @Test
    fun `each preset recognises its own range`() {
        DatePreset.entries.forEach { preset ->
            val (start, end) = preset.range()
            assertEquals(
                preset,
                datePresetFor(start, end),
                "${preset.label} must light up its own chip",
            )
        }
    }

    @Test
    fun `the presets are distinct, so none can shadow another`() {
        val ranges = DatePreset.entries.map { it.range() }
        assertEquals(ranges.size, ranges.toSet().size)
    }

    @Test
    fun `presets nest from today outward`() {
        val (todayStart, todayEnd) = DatePreset.Today.range()
        val (weekStart, weekEnd) = DatePreset.Week.range()
        val (monthStart, _) = DatePreset.Month.range()

        // All windows end together; only the start reaches further back.
        assertEquals(todayEnd, weekEnd)
        assert(weekStart < todayStart)
        assert(monthStart < weekStart)
    }

    @Test
    fun `no range and a half-open range match no preset`() {
        assertNull(datePresetFor(null, null))
        assertNull(datePresetFor(DatePreset.Today.range().first, null))
        assertNull(datePresetFor(null, DatePreset.Today.range().second))
    }

    @Test
    fun `an arbitrary range keeps its own Date range chip`() {
        // e.g. a single past day tapped on the dashboard's spend chart.
        assertNull(datePresetFor(1_754_006_400_000L, 1_754_092_799_999L))
    }
}
