package dev.andrej.echo.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.LocalReducedMotion
import kotlin.math.sin

enum class MascotMood { Still, Breathing, Nodding }

enum class MascotVariant(@DrawableRes val drawable: Int) {
    Plain(R.drawable.mascot_echo),
    Tasks(R.drawable.mascot_tasks),
    Notes(R.drawable.mascot_notes),
}

@Composable
fun Mascot(
    modifier: Modifier = Modifier,
    size: Dp = 104.dp,
    mood: MascotMood = MascotMood.Still,
    variant: MascotVariant = MascotVariant.Plain,
    contentDescription: String? = null,
) {
    val reduced = LocalReducedMotion.current
    val effective = if (reduced) MascotMood.Still else mood

    val transition = rememberInfiniteTransition(label = "mascot")
    val breathe by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (effective == MascotMood.Breathing) 1.04f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = EchoTheme.motion.easeBreathe),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mascotBreathe",
    )
    val nod by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (effective == MascotMood.Nodding) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "mascotNod",
    )

    // echo-nod: -2.5deg / +2.5deg with a 4dp lift at the midpoint.
    val wave = sin(nod * 2f * Math.PI).toFloat()
    val rotation = if (effective == MascotMood.Nodding) wave * 2.5f else 0f
    val liftPx = with(LocalDensity.current) {
        if (effective == MascotMood.Nodding) (-4).dp.toPx() * kotlin.math.abs(wave) else 0f
    }

    Image(
        painter = painterResource(variant.drawable),
        contentDescription = contentDescription,
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = liftPx }
            .rotate(rotation)
            .scale(breathe),
    )
}
