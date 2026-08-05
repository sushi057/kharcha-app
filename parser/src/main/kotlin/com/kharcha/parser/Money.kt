package com.kharcha.parser

enum class Currency {
    NPR, USD
}

data class Money(val minorUnits: Long, val currency: Currency)

fun parseAmount(text: String, currency: Currency): Money? {
    if (text.isEmpty()) return null

    // Strip commas
    val stripped = text.replace(",", "")

    // Validate: must match \d+(\.\d{1,2})?$ (no leading minus, etc)
    if (!stripped.matches(Regex("^\\d+(\\.\\d{1,2})?$"))) return null

    // Parse into integer and decimal parts
    val parts = stripped.split(".")
    val integerPart = parts[0]
    val decimalPart = if (parts.size > 1) parts[1] else ""

    // Convert to minor units: integer * 100 + decimal (zero-padded)
    val integerMinorUnits = integerPart.toLong() * 100
    val decimalMinorUnits = when (decimalPart.length) {
        0 -> 0L
        1 -> decimalPart.toLong() * 10  // e.g., ".5" becomes 50
        2 -> decimalPart.toLong()        // e.g., ".50" becomes 50
        else -> return null  // More than 2 decimals should have been rejected by regex
    }

    val totalMinorUnits = integerMinorUnits + decimalMinorUnits
    return Money(totalMinorUnits, currency)
}
