package dev.andrej.echo.ui.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.transcribe.TranscriptionStatus
import dev.andrej.echo.ui.theme.EchoTheme

/**
 * Takes the seek row's place on the tab bar while Whisper works, the text filling in above it.
 * The stop disc ([StopButton]) pokes up from the bar below the progress line.
 */
@Composable
internal fun TranscribingRow(status: TranscriptionStatus, model: String, modifier: Modifier = Modifier) {
    val colors = EchoTheme.colors
    val (label, fraction) = when (status) {
        TranscriptionStatus.Queued, TranscriptionStatus.Failed -> "Waiting to transcribe" to null
        is TranscriptionStatus.DownloadingModel -> "Getting the speech model" to status.fraction
        is TranscriptionStatus.Transcribing -> "Transcribing…" to status.fraction
        is TranscriptionStatus.FindingSpeakers -> "Finding speakers…" to status.fraction
    }
    val shown by animateFloatAsState(fraction ?: 0f, label = "progress")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .barRow()
            .padding(
                start = EchoTheme.spacing.gutterScreen,
                end = EchoTheme.spacing.gutterScreen,
                top = RowTopPadding,
                // Puts the progress line level with the player's seek track.
                bottom = 8.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(EchoTheme.radii.md)
                    .background(colors.surfaceAccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_audio_waveform),
                    contentDescription = null,
                    tint = colors.textAccent,
                    modifier = Modifier.size(14.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$model · on this phone",
                    style = EchoTheme.typography.micro,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (fraction != null) {
                Text(
                    text = "${(fraction * 100).toInt()}%",
                    style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textAccent,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(EchoTheme.radii.pill)
                .background(colors.controlTrack),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(shown)
                    .fillMaxHeight()
                    .clip(EchoTheme.radii.pill)
                    .background(colors.textAccent),
            )
        }
    }
}
