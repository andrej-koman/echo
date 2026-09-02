package dev.andrej.echo.ui.theme

import android.graphics.BlurMaskFilter
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One CSS box-shadow layer. Compose's own [androidx.compose.ui.draw.shadow] only draws black,
 * which reads grey against cornsilk — the whole point of the design system's warm shadows.
 */
@Immutable
data class EchoShadow(
    val offsetY: Dp,
    val blur: Dp,
    val spread: Dp,
    val color: Color,
)

@Immutable
data class EchoElevation(
    val shadow1: List<EchoShadow> = listOf(
        EchoShadow(1.dp, 2.dp, 0.dp, AlphaInk06),
    ),
    val shadow2: List<EchoShadow> = listOf(
        EchoShadow(2.dp, 6.dp, (-1).dp, AlphaInk10),
        EchoShadow(1.dp, 2.dp, 0.dp, AlphaInk04),
    ),
    val shadow3: List<EchoShadow> = listOf(
        EchoShadow(8.dp, 24.dp, (-6).dp, AlphaInk16),
        EchoShadow(2.dp, 6.dp, (-3).dp, AlphaInk06),
    ),
    val shadow4: List<EchoShadow> = listOf(
        EchoShadow(24.dp, 60.dp, (-18).dp, Color(0x382A2318)),
        EchoShadow(6.dp, 16.dp, (-8).dp, AlphaInk10),
    ),
    val haloBronze: EchoShadow = EchoShadow(0.dp, 0.dp, 6.dp, Color(0x2ED4A373)),
    val haloClay: EchoShadow = EchoShadow(0.dp, 0.dp, 8.dp, Color(0x24C0603F)),
    val haloSage: EchoShadow = EchoShadow(0.dp, 0.dp, 6.dp, Color(0x3394A56D)),
)

/** Draws the given shadow layers behind the content as a rounded rect. */
fun Modifier.echoShadow(
    layers: List<EchoShadow>,
    cornerRadius: Dp = 18.dp,
): Modifier = drawBehind {
    layers.forEach { layer -> drawShadowLayer(layer, cornerRadius) }
}

fun Modifier.echoShadow(
    layer: EchoShadow,
    cornerRadius: Dp = 18.dp,
): Modifier = echoShadow(listOf(layer), cornerRadius)

private fun DrawScope.drawShadowLayer(layer: EchoShadow, cornerRadius: Dp) {
    val spread = layer.spread.toPx()
    val width = size.width + spread * 2
    val height = size.height + spread * 2
    if (width <= 0f || height <= 0f) return

    val blurPx = layer.blur.toPx()
    val radius = cornerRadius.toPx() + spread

    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = layer.color.toArgb()
            // CSS blur radius is roughly twice the Gaussian sigma BlurMaskFilter expects.
            if (blurPx > 0f) maskFilter = BlurMaskFilter(blurPx / 2f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.nativeCanvas.drawRoundRect(
            -spread,
            -spread + layer.offsetY.toPx(),
            -spread + width,
            -spread + layer.offsetY.toPx() + height,
            radius,
            radius,
            paint,
        )
    }
}

/** A hairline drawn as an inset stroke, so it never affects layout. */
fun Modifier.hairline(
    color: Color,
    cornerRadius: Dp = 18.dp,
    width: Dp = 1.dp,
): Modifier = drawBehind {
    val strokeWidth = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
        size = Size(size.width - strokeWidth, size.height - strokeWidth),
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = Stroke(width = strokeWidth),
    )
}
