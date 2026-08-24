package com.kharcha.app.ui.budgets

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BudgetsCalculationsTest {

    @Test
    fun `pace computation across month positions`() {
        val monthStart = LocalDate(2026, 8, 1)

        // Day 5 of August: 4 days elapsed of 31 -> 12%
        val day5 = LocalDate(2026, 8, 5)
        val pace5 = computePace(day5, monthStart, 400_00L, 1000_00L)
        assertEquals("You're 12% through August and 40% through your budget — running ahead.", pace5)

        // Day 15 of August: 14 days elapsed of 31 -> 45%
        val day15 = LocalDate(2026, 8, 15)
        val pace15 = computePace(day15, monthStart, 500_00L, 1000_00L)
        assertEquals("You're 45% through August and 50% through your budget — on pace.", pace15)

        // Day 25 of August: 24 days elapsed of 31 -> 77%
        val day25 = LocalDate(2026, 8, 25)
        val pace25 = computePace(day25, monthStart, 900_00L, 1000_00L)
        assertEquals("You're 77% through August and 90% through your budget — running ahead.", pace25)
    }

    @Test
    fun `pace computation returns null when budget is zero or negative`() {
        val monthStart = LocalDate(2026, 8, 1)
        val day5 = LocalDate(2026, 8, 5)

        assertNull(computePace(day5, monthStart, 100_00L, 0L))
        assertNull(computePace(day5, monthStart, 100_00L, -1000_00L))
    }

    @Test
    fun `month length is correct for leap and non-leap years`() {
        // Non-leap year
        assertEquals(28, getMonthLength(2025, 2))
        // Leap year
        assertEquals(29, getMonthLength(2024, 2))
        // 30-day months
        assertEquals(30, getMonthLength(2026, 4))
        assertEquals(30, getMonthLength(2026, 6))
        assertEquals(30, getMonthLength(2026, 9))
        assertEquals(30, getMonthLength(2026, 11))
        // 31-day months
        assertEquals(31, getMonthLength(2026, 1))
        assertEquals(31, getMonthLength(2026, 8))
    }

    @Test
    fun `suggest budget amount computes 3-month average correctly`() {
        // 3-month average of [1000, 2000, 3000] = 2000
        assertEquals(2000L, suggestBudgetAmount(listOf(1000L, 2000L, 3000L)))

        // Average of [1000] = 1000
        assertEquals(1000L, suggestBudgetAmount(listOf(1000L)))

        // Average with fractional amount is rounded up: (1000 + 2000 + 1500) / 3 = 1500
        assertEquals(1500L, suggestBudgetAmount(listOf(1000L, 2000L, 1500L)))

        // Average that needs rounding up: (1000 + 2000 + 1001) / 3 = 1333.67 → 1334
        assertEquals(1334L, suggestBudgetAmount(listOf(1000L, 2000L, 1001L)))

        // Empty history returns null
        assertNull(suggestBudgetAmount(emptyList()))
    }

    @Test
    fun `budget status classification uses alert threshold`() {
        val limitMinorUnits = 1000_00L
        val thresholdPercent = 80

        // Normal: 0-79%
        assertEquals(BudgetStatus.NORMAL, classifyBudgetStatus(500_00L, limitMinorUnits, thresholdPercent))
        assertEquals(BudgetStatus.NORMAL, classifyBudgetStatus(799_00L, limitMinorUnits, thresholdPercent))

        // Near limit: 80-99%
        assertEquals(BudgetStatus.NEAR_LIMIT, classifyBudgetStatus(800_00L, limitMinorUnits, thresholdPercent))
        assertEquals(BudgetStatus.NEAR_LIMIT, classifyBudgetStatus(999_00L, limitMinorUnits, thresholdPercent))

        // Over budget: 100%+
        assertEquals(BudgetStatus.OVER_BUDGET, classifyBudgetStatus(1000_00L, limitMinorUnits, thresholdPercent))
        assertEquals(BudgetStatus.OVER_BUDGET, classifyBudgetStatus(1500_00L, limitMinorUnits, thresholdPercent))

        // Zero or negative budget returns null
        assertNull(classifyBudgetStatus(500_00L, 0L, thresholdPercent))
        assertNull(classifyBudgetStatus(500_00L, -1000_00L, thresholdPercent))
    }

    @Test
    fun `leap year detection is accurate`() {
        // Regular leap year
        assert(isLeapYear(2024))
        assert(isLeapYear(2020))

        // Non-leap year
        assert(!isLeapYear(2025))
        assert(!isLeapYear(2023))

        // Century years: divisible by 400 is leap, others are not
        assert(isLeapYear(2000))
        assert(!isLeapYear(1900))
        assert(!isLeapYear(1800))
    }
}
