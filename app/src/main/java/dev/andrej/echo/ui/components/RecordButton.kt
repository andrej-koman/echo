package dev.andrej.echo.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.andrej.echo.ui.theme.Bronze400
import dev.andrej.echo.ui.theme.Bronze500
import dev.andrej.echo.ui.theme.Clay400
import dev.andrej.echo.ui.theme.Clay500
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.LocalReducedMotion
import dev.andrej.echo.ui.theme.echoShadow

enum class RecordButtonState { Idle, Recording }

enum class RecordButtonSize(val diameter: Dp) {
    Sm(56.dp),
    Md(72.dp),
    Lg(96.dp),
}

private const val RING_COUNT = 3
private const val RING_PERIOD_MS = 2600

@Composable
fun RecordButton(
    state: RecordButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: RecordButtonSize = RecordButtonSize.Lg,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val live = state == RecordButtonState.Recording
    val diameter = size.diameter
    val reduced = LocalReducedMotion.current

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val transition = rememberInfiniteTransition(label = "record")
    val breathe by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (live && !reduced) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = EchoTheme.motion.easeBreathe),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recordBreathe",
    )
    val press by animateFloatAsState(
        targetValue = if (pressed && enabled) EchoTheme.motion.pressScaleLg else 1f,
        label = "recordPress",
    )

    val label = contentDescription
        ?: if (live) "Stop recording" else "Start recording"

    Box(
        modifier = modifier.size(diameter * 1.55f),
        contentAlignment = Alignment.Center,
    ) {
        if (live && !reduced) {
            val cycle by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(animation = tween(RING_PERIOD_MS, easing = LinearEasing)),
                label = "recordRings",
            )
            repeat(RING_COUNT) { index ->
                ExpandingRing(
                    diameter = diameter,
                    phase = (cycle + index.toFloat() / RING_COUNT) % 1f,
                    color = EchoTheme.colors.recordLive,
                )
            }
        }

        Box(
            modifier = Modifier
                .size(diameter)
                .scale(breathe * press)
                .echoShadow(
                    if (live) EchoTheme.elevation.haloClay else EchoTheme.elevation.haloBronze,
                    cornerRadius = diameter / 2,
                )
                .echoShadow(EchoTheme.elevation.shadow3, cornerRadius = diameter / 2)
                .clip(EchoTheme.radii.pill)
                .background(
                    Brush.linearGradient(
                        colors = if (live) {
                            listOf(Clay400, Clay500)
                        } else {
                            listOf(Bronze400, Bronze500)
                        },
                        start = Offset.Zero,
                        end = Offset.Infinite,
                    ),
                )
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .semantics { this.contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            if (live) {
                StopGlyph(diameter)
            } else {
                MicGlyph(diameter)
            }
        }
    }
}

@Composable
private fun ExpandingRing(diameter: Dp, phase: Float, color: Color) {
    // echo-listen: scale .86 -> 1.55, opacity .5 -> 0 by 70% of the way through.
    val eased = EchoTheme.motion.easeOutSoft.transform(phase)
    val scale = 0.86f + eased * (1.55f - 0.86f)
    val alpha = (0.5f * (1f - phase / 0.7f)).coerceAtLeast(0f)

    Canvas(modifier = Modifier.size(diameter).scale(scale)) {
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = this.size.minDimension / 2f - 1.dp.toPx(),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

@Composable
private fun MicGlyph(diameter: Dp) {
    val glyph = diameter * 0.42f
    Canvas(modifier = Modifier.size(glyph)) {
        val unit = this.size.width / 24f
        val stroke = Stroke(
            width = 1.9f * unit,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )

        drawRoundRect(
            color = Color.White,
            topLeft = Offset(9f * unit, 2f * unit),
            size = Size(6f * unit, 11f * unit),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * unit),
            style = stroke,
        )
        drawArc(
            color = Color.White,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(5f * unit, 5f * unit),
            size = Size(14f * unit, 14f * unit),
            style = stroke,
        )
        listOf(5f, 19f).forEach { x ->
            drawLine(
                color = Color.White,
                start = Offset(x * unit, 10f * unit),
                end = Offset(x * unit, 12f * unit),
                strokeWidth = 1.9f * unit,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
        drawLine(
            color = Color.White,
            start = Offset(12f * unit, 18f * unit),
            end = Offset(12f * unit, 21f * unit),
            strokeWidth = 1.9f * unit,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

@Composable
private fun StopGlyph(diameter: Dp) {
    val side = diameter * 0.26f
    val radius = maxOf(3.dp, diameter * 0.06f)
    Box(
        modifier = Modifier
            .size(side)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(radius))
            .background(Color.White),
    )
}
