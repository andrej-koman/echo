package dev.andrej.echo.ui.capture

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.IconButtonSize
import dev.andrej.echo.ui.components.RecordingTimer
import dev.andrej.echo.ui.components.Waveform
import dev.andrej.echo.ui.detail.barRow
import dev.andrej.echo.ui.record.RecordUiState
import dev.andrej.echo.ui.theme.EchoTheme

const val TAG_RECORD_BUTTON = "record_button"
const val TAG_PAUSE_BUTTON = "pause_button"

@Composable
fun RecordingRow(
    state: RecordUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val live = !state.isPaused
    Column(
        modifier = modifier.fillMaxWidth().barRow()
            .padding(horizontal = EchoTheme.spacing.gutterScreen)
            // The center record button's disc rises ~19 dp into this row; the bottom inset keeps
            // the waveform's tallest bars clear of it.
            .padding(top = 8.dp, bottom = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RecordingTimer(
                seconds = state.elapsedMs / 1000,
                live = live,
                showDot = true,
                style = EchoTheme.typography.bodySm.copy(fontFeatureSettings = "tnum"),
            )
            BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (live) {
                    Waveform(
                        bars = ((maxWidth + 3.dp) / 6.dp).toInt().coerceAtLeast(1),
                        level = state.level,
                        live = true,
                        height = 24.dp,
                        color = EchoTheme.colors.recordLive,
                    )
                } else {
                    Text(
                        text = when {
                            state.micFailed -> "Microphone in use by another app"
                            state.interrupted -> "Paused for a call"
                            else -> "Paused"
                        },
                        style = EchoTheme.typography.micro,
                        color = if (state.micFailed) EchoTheme.colors.textDanger else EchoTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            EchoIconButton(
                iconRes = if (state.isPaused) R.drawable.ic_mic else R.drawable.ic_pause,
                contentDescription = if (state.isPaused) "Resume recording" else "Pause recording",
                onClick = if (state.isPaused) onResume else onPause,
                size = IconButtonSize.Md,
                modifier = Modifier.testTag(TAG_PAUSE_BUTTON),
            )
        }
    }
}
