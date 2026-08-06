package com.kharcha.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Debit/credit are semantic roles Material 3's [androidx.compose.material3.ColorScheme] has
 * no slot for, so they get their own theme-aware holder rather than being read off a global
 * constant. Each scheme carries its own pair: the dark scheme's amber/clay accents are
 * tuned for a near-black background and are far too light to read on the light scheme's
 * warm off-white, so the light scheme uses darkened counterparts. Both pairs clear WCAG AA
 * on their own `background` and `surface` — enforced by
 * [com.kharcha.app.ui.theme.ThemeContrastTest].
 */
data class KharchaSemanticColors(
    val debit: Color,
    val credit: Color,
)

internal val kharchaDarkSemanticColors = KharchaSemanticColors(
    // Deep clay red, darker/more saturated — money leaving.
    debit = KharchaColors.debit,
    // Warm amber-gold, lighter — money arriving.
    credit = KharchaColors.credit,
)

internal val kharchaLightSemanticColors = KharchaSemanticColors(
    debit = Color(0xFFA33520),
    credit = Color(0xFF805712),
)

private val LocalKharchaSemanticColors = staticCompositionLocalOf { kharchaDarkSemanticColors }

/**
 * Theme-aware debit/credit accents. Read these from composables —
 * `KharchaSemantics.debit`, not `KharchaColors.debit` — so an amount stays legible in
 * whichever scheme [KharchaTheme] selected.
 */
object KharchaSemantics {
    val debit: Color
        @Composable @ReadOnlyComposable get() = LocalKharchaSemanticColors.current.debit

    val credit: Color
        @Composable @ReadOnlyComposable get() = LocalKharchaSemanticColors.current.credit
}

/**
 * Dark-first Kharcha theme. The app is opened at a glance, often at night, so dark is the
 * primary experience; light mode is genuinely supported (not half-supported) for users
 * whose system setting requests it, and every neutral in both schemes stays warm-tinted —
 * see [KharchaNeutrals].
 *
 * Composables must read colors and type from `MaterialTheme.colorScheme` /
 * `MaterialTheme.typography` (and [KharchaSemantics] for debit/credit), never from the
 * [KharchaColors] / [KharchaTypography] objects directly: those hold one scheme's
 * constants and cannot respond to the theme at all. Tasks 10 and 11 did exactly that,
 * which drew Neutral95 text on a Neutral95 background in light mode.
 * [com.kharcha.app.ui.theme.PaletteUsageTest] fails the build if it happens again.
 */
internal val kharchaDarkColorScheme = darkColorScheme(
    primary = kharchaDarkSemanticColors.credit,
    onPrimary = Color(KharchaNeutrals.Neutral0),
    secondary = kharchaDarkSemanticColors.debit,
    background = KharchaColors.background,
    onBackground = KharchaColors.onBackground,
    surface = KharchaColors.surface,
    onSurface = KharchaColors.onSurface,
    surfaceVariant = KharchaColors.surfaceVariant,
    onSurfaceVariant = KharchaColors.onSurfaceVariant,
    outline = KharchaColors.outline,
)

internal val kharchaLightColorScheme = lightColorScheme(
    primary = kharchaLightSemanticColors.credit,
    onPrimary = Color(KharchaNeutrals.Neutral95),
    secondary = kharchaLightSemanticColors.debit,
    background = Color(KharchaNeutrals.Neutral95),
    onBackground = Color(KharchaNeutrals.Neutral10),
    surface = Color(KharchaNeutrals.Neutral90),
    onSurface = Color(KharchaNeutrals.Neutral10),
    surfaceVariant = Color(KharchaNeutrals.Neutral80),
    onSurfaceVariant = Color(KharchaNeutrals.Neutral40),
    outline = Color(KharchaNeutrals.Neutral50),
)

@Composable
fun KharchaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) kharchaDarkColorScheme else kharchaLightColorScheme
    val semanticColors = if (darkTheme) kharchaDarkSemanticColors else kharchaLightSemanticColors

    CompositionLocalProvider(LocalKharchaSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KharchaTypography,
            content = content,
        )
    }
}
