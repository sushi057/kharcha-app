package com.kharcha.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Dark-first Kharcha theme. The app is opened at a glance, often at night,
 * so dark is the primary experience; a light scheme is provided for users
 * whose system setting requests it, but every neutral in both schemes stays
 * warm-tinted — see [KharchaNeutrals].
 */
private val KharchaDarkColorScheme = darkColorScheme(
    primary = KharchaColors.credit,
    onPrimary = Color(KharchaNeutrals.Neutral0),
    secondary = KharchaColors.debit,
    background = KharchaColors.background,
    onBackground = KharchaColors.onBackground,
    surface = KharchaColors.surface,
    onSurface = KharchaColors.onSurface,
    surfaceVariant = KharchaColors.surfaceVariant,
    onSurfaceVariant = KharchaColors.onSurfaceVariant,
    outline = KharchaColors.outline,
)

private val KharchaLightColorScheme = lightColorScheme(
    primary = KharchaColors.credit,
    onPrimary = Color(KharchaNeutrals.Neutral0),
    secondary = KharchaColors.debit,
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
    val colorScheme = if (darkTheme) KharchaDarkColorScheme else KharchaLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KharchaTypography,
        content = content,
    )
}
