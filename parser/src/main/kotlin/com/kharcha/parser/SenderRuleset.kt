package com.kharcha.parser

interface SenderRuleset {
    val senderId: String
    fun parse(body: String): ParseResult
}
