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
    // Use toLongOrNull() to safely handle overflow (too many digits for Long)
    val integerValue = integerPart.toLongOrNull() ?: return null
    val integerMinorUnits = try {
        Math.multiplyExact(integerValue, 100)
    } catch (e: ArithmeticException) {
        return null  // Overflow on multiplication
    }

    val decimalMinorUnits = when (decimalPart.length) {
        0 -> 0L
        1 -> {
            val decValue = decimalPart.toLongOrNull() ?: return null
            decValue * 10  // e.g., ".5" becomes 50
        }
        2 -> decimalPart.toLongOrNull() ?: return null  // e.g., ".50" becomes 50
        else -> return null  // More than 2 decimals should have been rejected by regex
    }

    val totalMinorUnits = try {
        Math.addExact(integerMinorUnits, decimalMinorUnits)
    } catch (e: ArithmeticException) {
        return null  // Overflow on addition
    }
    return Money(totalMinorUnits, currency)
}
