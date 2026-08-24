package com.kharcha.app.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant

class ExportFileNamerTest {

    private val zone = TimeZone.of("UTC")

    private fun epochOf(year: Int, month: Int, day: Int): Long {
        val date = LocalDate(year, month, day)
        val instant = date.atTime(0, 0, 0, 0).toInstant(zone)
        return instant.toEpochMilliseconds()
    }

    @Test
    fun `filename format for single month`() {
        val namer = ExportFileNamer()
        val start = epochOf(2026, 1, 1)
        val end = epochOf(2026, 1, 31)

        val csvName = namer.csvFilename(start, end)
        assertEquals("kharcha-2026-01-01-to-2026-01-31.csv", csvName)
    }

    @Test
    fun `filename format for single day`() {
        val namer = ExportFileNamer()
        val date = epochOf(2026, 7, 15)

        val csvName = namer.csvFilename(date, date)
        assertEquals("kharcha-2026-07-15-to-2026-07-15.csv", csvName)
    }

    @Test
    fun `filename format for multiple months`() {
        val namer = ExportFileNamer()
        val start = epochOf(2026, 1, 1)
        val end = epochOf(2026, 3, 31)

        val csvName = namer.csvFilename(start, end)
        assertEquals("kharcha-2026-01-01-to-2026-03-31.csv", csvName)
    }

    @Test
    fun `JSON filename has json extension`() {
        val namer = ExportFileNamer()
        val start = epochOf(2026, 1, 1)
        val end = epochOf(2026, 1, 31)

        val jsonName = namer.jsonFilename(start, end)
        assertEquals("kharcha-2026-01-01-to-2026-01-31.json", jsonName)
    }

    @Test
    fun `filename uses ISO-8601 date format`() {
        val namer = ExportFileNamer()
        val start = epochOf(2025, 12, 25)
        val end = epochOf(2026, 1, 15)

        val csvName = namer.csvFilename(start, end)
        // Should use yyyy-MM-dd format
        assertEquals(true, csvName.contains("2025-12-25"), "Start date in ISO format")
        assertEquals(true, csvName.contains("2026-01-15"), "End date in ISO format")
    }

    @Test
    fun `the filename uses the device's zone, not UTC`() {
        // Kathmandu is +05:45, so a local midnight is the previous day in UTC. Naming the
        // file in UTC used to make a 1-24 August export read as starting on 31 July.
        val kathmandu = TimeZone.of("Asia/Kathmandu")
        val startOfAugust = LocalDate(2026, 8, 1).atTime(0, 0).toInstant(kathmandu)

        val name = ExportFileNamer(kathmandu).csvFilename(
            startOfAugust.toEpochMilliseconds(),
            startOfAugust.toEpochMilliseconds(),
        )

        assertEquals("kharcha-2026-08-01-to-2026-08-01.csv", name)
    }
}
