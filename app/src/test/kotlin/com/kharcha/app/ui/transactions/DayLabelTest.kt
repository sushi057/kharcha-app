package com.kharcha.app.ui.transactions

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The transactions list grouped days with `SimpleDateFormat` + `TimeZone.getDefault()`
 * while Dashboard, Budgets and the notifier all use the Hilt-injected
 * `kotlinx.datetime.TimeZone` — two different definitions of which day a transaction
 * falls on (reviewer's cheap finding on `TransactionsScreen.kt:177-181`).
 */
class DayLabelTest {

    // 2026-08-05T20:00:00Z — still 5 August in UTC, already 6 August in Kathmandu (+05:45).
    private val epochMillis = 1_785_960_000_000L

    @Test
    fun `the day label honours the supplied zone`() {
        val utc = dayLabel(epochMillis, TimeZone.UTC)
        val kathmandu = dayLabel(epochMillis, TimeZone.of("Asia/Kathmandu"))
        assertNotEquals(
            utc,
            kathmandu,
            "the same instant falls on different calendar days in UTC and Asia/Kathmandu, " +
                "so the label must differ — it is ignoring the injected zone",
        )
    }

    @Test
    fun `the day label reads as a human date in the supplied zone`() {
        assertEquals("Wed, 5 Aug 2026", dayLabel(epochMillis, TimeZone.UTC))
        assertEquals("Thu, 6 Aug 2026", dayLabel(epochMillis, TimeZone.of("Asia/Kathmandu")))
    }
}
