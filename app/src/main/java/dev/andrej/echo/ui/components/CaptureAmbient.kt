package dev.andrej.echo.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.andrej.echo.ui.theme.Bronze400
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.LocalReducedMotion
import kotlin.math.abs

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
