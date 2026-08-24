package com.kharcha.app.chart

/**
 * Chart axis maths, shared by every chart in the app.
 *
 * This lives in its own package because it previously did not: the dashboard and the
 * budgets screen each grew their own `niceAxisMax` and `formatAxisLabel` with different
 * rules, so the same data drew visibly different axes depending on which screen you were
 * looking at. One implementation, one convention.
 */

/**
 * Rounds [rawMax] up to the next "nice" axis maximum: 1, 2, 2.5 or 5 times a power of
 * ten. 15 becomes 20, 201 becomes 250, 2400 becomes 2500, 6000 becomes 10000.
 *
 * Auto-fitted axes were the source of the "random increments" the charts used to show:
 * fitting gridlines to the data's exact maximum produces labels like 4,817 and 9,634,
 * which nobody can read a value off. Snapping the top of the axis to a round number makes
 * every gridline below it round too.
 *
 * The decade is found by repeated division rather than `log10`, because `log10(1000.0)`
 * can land fractionally below 3.0 and silently pick the wrong decade — precisely the
 * class of bug this function exists to eliminate.
 *
 * Returns 1 for non-positive input, so an empty or all-zero month still yields a
 * drawable axis rather than a degenerate zero-height one.
 */
fun niceAxisMax(rawMax: Long): Long {
    if (rawMax <= 0) return 1L

    // Largest power of ten <= rawMax.
    var power = 1L
    while (power <= rawMax / 10) {
        power *= 10
    }

    // 2.5x is computed as 25/10 to stay in integer arithmetic; at power = 1 it collapses
    // to 2, which is harmless because the 2x candidate already covers that case.
    val candidates = longArrayOf(power, 2 * power, 25 * power / 10, 5 * power)
    for (candidate in candidates) {
        if (candidate >= rawMax) return candidate
    }

    // Above 5x the decade, the next nice value is the start of the next decade.
    return 10 * power
}

/**
 * Nice axis maximum covering [values]. Returns 1 for an empty collection so callers
 * always get a usable, non-zero axis.
 */
fun axisMaxForValues(values: Collection<Long>): Long =
    niceAxisMax(values.maxOrNull() ?: 0L)

/**
 * Abbreviates a **whole-currency-unit** value for an axis label: 2500 -> "2.5k",
 * 10000 -> "10k", 1500000 -> "1.5M", 234 -> "234".
 *
 * Takes rupees, not paisa. The previous dashboard copy took minor units and abbreviated
 * them directly, so ₨10 (1000 paisa) rendered as "1k" — callers must divide by 100 first.
 * A single fractional digit is kept only when it is non-zero, so axes read "10k" rather
 * than "10.0k".
 */
fun formatAxisLabel(wholeUnits: Long): String = when {
    wholeUnits >= 1_000_000 -> abbreviate(wholeUnits, 1_000_000, "M")
    wholeUnits >= 1_000 -> abbreviate(wholeUnits, 1_000, "k")
    else -> wholeUnits.toString()
}

private fun abbreviate(value: Long, unit: Long, suffix: String): String {
    val whole = value / unit
    val tenths = (value % unit) * 10 / unit
    return if (tenths == 0L) "$whole$suffix" else "$whole.$tenths$suffix"
}
