package com.kharcha.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Warm-tinted, dark-first neutral ramp. Every step carries a small amount of
 * red/orange bias over green/blue so nothing in the app ever reads as pure
 * black or untinted gray — this is checked by [ThemeTest].
 *
 * The ramp runs from the near-black app background (index 0) to a warm
 * off-white used for the lightest surfaces/text (last index).
 */
object KharchaNeutrals {
    // Deepest background: warm near-black, not #000000.
    const val Neutral0 = 0xFF14100E.toInt()
    const val Neutral10 = 0xFF1C1714.toInt()
    const val Neutral20 = 0xFF261F1A.toInt()
    const val Neutral30 = 0xFF352C25.toInt()
    const val Neutral40 = 0xFF4A3E34.toInt()
    const val Neutral50 = 0xFF6B5B4C.toInt()
    const val Neutral60 = 0xFF8F7B68.toInt()
    const val Neutral70 = 0xFFB3A08C.toInt()
    const val Neutral80 = 0xFFD6C7B4.toInt()
    const val Neutral90 = 0xFFECE1D4.toInt()
    const val Neutral95 = 0xFFF6EFE6.toInt()

    /** Exposed as ARGB ints, in dark-to-light order, for the tinted-ness test. */
    val all: List<Int> = listOf(
        Neutral0, Neutral10, Neutral20, Neutral30, Neutral40, Neutral50,
        Neutral60, Neutral70, Neutral80, Neutral90, Neutral95,
    )
}

/**
 * App accent and semantic colors. Debit and credit differ in both hue and
 * lightness so they stay distinguishable for color-blind users when later
 * screens pair them with a +/- sign or a "debit"/"credit" label.
 */
object KharchaColors {
    // Credit: warm amber-gold, lighter — money arriving.
    val credit = Color(0xFFE0A94A)

    // Debit: deep clay red, darker/more saturated — money leaving.
    val debit = Color(0xFFD8583F)

    val surface = Color(KharchaNeutrals.Neutral10)
    val surfaceVariant = Color(KharchaNeutrals.Neutral20)
    val onSurface = Color(KharchaNeutrals.Neutral90)
    val onSurfaceVariant = Color(KharchaNeutrals.Neutral70)

    val background = Color(KharchaNeutrals.Neutral0)
    val onBackground = Color(KharchaNeutrals.Neutral95)

    val outline = Color(KharchaNeutrals.Neutral40)
}
