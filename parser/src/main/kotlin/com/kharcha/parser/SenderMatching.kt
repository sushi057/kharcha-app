package com.kharcha.parser

/**
 * Decides whether a message's sender address is the bank's alias.
 *
 * The alias is `SBL_Alert`, and the underscore is the whole problem. It survives to the
 * handset intact on the real network, but it is not a character every layer between the
 * SMSC and the SMS provider agrees on: the Android emulator's console rewrites it to
 * `SBL§Alert`, and a separator-substituted `SBL-Alert` is the same kind of accident.
 * A message dropped for that reason is dropped *silently* — the user simply never sees
 * the transaction — so the match tolerates the substitution.
 *
 * What it does not do is go back to `ADDRESS LIKE 'SBL_Alert'`, which is where this
 * started: SQL `LIKE` reads `_` as "any single character", so it also accepted
 * `SBLXAlert` and anything else shaped like the alias. Here the underscore may only be
 * a non-alphanumeric character, or nothing at all. Letters and digits in that position
 * make it a different sender.
 */
object SenderMatching {

    /** True when [actual] is [senderId], allowing a substituted or dropped separator. */
    fun matches(actual: String?, senderId: String): Boolean {
        if (actual == null) return false
        return patternFor(senderId).matches(actual.trim())
    }

    /**
     * SQL `LIKE` patterns covering every address [matches] can accept, for use as a
     * cheap prefilter on a content-provider query. `LIKE` cannot express "separator or
     * nothing" — its `_` is one character of any kind — so the patterns are deliberately
     * looser than [matches] and every row they return must still be passed through it.
     */
    fun sqlLikePatterns(senderId: String): List<String> = listOf(
        senderId,
        senderId.replace("_", ""),
    ).distinct()

    private val cache = mutableMapOf<String, Regex>()

    private fun patternFor(senderId: String): Regex = cache.getOrPut(senderId) {
        val pattern = senderId.split("_").joinToString("[^A-Za-z0-9]?") { Regex.escape(it) }
        Regex(pattern, RegexOption.IGNORE_CASE)
    }
}
