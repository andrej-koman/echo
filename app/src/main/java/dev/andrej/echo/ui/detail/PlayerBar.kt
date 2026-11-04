package dev.andrej.echo.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.ui.notes.clock
import dev.andrej.echo.ui.theme.Bronze400
import dev.andrej.echo.ui.theme.Bronze500
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.echoShadow
import dev.andrej.echo.ui.theme.rememberEchoHaptics

internal val PlayDiameter = 64.dp

internal val PlayRowOverlap = 16.dp
internal val RowTopPadding = 14.dp

private val BarRowRadius = 16.dp

/**
 * Chrome of a row resting on the tab bar: rounded top corners with a hairline over them only, so
 * its sides and bottom run straight into the bar.
 */
@Composable
internal fun Modifier.barRow(): Modifier {
    val colors = EchoTheme.colors
    return this
        .clip(RoundedCornerShape(topStart = BarRowRadius, topEnd = BarRowRadius))
        .background(colors.surfaceBar)
        .drawBehind {
            val stroke = 1.dp.toPx()
            val r = BarRowRadius.toPx()
            val inset = stroke / 2
            val top = Path().apply {
                moveTo(inset, r)
                arcTo(Rect(inset, inset, inset + 2 * r, inset + 2 * r), 180f, 90f, false)
                lineTo(size.width - r - inset, inset)
                arcTo(Rect(size.width - inset - 2 * r, inset, size.width - inset, inset + 2 * r), 270f, 90f, false)
            }
            drawPath(top, colors.borderSubtle, style = Stroke(stroke))
        }
}

/**
 * The seek row, sitting straight on the tab bar; the play button is the bar's middle slot
 * ([PlayButton] through `BarCenter`), its disc pokes up into this row's blank middle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProgressRow(player: NotePlayer, modifier: Modifier = Modifier) {
    val colors = EchoTheme.colors
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = player.durationMs.coerceAtLeast(1)
    val shownMs = dragging?.let { (it * duration).toLong() } ?: player.positionMs
    val sliderColors = SliderDefaults.colors(
        thumbColor = colors.textAccent,
        activeTrackColor = colors.textAccent,
        inactiveTrackColor = colors.controlTrack,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .barRow()
            .padding(start = EchoTheme.spacing.gutterScreen, end = EchoTheme.spacing.gutterScreen, top = RowTopPadding, bottom = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = clock(shownMs),
            style = EchoTheme.typography.micro,
            color = colors.textSecondary,
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
            modifier = Modifier
                .weight(1f)
                .height(20.dp),
        )
        Text(
            text = "-" + clock((player.durationMs - shownMs).coerceAtLeast(0)),
            style = EchoTheme.typography.micro,
            color = colors.textTertiary,
        )
    }
}

/** Takes the record button's place on a note: same bronze disc, play or pause glyph. */
@Composable
internal fun PlayButton(player: NotePlayer) {
    BarDisc(
        iconRes = if (player.playing) R.drawable.ic_pause else R.drawable.ic_play,
        contentDescription = if (player.playing) "Pause" else "Play",
        onClick = player::toggle,
    )
}

/** The play button's place while transcribing; null [onStop] when the stage can't be stopped. */
@Composable
internal fun StopButton(onStop: (() -> Unit)?) {
    BarDisc(
        iconRes = R.drawable.ic_square,
        contentDescription = "Stop transcribing",
        onClick = onStop,
    )
}

@Composable
private fun BarDisc(iconRes: Int, contentDescription: String, onClick: (() -> Unit)?) {
    val haptics = rememberEchoHaptics()
    Box(
        modifier = Modifier
            .size(PlayDiameter)
            .alpha(if (onClick == null) 0.4f else 1f)
            .echoShadow(EchoTheme.elevation.shadow3, cornerRadius = PlayDiameter / 2)
            .clip(EchoTheme.radii.pill)
            .background(Brush.linearGradient(listOf(Bronze400, Bronze500), Offset.Zero, Offset.Infinite))
            .clickable(
                enabled = onClick != null,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.tick()
                onClick?.invoke()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = EchoTheme.colors.recordGlyph,
            modifier = Modifier.size(26.dp),
        )
    }
}
