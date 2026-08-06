package com.kharcha.parser

sealed interface ParseResult {
    data class Parsed(val transaction: ParsedTransaction) : ParseResult
    data class Ignored(val reason: String) : ParseResult
    data object Unrecognized : ParseResult
}
