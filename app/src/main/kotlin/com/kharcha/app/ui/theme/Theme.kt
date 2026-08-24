package com.kharcha.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.kharcha.app.ui.settings.ThemeMode

/**
 * Debit/credit/accent are semantic roles Material 3's [androidx.compose.material3.ColorScheme]
 * partially covers (via primary/secondary) but Kharcha treats as explicitly separate so they
 * can be tuned independently. Each scheme carries its own triplet, tuned for readability on
 * that scheme's backgrounds. Both schemes clear WCAG AA 4.5:1 on their own `background` and
 * `surface` — enforced by [com.kharcha.app.ui.theme.ThemeContrastTest]. Debit, credit, and
 * accent also maintain perceptual distance in both schemes, so they never read as "the same color".
 */
data class KharchaSemanticColors(
    val debit: Color,
    val credit: Color,
    val accent: Color,
)

internal val kharchaDarkSemanticColors = KharchaSemanticColors(
    debit = KharchaDarkColors.debit,
    credit = KharchaDarkColors.credit,
    accent = KharchaDarkColors.accent,
)

internal val kharchaLightSemanticColors = KharchaSemanticColors(
    debit = KharchaLightColors.debit,
    credit = KharchaLightColors.credit,
    accent = KharchaLightColors.accent,
)

private val LocalKharchaSemanticColors = staticCompositionLocalOf { kharchaDarkSemanticColors }

/**
 * Theme-aware semantic colors: debit/credit/accent. Read these from composables —
 * `KharchaSemantics.debit`, not `KharchaDarkColors.debit` — so an amount stays legible in
 * whichever scheme [KharchaTheme] selected. The accent color is used for buttons, highlights,
 * and other interactive elements that should stand out from the debit/credit semantic colors.
 */
object KharchaSemantics {
    val debit: Color
        @Composable @ReadOnlyComposable get() = LocalKharchaSemanticColors.current.debit

    val credit: Color
        @Composable @ReadOnlyComposable get() = LocalKharchaSemanticColors.current.credit

    val accent: Color
        @Composable @ReadOnlyComposable get() = LocalKharchaSemanticColors.current.accent
}

/**
 * Dark-first Kharcha theme. The app is opened at a glance, often at night, so dark is the
 * primary experience; light mode is genuinely supported (not half-supported) for users
 * whose system setting requests it, and every neutral in both schemes stays warm-tinted —
 * see [KharchaNeutrals].
 *
 * Composables must read colors and type from `MaterialTheme.colorScheme` /
 * `MaterialTheme.typography` (and [KharchaSemantics] for debit/credit/accent), never from
 * the [KharchaDarkColors] / [KharchaLightColors] / [KharchaTypography] objects directly:
 * those hold one scheme's constants and cannot respond to the theme at all. Tasks 10 and 11
 * did exactly that, which drew Neutral95 text on a Neutral95 background in light mode.
 * [com.kharcha.app.ui.theme.PaletteUsageTest] fails the build if it happens again.
 *
 * [surfaceContainer] and [surfaceContainerHigh] implement Material 3's tonal elevation
 * system — the app uses layered tonal surfaces, never drop shadows, for depth.
 */
internal val kharchaDarkColorScheme = darkColorScheme(
    primary = kharchaDarkSemanticColors.accent,
    onPrimary = Color(KharchaNeutrals.Neutral0),
    primaryContainer = kharchaDarkSemanticColors.accent,
    onPrimaryContainer = KharchaDarkColors.background,
    inversePrimary = KharchaLightColors.accent,
    secondary = kharchaDarkSemanticColors.debit,
    onSecondary = Color(KharchaNeutrals.Neutral0),
    secondaryContainer = KharchaDarkColors.accentSoft,
    onSecondaryContainer = kharchaDarkSemanticColors.accent,
    tertiary = kharchaDarkSemanticColors.credit,
    onTertiary = Color(KharchaNeutrals.Neutral0),
    tertiaryContainer = KharchaDarkColors.creditContainer,
    onTertiaryContainer = KharchaDarkColors.onCreditContainer,
    error = kharchaDarkSemanticColors.debit,
    onError = Color(KharchaNeutrals.Neutral0),
    errorContainer = KharchaDarkColors.debitContainer,
    onErrorContainer = KharchaDarkColors.onDebitContainer,
    background = KharchaDarkColors.background,
    onBackground = KharchaDarkColors.onBackground,
    surface = KharchaDarkColors.surface,
    onSurface = KharchaDarkColors.onSurface,
    surfaceVariant = KharchaDarkColors.surfaceVariant,
    onSurfaceVariant = KharchaDarkColors.onSurfaceVariant,
    surfaceContainerLowest = KharchaDarkColors.surfaceContainerLowest,
    surfaceContainerLow = KharchaDarkColors.surfaceContainerLow,
    surfaceContainer = KharchaDarkColors.surfaceContainer,
    surfaceContainerHigh = KharchaDarkColors.surfaceContainerHigh,
    surfaceContainerHighest = KharchaDarkColors.surfaceContainerHighest,
    surfaceBright = KharchaDarkColors.surfaceBright,
    surfaceDim = KharchaDarkColors.surfaceDim,
    surfaceTint = kharchaDarkSemanticColors.accent,
    inverseSurface = KharchaDarkColors.onSurface,
    inverseOnSurface = KharchaDarkColors.surface,
    outline = KharchaDarkColors.outline,
    outlineVariant = KharchaDarkColors.outlineVariant,
    scrim = Color(0xFF000000),
)

internal val kharchaLightColorScheme = lightColorScheme(
    primary = kharchaLightSemanticColors.accent,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = kharchaLightSemanticColors.accent,
    onPrimaryContainer = KharchaLightColors.background,
    inversePrimary = KharchaDarkColors.accent,
    secondary = kharchaLightSemanticColors.debit,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = KharchaLightColors.accentSoft,
    onSecondaryContainer = kharchaLightSemanticColors.accent,
    tertiary = kharchaLightSemanticColors.credit,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = KharchaLightColors.creditContainer,
    onTertiaryContainer = KharchaLightColors.onCreditContainer,
    error = kharchaLightSemanticColors.debit,
    onError = Color(0xFFFFFFFF),
    errorContainer = KharchaLightColors.debitContainer,
    onErrorContainer = KharchaLightColors.onDebitContainer,
    background = KharchaLightColors.background,
    onBackground = KharchaLightColors.onBackground,
    surface = KharchaLightColors.surface,
    onSurface = KharchaLightColors.onSurface,
    surfaceVariant = KharchaLightColors.surfaceVariant,
    onSurfaceVariant = KharchaLightColors.onSurfaceVariant,
    surfaceContainerLowest = KharchaLightColors.surfaceContainerLowest,
    surfaceContainerLow = KharchaLightColors.surfaceContainerLow,
    surfaceContainer = KharchaLightColors.surfaceContainer,
    surfaceContainerHigh = KharchaLightColors.surfaceContainerHigh,
    surfaceContainerHighest = KharchaLightColors.surfaceContainerHighest,
    surfaceBright = KharchaLightColors.surfaceBright,
    surfaceDim = KharchaLightColors.surfaceDim,
    surfaceTint = kharchaLightSemanticColors.accent,
    inverseSurface = KharchaLightColors.onSurface,
    inverseOnSurface = KharchaLightColors.surface,
    outline = KharchaLightColors.outline,
    outlineVariant = KharchaLightColors.outlineVariant,
    scrim = Color(0xFF000000),
)

/**
 * Whether the active scheme is the dark one. Read this — not
 * `isSystemInDarkTheme()` — anywhere a composable has to pick between two
 * pre-computed constants (a category hue, say). The system setting and the
 * active scheme are different things the moment the user overrides the theme
 * from the app bar, and `isSystemInDarkTheme()` would still report the system's
 * answer.
 */
val LocalKharchaIsDark = staticCompositionLocalOf { true }

/**
 * The app's theme control surface, published so any screen can offer the
 * light/dark toggle without threading a callback down through four layers of
 * composable. [mode] is the user's stored preference (which may be
 * [ThemeMode.SYSTEM]); [isDark] is what that preference actually resolved to
 * against the current system setting.
 */
@Immutable
data class KharchaThemeController(
    val mode: ThemeMode,
    val isDark: Boolean,
    val setMode: (ThemeMode) -> Unit,
) {
    /**
     * Flips to the opposite of what is on screen right now. Deliberately lands
     * on an explicit LIGHT or DARK rather than cycling back through SYSTEM:
     * a user who taps the toggle is asking for a specific appearance, and
     * three-state cycling from a one-glyph control is a guessing game.
     */
    fun toggle() = setMode(if (isDark) ThemeMode.LIGHT else ThemeMode.DARK)
}

val LocalKharchaThemeController = staticCompositionLocalOf {
    KharchaThemeController(mode = ThemeMode.SYSTEM, isDark = true, setMode = {})
}

/** Resolves a stored [ThemeMode] against the current system setting. */
@Composable
@ReadOnlyComposable
fun ThemeMode.resolveIsDark(): Boolean = when (this) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun KharchaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeController: KharchaThemeController = KharchaThemeController(
        mode = if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT,
        isDark = darkTheme,
        setMode = {},
    ),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) kharchaDarkColorScheme else kharchaLightColorScheme
    val semanticColors = if (darkTheme) kharchaDarkSemanticColors else kharchaLightSemanticColors

    CompositionLocalProvider(
        LocalKharchaSemanticColors provides semanticColors,
        LocalKharchaIsDark provides darkTheme,
        LocalKharchaThemeController provides themeController,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KharchaTypography,
            shapes = KharchaShapes,
            content = content,
        )
    }
}
