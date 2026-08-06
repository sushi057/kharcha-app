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
 * `priority` wins; rules are pre-sorted once at construction time. Ties are
 * broken deterministically by ascending `id` (lowest id wins) rather than by
 * the order the caller happened to supply — this must not depend on
 * caller-supplied list order, since callers (e.g. `RuleDao.observeAll()`)
 * are not guaranteed to hand back a stable order for equal priorities.
 */
class Categorizer(rules: List<RuleEntity>) {

    private val sortedRules = rules.sortedWith(
        compareByDescending<RuleEntity> { it.priority }.thenBy { it.id }
    )

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
