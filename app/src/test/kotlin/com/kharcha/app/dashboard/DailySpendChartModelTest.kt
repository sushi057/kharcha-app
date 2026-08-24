package com.kharcha.app.dashboard

import com.kharcha.parser.Currency
import com.kharcha.parser.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DailySpendChartModelTest {

    @Test
    fun augustHas31Days() {
        val august = LocalDate(2026, 8, 1)
        val data = buildDailySpendChartModel(august, emptyList())
        assertEquals(31, data.size)
    }

    @Test
    fun everyDayPresent() {
        val august = LocalDate(2026, 8, 1)
        val data = buildDailySpendChartModel(august, emptyList())
        val days = data.map { it.dayOfMonth }
        assertEquals((1..31).toList(), days)
    }

    @Test
    fun zeroSpendDaysGet0() {
        val august = LocalDate(2026, 8, 1)
        val data = buildDailySpendChartModel(august, emptyList())
        assertTrue(data.all { it.amountMinorUnits == 0L })
    }

    @Test
    fun spendDataPopulatedCorrectly() {
        val august = LocalDate(2026, 8, 1)
        val spend = listOf(
            DailySpend(LocalDate(2026, 8, 5), Currency.NPR, Money(10000L, Currency.NPR)),
            DailySpend(LocalDate(2026, 8, 15), Currency.NPR, Money(25000L, Currency.NPR)),
        )
        val data = buildDailySpendChartModel(august, spend)

        assertEquals(0L, data[0].amountMinorUnits) // day 1
        assertEquals(10000L, data[4].amountMinorUnits) // day 5
        assertEquals(25000L, data[14].amountMinorUnits) // day 15 sits at index 14
        assertEquals(0L, data[13].amountMinorUnits) // day 14 had no spend
        assertEquals(31, data.size)
    }

    @Test
    fun februaryNonLeapYear28Days() {
        val feb = LocalDate(2025, 2, 1)
        val data = buildDailySpendChartModel(feb, emptyList())
        assertEquals(28, data.size)
    }

    @Test
    fun februaryLeapYear29Days() {
        val feb = LocalDate(2024, 2, 1)
        val data = buildDailySpendChartModel(feb, emptyList())
        assertEquals(29, data.size)
    }

    @Test
    fun aprilHas30Days() {
        val april = LocalDate(2026, 4, 1)
        val data = buildDailySpendChartModel(april, emptyList())
        assertEquals(30, data.size)
    }
}
