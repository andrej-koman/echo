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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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

private val PlayDiameter = 64.dp

internal val PlayRowOverlap = 16.dp
private val RowTopPadding = 14.dp

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
            .background(colors.surfaceBar)
            .drawBehind {
                drawLine(colors.borderSubtle, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
            }
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
    val haptics = rememberEchoHaptics()
    Box(
        modifier = Modifier
            .size(PlayDiameter)
            .echoShadow(EchoTheme.elevation.shadow3, cornerRadius = PlayDiameter / 2)
            .clip(EchoTheme.radii.pill)
            .background(Brush.linearGradient(listOf(Bronze400, Bronze500), Offset.Zero, Offset.Infinite))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptics.tick()
                player.toggle()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (player.playing) R.drawable.ic_pause else R.drawable.ic_play),
            contentDescription = if (player.playing) "Pause" else "Play",
            tint = EchoTheme.colors.recordGlyph,
            modifier = Modifier.size(26.dp),
        )
    }
}
