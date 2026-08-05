package com.kharcha.parser

import kotlinx.datetime.LocalDateTime

enum class Direction {
    DEBIT, CREDIT
}

data class ParsedTransaction(
    val sourceAccount: String,
    val amount: Money,
    val direction: Direction,
    val occurredAt: LocalDateTime,
    val remark: String,
    val merchant: String?,
    val balanceAfter: Money?,
    val remarkTruncated: Boolean
)
