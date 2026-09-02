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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.LocalReducedMotion

enum class BadgeTone { Neutral, Info, Success, Warning, Danger, Live }

@Composable
fun EchoBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.Neutral,
) {
    val colors = EchoTheme.colors

    val background = when (tone) {
        BadgeTone.Neutral -> colors.statusNeutralBg
        BadgeTone.Info -> colors.statusInfoBg
        BadgeTone.Success -> colors.statusSuccessBg
        BadgeTone.Warning -> colors.statusWarningBg
        BadgeTone.Danger, BadgeTone.Live -> colors.statusDangerBg
    }
    val foreground = when (tone) {
        BadgeTone.Neutral -> colors.statusNeutralFg
        BadgeTone.Info -> colors.statusInfoFg
        BadgeTone.Success -> colors.statusSuccessFg
        BadgeTone.Warning -> colors.statusWarningFg
        BadgeTone.Danger, BadgeTone.Live -> colors.statusDangerFg
    }

    Row(
        modifier = modifier
            .height(24.dp)
            .clip(EchoTheme.radii.pill)
            .background(background)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tone == BadgeTone.Live) {
            PulsingDot()
        }
        Text(
            text = text,
            style = EchoTheme.typography.micro.copy(fontWeight = FontWeight.Medium),
            color = foreground,
        )
    }
}

@Composable
private fun PulsingDot() {
    val reduced = LocalReducedMotion.current
    val alpha = if (reduced) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "livePulse")
        val value by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600, easing = EchoTheme.motion.easeBreathe),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "livePulseAlpha",
        )
        value
    }

    Box(
        modifier = Modifier
            .size(7.dp)
            .alpha(alpha)
            .clip(EchoTheme.radii.pill)
            .background(EchoTheme.colors.recordLive),
    )
}
