package com.kharcha.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Strict 4dp-based spacing scale used across the app for consistent rhythm.
 * Scales use semantic naming (xs → xxl) to communicate visual weight, not pixel values.
 */
object KharchaSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp

    // Named layout constants for common use cases.
    val screenGutter: Dp = 16.dp
    val cardPadding: Dp = 14.dp
    val minTouchTarget: Dp = 48.dp
}
