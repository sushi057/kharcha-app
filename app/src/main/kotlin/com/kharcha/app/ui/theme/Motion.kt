package com.kharcha.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.tween

/**
 * Motion design tokens for the Kharcha app. Defines reusable animation curves,
 * spring specifications, and interaction scales to maintain consistency and prevent
 * hand-rolled animation curves throughout the codebase.
 */

/**
 * Standard easing curve for most transitions: a slightly faster-out curve
 * that feels snappy but not jarring. Cubic Bézier (0.16, 1, 0.3, 1).
 */
val KharchaStandardEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/**
 * Standard tween duration for most animations in milliseconds.
 */
const val KharchaStandardDuration = 300

/**
 * Tween animation spec with the standard easing and duration.
 */
fun <T> standardAnimation() = tween<T>(
    durationMillis = KharchaStandardDuration,
    easing = KharchaStandardEasing,
)

/**
 * Spring animation spec for sheet entrance/exit and natural motion transitions.
 * Tuned for a bouncy but controlled feel with dampingRatio ~0.75 and stiffness ~90.
 */
fun <T> sheetSpringAnimation() = SpringSpec<T>(
    dampingRatio = 0.75f,
    stiffness = 90f,
)

/**
 * Scale factor for press interactions on buttons and tappable elements.
 * Scales to 97% of original size to provide tactile feedback on press.
 */
const val KharchaPressScale = 0.97f
