package dev.andrej.echo.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.Ink100
import dev.andrej.echo.ui.theme.LocalReducedMotion

@Composable
fun SkeletonBar(
    widthFraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
    delayMillis: Int = 0,
) {
    val reduced = LocalReducedMotion.current
    val alpha = if (reduced) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "shimmer$delayMillis")
        val value by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.45f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1600,
                    delayMillis = delayMillis,
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "shimmerAlpha$delayMillis",
        )
        value
    }

    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .alpha(alpha)
            .clip(EchoTheme.radii.pill)
            .background(Ink100),
    )
}
