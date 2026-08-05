package com.kharcha.data

/**
 * Assigns a category id to a transaction's remark/merchant text by matching
 * against a set of user- and seed-defined [RuleEntity] patterns.
 *
 * Real bank SMS remarks are truncated by the 160-character SMS limit, so
 * matching must tolerate a truncated haystack: rule patterns are matched as
 * substrings (or prefixes) of the merchant/remark text, never the reverse.
 * `matchesPrefix = true` uses `startsWith`; `false` uses `contains`. Both are
 * case-insensitive. When multiple rules match, the one with the highest
 * `priority` wins; rules are pre-sorted once at construction time.
 */
class Categorizer(rules: List<RuleEntity>) {

    private val sortedRules = rules.sortedByDescending { it.priority }

    fun categorize(remark: String, merchant: String?): Long? {
        val haystack = merchant ?: remark
        for (rule in sortedRules) {
            val matches = if (rule.matchesPrefix) {
                haystack.startsWith(rule.matchPattern, ignoreCase = true)
            } else {
                haystack.contains(rule.matchPattern, ignoreCase = true)
            }
            if (matches) return rule.categoryId
        }
        return null
    }
}
