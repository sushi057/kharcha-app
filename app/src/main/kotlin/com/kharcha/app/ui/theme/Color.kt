package com.kharcha.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Warm-tinted neutral ramp, shared between both color schemes. Every step
 * carries a small amount of red/orange bias over green/blue so nothing in
 * the app ever reads as pure black or untinted gray — this is checked by
 * [ThemeTest].
 *
 * The dark scheme ramps from the near-black app background (Neutral0) to the
 * warm off-white used for light text (Neutral95). The light scheme reverses
 * this relationship: Neutral95 becomes the background, Neutral0 the text.
 * Both schemes maintain warm tinting across their entire range.
 */
object KharchaNeutrals {
    // Deepest background (dark scheme): warm near-black, not #000000.
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
 * Warm-accented semantic colors for the dark scheme. Debit, credit, and accent
 * differ in both hue and lightness so they stay distinguishable for color-blind
 * users when later screens pair them with signs or labels. Credit shifted from
 * amber to sage green to separate "money arrived" (credit) from "this is a button"
 * (accent gold) — the old amber was too close to the new gold.
 */
object KharchaDarkColors {
    // Accent: warm gold, medium tone — buttons, active highlights.
    val accent = Color(0xFFD4A03C)

    // Debit: deep clay red, darker/more saturated — money leaving.
    val debit = Color(0xFFD8583F)

    // Credit: sage green, higher hue and lightness — money arriving.
    val credit = Color(0xFF8FAE4F)

    val background = Color(KharchaNeutrals.Neutral0)
    val surface = Color(KharchaNeutrals.Neutral10)
    val surfaceContainer = Color(KharchaNeutrals.Neutral20)
    val surfaceContainerHigh = Color(KharchaNeutrals.Neutral30)
    val onBackground = Color(KharchaNeutrals.Neutral95)
    val onSurface = Color(KharchaNeutrals.Neutral95)
    val surfaceVariant = Color(KharchaNeutrals.Neutral20)
    val onSurfaceVariant = Color(KharchaNeutrals.Neutral70)
    val outline = Color(KharchaNeutrals.Neutral60)

    /**
     * The accent at 15% over [background], pre-flattened. This is the "selected but quiet"
     * fill — nav pill, selected chip — and it must exist as a scheme role, because Material
     * components reach for `secondaryContainer` on their own and fall back to the baseline
     * purple when it is left unset. See [kharchaDarkColorScheme].
     */
    val accentSoft = Color(0xFF312615)

    val surfaceContainerLowest = Color(0xFF100D0B)
    val surfaceContainerLow = Color(0xFF191411)
    val surfaceContainerHighest = Color(KharchaNeutrals.Neutral40)
    val surfaceBright = Color(KharchaNeutrals.Neutral30)
    val surfaceDim = Color(KharchaNeutrals.Neutral0)
    val outlineVariant = Color(KharchaNeutrals.Neutral40)
    val debitContainer = Color(0xFF4A1E14)
    val onDebitContainer = Color(0xFFF5C9BF)
    val creditContainer = Color(0xFF2C3B12)
    val onCreditContainer = Color(0xFFD7E6B8)
}

/**
 * Warm-accented semantic colors for the light scheme, built on the same ramp
 * as the dark scheme but using reversed and darkened values. Light mode reverses
 * the neutral hierarchy: what was near-black becomes near-white, and vice versa.
 * Accent, debit, and credit are darkened from their dark counterparts to maintain
 * readability on the light background. All pairs are tuned to meet WCAG AA 4.5:1
 * contrast on their own surfaces.
 */
object KharchaLightColors {
    // Accent: deep warm brown, darkened for light background — buttons, active highlights.
    val accent = Color(0xFFA16207)

    // Debit: deep clay red, darkened from dark scheme — money leaving.
    val debit = Color(0xFFB0402B)

    // Credit: sage green, darkened from dark scheme — money arriving.
    val credit = Color(0xFF5C7A2E)

    val background = Color(0xFFFAF7F2)
    val surface = Color(0xFFFFFFFF)
    val surfaceContainer = Color(0xFFF1EAE0)
    val surfaceContainerHigh = Color(0xFFE7DCCD)
    val onBackground = Color(0xFF241C16)
    val onSurface = Color(0xFF241C16)
    val surfaceVariant = Color(0xFFF1EAE0)
    // Darkened to meet WCAG AA 4.5:1 contrast against surfaceVariant
    val onSurfaceVariant = Color(0xFF6A5740)
    val outline = Color(0xFF9A8A7C)

    /** The accent at 12% over [background], pre-flattened — see [KharchaDarkColors.accentSoft]. */
    val accentSoft = Color(0xFFEFE5D6)

    val surfaceContainerLowest = Color(0xFFFFFFFF)
    val surfaceContainerLow = Color(0xFFFBF8F4)
    val surfaceContainerHighest = Color(0xFFE0D3C1)
    val surfaceBright = Color(0xFFFFFFFF)
    val surfaceDim = Color(0xFFE9E0D4)
    val outlineVariant = Color(KharchaNeutrals.Neutral80)
    val debitContainer = Color(0xFFF5DED8)
    val onDebitContainer = Color(0xFF5A1E13)
    val creditContainer = Color(0xFFE4EBD3)
    val onCreditContainer = Color(0xFF2C3B12)
}
