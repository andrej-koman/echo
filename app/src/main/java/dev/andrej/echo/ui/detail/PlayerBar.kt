package dev.andrej.echo.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.IconButtonSize
import dev.andrej.echo.ui.components.IconButtonVariant
import dev.andrej.echo.ui.notes.clock
import dev.andrej.echo.ui.theme.EchoTheme

private const val SKIP_MS = 15_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerBar(player: NotePlayer, modifier: Modifier = Modifier) {
    val colors = EchoTheme.colors
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = player.durationMs.coerceAtLeast(1)
    val shownMs = dragging?.let { (it * duration).toLong() } ?: player.positionMs

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceBar)
            .drawBehind {
                drawLine(colors.borderSubtle, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
            }
            .padding(horizontal = EchoTheme.spacing.gutterScreen, vertical = EchoTheme.spacing.s3),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = clock(shownMs),
                style = EchoTheme.typography.micro,
                color = colors.textSecondary,
                modifier = Modifier.width(40.dp),
            )
            val sliderColors = SliderDefaults.colors(
                thumbColor = colors.textAccent,
                activeTrackColor = colors.textAccent,
                inactiveTrackColor = colors.controlTrack,
            )
            Slider(
                value = dragging ?: (player.positionMs.toFloat() / duration).coerceIn(0f, 1f),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { player.seekTo((it * duration).toLong()) }
                    dragging = null
                },
                colors = sliderColors,
                track = { state ->
                    SliderDefaults.Track(
                        sliderState = state,
                        colors = sliderColors,
                        drawStopIndicator = null,
                        thumbTrackGapSize = 0.dp,
                        trackInsideCornerSize = 2.dp,
                        modifier = Modifier.height(4.dp),
                    )
                },
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(EchoTheme.radii.pill)
                            .background(colors.textAccent),
                    )
                },
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "-" + clock((player.durationMs - shownMs).coerceAtLeast(0)),
                style = EchoTheme.typography.micro,
                color = colors.textTertiary,
                modifier = Modifier.width(44.dp),
            )
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EchoIconButton(
                    iconRes = R.drawable.ic_rotate_ccw,
                    contentDescription = "Back 15 seconds",
                    onClick = { player.skip(-SKIP_MS) },
                    size = IconButtonSize.Md,
                )
                EchoIconButton(
                    iconRes = if (player.playing) R.drawable.ic_pause else R.drawable.ic_play,
                    contentDescription = if (player.playing) "Pause" else "Play",
                    onClick = player::toggle,
                    variant = IconButtonVariant.Inverse,
                    size = IconButtonSize.Md,
                    modifier = Modifier.size(52.dp),
                )
                EchoIconButton(
                    iconRes = R.drawable.ic_rotate_cw,
                    contentDescription = "Forward 15 seconds",
                    onClick = { player.skip(SKIP_MS) },
                    size = IconButtonSize.Md,
                )
            }
            Text(
                text = speedLabel(player.speed),
                style = EchoTheme.typography.micro.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textSecondary,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(EchoTheme.radii.pill)
                    .background(colors.surfaceSunken)
                    .clickable(onClick = player::cycleSpeed)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

internal fun speedLabel(speed: Float): String =
    (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')) + "×"
