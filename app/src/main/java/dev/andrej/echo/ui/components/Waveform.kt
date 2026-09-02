package dev.andrej.echo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.andrej.echo.ui.theme.EchoTheme
import kotlinx.coroutines.delay

private const val SAMPLE_MS = 70L
private const val ATTACK = 0.6f
private const val DECAY = 0.12f

/**
 * @param level microphone loudness, 0f..1f. While [live] the bars are a scrolling history of it,
 * newest on the right, so the shape is the last few seconds of actual sound.
 */
@Composable
fun Waveform(
    modifier: Modifier = Modifier,
    bars: Int = 42,
    level: Float = 0f,
    live: Boolean = false,
    height: Dp = 40.dp,
    barWidth: Dp = 3.dp,
    gap: Dp = 3.dp,
    color: Color = EchoTheme.colors.waveformActive,
    idleColor: Color = EchoTheme.colors.waveformIdle,
) {
    val currentLevel by rememberUpdatedState(level)
    val history = remember(bars) { mutableStateListOf<Float>().apply { repeat(bars) { add(0f) } } }
    val idleProfile = remember(bars) { idleProfile(bars) }

    LaunchedEffect(live, bars) {
        if (!live) {
            for (i in history.indices) history[i] = 0f
            return@LaunchedEffect
        }
        var smoothed = 0f
        while (true) {
            val target = currentLevel.coerceIn(0f, 1f)
            smoothed += (target - smoothed) * if (target > smoothed) ATTACK else DECAY
            history.removeAt(0)
            history.add(smoothed)
            delay(SAMPLE_MS)
        }
    }

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

        repeat(bars) { index ->
            val fraction = if (live) history[index] else idleProfile[index] * 0.25f
            val barHeight = maxOf(minHeight, fraction * size.height)
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

/** The design system's deterministic pseudo-random profile, used for the resting state only. */
private fun idleProfile(count: Int): List<Float> {
    var seed = 7
    return List(count) {
        seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
        0.25f + (seed % 1000) / 1000f * 0.75f
    }
}
