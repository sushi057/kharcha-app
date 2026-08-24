package com.kharcha.app.ui.settings

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Clock that returns a fixed instant for deterministic testing.
 */
class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}
