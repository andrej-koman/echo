package dev.andrej.echo.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable

/** Nothing in OutLoud snaps; nothing drags. */
@Immutable
data class EchoMotion(
    val instant: Int = 90,
    val fast: Int = 140,
    val base: Int = 220,
    val slow: Int = 340,
    val sheet: Int = 460,
    val ambient: Int = 3200,

    val easeOutSoft: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f),
    val easeInOutSoft: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f),
    val easeSpring: Easing = CubicBezierEasing(0.34f, 1.42f, 0.58f, 1f),
    val easeBreathe: Easing = CubicBezierEasing(0.45f, 0f, 0.55f, 1f),

    val pressScale: Float = 0.97f,
    val pressScaleLg: Float = 0.94f,
)
