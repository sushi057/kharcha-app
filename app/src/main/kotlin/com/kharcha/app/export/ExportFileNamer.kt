package com.kharcha.app.export

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Generates consistent filenames for exported transaction data.
 */
class ExportFileNamer(private val zone: TimeZone = TimeZone.currentSystemDefault()) {

    /**
     * Generate a CSV filename for a date range.
     *
     * @param startEpochMillis Inclusive start date
     * @param endEpochMillis Inclusive end date
     * @return Filename like "kharcha-2026-01-01-to-2026-01-31.csv"
     */
    fun csvFilename(startEpochMillis: Long, endEpochMillis: Long): String {
        val start = epochMillisToIsoDate(startEpochMillis)
        val end = epochMillisToIsoDate(endEpochMillis)
        return "kharcha-$start-to-$end.csv"
    }

    /**
     * Generate a JSON filename for a date range.
     *
     * @param startEpochMillis Inclusive start date
     * @param endEpochMillis Inclusive end date
     * @return Filename like "kharcha-2026-01-01-to-2026-01-31.json"
     */
    fun jsonFilename(startEpochMillis: Long, endEpochMillis: Long): String {
        val start = epochMillisToIsoDate(startEpochMillis)
        val end = epochMillisToIsoDate(endEpochMillis)
        return "kharcha-$start-to-$end.json"
    }

    /**
     * Formats in the device's zone, not UTC. The range boundaries are local midnights, so
     * UTC pushed every one of them backwards in Nepal (+05:45): a "This month" export on
     * 24 August was named `kharcha-2026-07-31-to-2026-08-24`, claiming a July start it
     * did not contain.
     */
    private fun epochMillisToIsoDate(epochMillis: Long): String {
        return Instant.fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(zone)
            .date
            .toString()
    }
}
