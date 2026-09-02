package dev.andrej.echo.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.LocalReducedMotion
import kotlin.math.abs
import kotlin.math.sin

/**
 * @param level microphone loudness, 0f..1f. Scales the whole waveform, so the bars move with
 *   the voice rather than running a canned animation.
 */
@Composable
fun Waveform(
    modifier: Modifier = Modifier,
    bars: Int = 42,
    level: Float = 0f,
    live: Boolean = false,
    height: Dp = 88.dp,
    barWidth: Dp = 3.dp,
    gap: Dp = 3.dp,
    color: Color = EchoTheme.colors.waveformActive,
    idleColor: Color = EchoTheme.colors.waveformIdle,
) {
    val reduced = LocalReducedMotion.current
    val amplitudes = remember(bars) { amplitudes(bars) }

    val transition = rememberInfiniteTransition(label = "waveform")
    val cycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "waveformCycle",
    )

    val animate = live && !reduced

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val barPx = barWidth.toPx()
        val gapPx = gap.toPx()
        val totalWidth = bars * barPx + (bars - 1) * gapPx
        val startX = (size.width - totalWidth) / 2f
        val minHeight = 3.dp.toPx()

        amplitudes.forEachIndexed { index, amplitude ->
            val wobble = if (animate) {
                0.65f + 0.35f * abs(sin((cycle * 2f + index * 0.35f) * Math.PI).toFloat())
            } else {
                1f
            }
            val scaled = amplitude * wobble * (0.25f + level.coerceIn(0f, 1f) * 0.75f)
            val barHeight = maxOf(minHeight, scaled * size.height)
            val x = startX + index * (barPx + gapPx)

            drawRoundRect(
                color = if (live) color else idleColor,
                topLeft = Offset(x, (size.height - barHeight) / 2f),
                size = Size(barPx, barHeight),
                cornerRadius = CornerRadius(barPx / 2f),
            )
        }
    }
}

/** The design system's deterministic pseudo-random bar profile. */
private fun amplitudes(count: Int): List<Float> {
    var seed = 7
    return List(count) {
        seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
        0.25f + (seed % 1000) / 1000f * 0.75f
    }
}
