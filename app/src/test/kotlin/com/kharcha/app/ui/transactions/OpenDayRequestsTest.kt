package com.kharcha.app.ui.transactions

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The hand-off behind "tap a bar on the daily-spend chart, land on that day's
 * transactions". [TransactionsViewModelTest] covers the receiving end; this covers the
 * promise the channel itself makes — that a request is delivered once.
 */
class OpenDayRequestsTest {

    @Test
    fun `a request is readable until it is consumed`() {
        val bus = OpenDayRequests()
        assertNull(bus.requests.value)

        bus.request(LocalDate(2026, 8, 14))
        assertEquals(LocalDate(2026, 8, 14), bus.requests.value?.date)

        bus.consume()
        assertNull(
            bus.requests.value,
            "a consumed request must not re-apply the filter next time the tab is opened",
        )
    }

    @Test
    fun `a second request replaces the first`() {
        val bus = OpenDayRequests()
        bus.request(LocalDate(2026, 8, 14))
        bus.request(LocalDate(2026, 8, 15))
        assertEquals(LocalDate(2026, 8, 15), bus.requests.value?.date)
    }
}
