package com.kharcha.app.chart

import kotlin.test.Test
import kotlin.test.assertEquals

class AxisUtilsTest {

    @Test
    fun zeroAndNegativeInputsReturn1() {
        assertEquals(1L, niceAxisMax(0L))
        assertEquals(1L, niceAxisMax(-100L))
    }

    @Test
    fun smallValuesRoundToNearestNiceValue() {
        assertEquals(1L, niceAxisMax(1L))
        assertEquals(2L, niceAxisMax(2L))
        // The axis max must never fall below the data, or the tallest bar is clipped.
        assertEquals(5L, niceAxisMax(3L))
        assertEquals(5L, niceAxisMax(4L))
        assertEquals(5L, niceAxisMax(5L))
    }

    @Test
    fun valuesRoundUpToPowersOf10WithMultipliers() {
        assertEquals(10L, niceAxisMax(6L))
        assertEquals(10L, niceAxisMax(10L))
        assertEquals(20L, niceAxisMax(11L))
        assertEquals(50L, niceAxisMax(30L))
        assertEquals(100L, niceAxisMax(60L))
    }

    @Test
    fun thousandsRoundCorrectly() {
        assertEquals(2000L, niceAxisMax(1500L))
        assertEquals(5000L, niceAxisMax(3000L))
        assertEquals(10000L, niceAxisMax(6000L))
        assertEquals(20000L, niceAxisMax(15000L))
    }

    @Test
    fun largeValuesRoundToNiceMultiples() {
        assertEquals(100000L, niceAxisMax(75000L))
        assertEquals(200000L, niceAxisMax(150000L))
        assertEquals(1000000L, niceAxisMax(750000L))
    }

    @Test
    fun exactPowersOf10ReturnThemselves() {
        assertEquals(100L, niceAxisMax(100L))
        assertEquals(1000L, niceAxisMax(1000L))
        assertEquals(10000L, niceAxisMax(10000L))
    }

    @Test
    fun twoPointFiveMultiplesWork() {
        // A value already sitting on a nice number is returned unchanged...
        assertEquals(200L, niceAxisMax(200L))
        // ...and only a value above it reaches for the 2.5x step.
        assertEquals(250L, niceAxisMax(201L))
        assertEquals(2500L, niceAxisMax(2001L))
        assertEquals(25000L, niceAxisMax(20001L))
    }

    @Test
    fun formatAxisLabelAbbreviatesWholeUnits() {
        assertEquals("234", formatAxisLabel(234L))
        assertEquals("2.5k", formatAxisLabel(2500L))
        assertEquals("10k", formatAxisLabel(10_000L))
        assertEquals("1M", formatAxisLabel(1_000_000L))
        assertEquals("1.5M", formatAxisLabel(1_500_000L))
        // A trailing .0 is never rendered.
        assertEquals("3k", formatAxisLabel(3_000L))
    }
}
