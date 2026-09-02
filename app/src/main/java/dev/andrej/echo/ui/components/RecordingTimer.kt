package dev.andrej.echo.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.Ink300
import dev.andrej.echo.ui.theme.LocalReducedMotion
import java.util.Locale

@Composable
fun RecordingTimer(
    seconds: Long,
    modifier: Modifier = Modifier,
    live: Boolean = true,
    style: TextStyle = EchoTheme.typography.monoTimer,
    color: Color = EchoTheme.colors.textPrimary,
    showDot: Boolean = false,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showDot) {
            LiveDot(live)
        }
        Text(
            text = formatDuration(seconds),
            style = style,
            color = color,
        )
    }
}

@Composable
private fun LiveDot(live: Boolean) {
    val reduced = LocalReducedMotion.current
    val alpha = if (!live || reduced) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "timerPulse")
        val value by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600, easing = EchoTheme.motion.easeBreathe),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "timerPulseAlpha",
        )
        value
    }

    Box(
        modifier = Modifier
            .size(8.dp)
            .alpha(alpha)
            .clip(EchoTheme.radii.pill)
            .background(if (live) EchoTheme.colors.recordLive else Ink300),
    )
}

fun formatDuration(seconds: Long): String =
    String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
