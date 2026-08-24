package com.kharcha.app.export

import com.kharcha.data.RawMessage
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawMessageExporterTest {

    private val exporter = RawMessageExporter()
    private val utc = TimeZone.UTC

    private fun message(
        id: Long,
        body: String = "NPR 100.00 debited",
        ignored: Boolean = false,
        ignoreReason: String? = null,
        dismissed: Boolean = false,
    ) = RawMessage(
        id = id,
        sender = "SBL_Alert",
        body = body,
        receivedAtEpochMillis = 1_700_000_000_000,
        contentHash = "hash$id",
        ignored = ignored,
        ignoreReason = ignoreReason,
        dismissed = dismissed,
    )

    @Test
    fun `exports every message regardless of state`() {
        val json = exporter.toJson(
            listOf(
                message(1),
                message(2, ignored = true, ignoreReason = "OTP"),
                message(3, dismissed = true),
            ),
            utc,
        )

        assertTrue(json.contains("\"count\": 3"))
        assertTrue(json.contains("\"id\": 1"))
        assertTrue(json.contains("\"ignore_reason\": \"OTP\""))
        assertTrue(json.contains("\"dismissed\": true"))
    }

    @Test
    fun `null ignore reason is a JSON null, not a quoted string`() {
        val json = exporter.toJson(listOf(message(1)), utc)

        assertTrue(json.contains("\"ignore_reason\": null"))
    }

    @Test
    fun `escapes quotes and newlines in the body`() {
        val json = exporter.toJson(listOf(message(1, body = "line1\nsaid \"hi\"")), utc)

        assertTrue(json.contains("\"body\": \"line1\\nsaid \\\"hi\\\"\""))
    }

    @Test
    fun `empty inbox produces an empty array`() {
        val json = exporter.toJson(emptyList(), utc)

        assertTrue(json.contains("\"count\": 0"))
        assertTrue(json.contains("\"messages\": [\n  ]"))
    }

    @Test
    fun `orders by id ascending`() {
        val json = exporter.toJson(listOf(message(3), message(1), message(2)), utc)

        val ids = Regex("\"id\": (\\d+)").findAll(json).map { it.groupValues[1] }.toList()
        assertEquals(listOf("1", "2", "3"), ids)
    }
}
