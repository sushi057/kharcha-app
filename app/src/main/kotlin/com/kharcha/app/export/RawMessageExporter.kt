package com.kharcha.app.export

import com.kharcha.data.RawMessage
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Exports the raw SMS inbox — every `raw_messages` row, whatever its parse outcome — as
 * JSON, so a build's parsing behaviour can be reproduced off-device against real input.
 *
 * This deliberately does *not* filter by ignored/dismissed/parsed: the point is a faithful
 * dump of what the app was given, not a view of what it made of it. [TransactionExporter]
 * is the one that exports outcomes.
 *
 * JSON rather than CSV because bodies are multi-line and quoting them into a spreadsheet
 * makes them harder, not easier, to read back.
 */
class RawMessageExporter {

    fun toJson(messages: List<RawMessage>, zone: TimeZone): String {
        val sorted = messages.sortedBy { it.id }
        val exportedAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            .toLocalDateTime(zone)
            .toString()

        return buildString {
            append("{\n")
            append("  \"exported_at\": \"$exportedAt\",\n")
            append("  \"count\": ${sorted.size},\n")
            append("  \"messages\": [\n")
            sorted.forEachIndexed { index, message ->
                append(messageJson(message, zone))
                if (index != sorted.lastIndex) append(",")
                append("\n")
            }
            append("  ]\n")
            append("}\n")
        }
    }

    private fun messageJson(message: RawMessage, zone: TimeZone): String {
        val receivedAt = Instant.fromEpochMilliseconds(message.receivedAtEpochMillis)
            .toLocalDateTime(zone)
            .toString()
        val ignoreReason = message.ignoreReason
            ?.let { "\"${jsonEscape(it)}\"" }
            ?: "null"

        return buildString {
            append("    {\n")
            append("      \"id\": ${message.id},\n")
            append("      \"sender\": \"${jsonEscape(message.sender)}\",\n")
            append("      \"received_at\": \"$receivedAt\",\n")
            append("      \"received_at_epoch_millis\": ${message.receivedAtEpochMillis},\n")
            append("      \"content_hash\": \"${jsonEscape(message.contentHash)}\",\n")
            append("      \"ignored\": ${message.ignored},\n")
            append("      \"ignore_reason\": $ignoreReason,\n")
            append("      \"dismissed\": ${message.dismissed},\n")
            append("      \"body\": \"${jsonEscape(message.body)}\"\n")
            append("    }")
        }
    }

    private fun jsonEscape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
