package com.kharcha.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 shape system for the Kharcha app. Defines corner radius scales for
 * different semantic components. All shapes use rounded corners — no hard edges —
 * to soften the visual hierarchy and improve readability.
 */
val KharchaShapes = Shapes(
    // Small: for category icon tiles and small visual elements.
    small = RoundedCornerShape(10.dp),
    // Medium: default for most interactive surfaces (currently unused in M3 scheme).
    medium = RoundedCornerShape(12.dp),
    // Large: for cards, sheets, and major surface containers.
    large = RoundedCornerShape(16.dp),
)

/**
 * Pill-shaped corner radius for chips and pill-button components.
 * Radius of 999.dp ensures a fully rounded end regardless of container size.
 */
val KharchaPillShape = RoundedCornerShape(999.dp)
