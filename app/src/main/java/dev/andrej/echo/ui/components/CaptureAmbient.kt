package dev.andrej.echo.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import dev.andrej.echo.ui.theme.Bronze400
import dev.andrej.echo.ui.theme.Bronze500
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.LocalReducedMotion
import dev.andrej.echo.ui.theme.echoShadow
import kotlin.math.abs

private const val RIPPLE_PERIOD_MS = 2600
private const val RIPPLE_COUNT = 3

/** The three clay rings the board expands out from behind the mascot while listening. */
@Composable
fun RecordRipples(modifier: Modifier = Modifier) {
    if (LocalReducedMotion.current) return

    val transition = rememberInfiniteTransition(label = "ripples")
    val cycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(RIPPLE_PERIOD_MS, easing = LinearEasing),
        ),
        label = "ripplePhase",
    )

    val color = EchoTheme.colors.recordLive
    val easing = EchoTheme.motion.easeOutSoft

    repeat(RIPPLE_COUNT) { index ->
        val phase = (cycle + index.toFloat() / RIPPLE_COUNT) % 1f
        val scale = 0.86f + easing.transform(phase) * (1.55f - 0.86f)
        val alpha = (0.5f * (1f - phase / 0.7f)).coerceAtLeast(0f)

        Canvas(modifier = modifier.size(118.dp).scale(scale)) {
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = size.minDimension / 2f - 1.dp.toPx(),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

/** Three bronze dots that hop in sequence while Echo is thinking. */
@Composable
fun ThinkingDots(modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "dots")
    val cycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing)),
        label = "dotPhase",
    )

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { index ->
            // echo-dot: one hop peaking at 40% of the cycle, staggered 160ms apart.
            val phase = if (reduced) 0.4f else (cycle + index * 0.133f) % 1f
            val lift = (1f - abs(phase - 0.4f) / 0.4f).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .offset(y = (-5).dp * lift)
                    .size(7.dp)
                    .clip(EchoTheme.radii.pill)
                    .background(Bronze400.copy(alpha = 0.28f + 0.72f * lift)),
            )
        }
    }
}

/** The bronze disc with its two counter-spinning arcs, shown while processing. */
@Composable
fun ProcessingSpinner(modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "spinner")

    val inner by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (reduced) 0f else 360f,
        animationSpec = infiniteRepeatable(animation = tween(760, easing = LinearEasing)),
        label = "spinnerInner",
    )
    val outer by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (reduced) 0f else 360f,
        animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing)),
        label = "spinnerOuter",
    )

    Box(modifier = modifier.size(118.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(118.dp)) {
            drawCircle(
                color = Bronze400.copy(alpha = 0.30f),
                radius = size.minDimension / 2f - 1.dp.toPx(),
                style = Stroke(width = 2.dp.toPx()),
            )
            rotate(outer) {
                drawArc(
                    color = Bronze500,
                    startAngle = -90f,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        Box(
            modifier = Modifier
                .size(96.dp)
                .echoShadow(EchoTheme.elevation.haloBronze, cornerRadius = 48.dp)
                .echoShadow(EchoTheme.elevation.shadow3, cornerRadius = 48.dp)
                .clip(EchoTheme.radii.pill)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Bronze400, Bronze500),
                        start = Offset.Zero,
                        end = Offset.Infinite,
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(28.dp)) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.42f),
                    radius = size.minDimension / 2f - 1.5f.dp.toPx(),
                    style = Stroke(width = 3.dp.toPx()),
                )
                rotate(inner) {
                    drawArc(
                        color = Color.White,
                        startAngle = -90f,
                        sweepAngle = 90f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
            }
        }
    }
}
