package com.kharcha.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The app must open on the same destination the bottom bar lists first — otherwise
 * launching lands on tab 2 with tab 1 highlighted (reviewer's cheap finding on
 * `KharchaNavHost.kt:98`, a Task 9 leftover from when Transactions was the only screen).
 */
class KharchaNavHostTest {

    @Test
    fun `the start destination is the first bottom-nav item`() {
        assertEquals(kharchaDestinations.first(), kharchaStartDestination)
    }
}
