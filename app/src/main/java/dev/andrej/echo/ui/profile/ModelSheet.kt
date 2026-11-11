package dev.andrej.echo.ui.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.ai.ModelState
import dev.andrej.echo.ui.components.BadgeTone
import dev.andrej.echo.ui.components.ButtonVariant
import dev.andrej.echo.ui.components.EchoBadge
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.EchoSwitch
import dev.andrej.echo.ui.theme.EchoTheme

/** One downloadable model, Whisper or LLM: status, progress, and the single action that fits it. */
@Composable
internal fun ColumnScope.ModelDetails(
    kind: String,
    title: String,
    description: String?,
    publishedBytes: Long,
    state: ModelState,
    active: Boolean,
    paused: Boolean,
    waitingForWifi: Boolean,
    wifiOnly: Boolean,
    onWifiOnlyChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onUse: () -> Unit,
    onDelete: () -> Unit,
    footnote: String? = null,
) {
    val colors = EchoTheme.colors
    val size = formatBytes(if (state is ModelState.Ready) state.file.length() else publishedBytes)

    Row(verticalAlignment = Alignment.CenterVertically) {
        EchoIconButton(iconRes = R.drawable.ic_chevron_left, contentDescription = "All models", onClick = onBack)
        Text(kind.uppercase(), style = EchoTheme.typography.microCaps, color = colors.textTertiary)
    }

    Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s2)) {
        Text(title, style = EchoTheme.typography.heading, color = colors.textPrimary)
        description?.let { Text(it, style = EchoTheme.typography.bodySm, color = colors.textSecondary) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(EchoTheme.radii.lg)
            .background(colors.surfaceSunken)
            .padding(EchoTheme.spacing.s6),
        verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (label, tone) = when (state) {
                ModelState.Absent -> (if (paused) "Paused" else "Not downloaded") to BadgeTone.Neutral
                is ModelState.Downloading -> "Downloading" to BadgeTone.Info
                is ModelState.Ready -> (if (active) "In use" else "On this phone") to
                    (if (active) BadgeTone.Success else BadgeTone.Neutral)
                is ModelState.Failed -> "Download failed" to BadgeTone.Danger
            }
            EchoBadge(text = label, tone = tone)
            Box(Modifier.weight(1f))
            Text(size, style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.Medium), color = colors.textSecondary)
        }
        when {
            state is ModelState.Downloading -> {
                DownloadBar(state.fraction)
                Row {
                    Text(
                        "${formatBytes(state.bytesDownloaded)} of ${formatBytes(state.totalBytes)}",
                        style = EchoTheme.typography.caption,
                        color = colors.textTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${(state.fraction * 100).toInt()}%", style = EchoTheme.typography.caption, color = colors.textSecondary)
                }
            }
            waitingForWifi && wifiOnly -> Text(
                "Not on Wi-Fi. Connect, or turn off Wi-Fi only to use mobile data.",
                style = EchoTheme.typography.caption,
                color = colors.textSecondary,
            )
            state is ModelState.Failed -> Text(
                "Check your connection and try again. Downloaded parts are kept.",
                style = EchoTheme.typography.caption,
                color = colors.textSecondary,
            )
            state is ModelState.Ready -> Text(
                "Runs offline. Delete it any time to free the space.",
                style = EchoTheme.typography.caption,
                color = colors.textTertiary,
            )
        }
    }

    if (state !is ModelState.Ready) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = EchoTheme.spacing.s6)) {
                Text("Wi-Fi only", style = EchoTheme.typography.body, color = colors.textPrimary)
                Text("Keep large downloads off mobile data", style = EchoTheme.typography.caption, color = colors.textTertiary)
            }
            EchoSwitch(checked = wifiOnly, onCheckedChange = onWifiOnlyChange)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3)) {
        when (state) {
            ModelState.Absent -> EchoButton(text = "Download · ${formatBytes(publishedBytes)}", onClick = onDownload, fullWidth = true)
            is ModelState.Failed -> EchoButton(text = "Try again", onClick = onDownload, fullWidth = true)
            is ModelState.Downloading -> EchoButton(
                text = "Cancel download",
                onClick = onCancel,
                variant = ButtonVariant.Secondary,
                fullWidth = true,
            )
            is ModelState.Ready -> {
                if (!active) EchoButton(text = "Use this model", onClick = onUse, fullWidth = true)
                EchoButton(
                    text = "Delete from phone",
                    onClick = onDelete,
                    variant = ButtonVariant.Ghost,
                    textColor = colors.textDanger,
                    fullWidth = true,
                )
            }
        }
    }

    footnote?.let { Text(it, style = EchoTheme.typography.caption, color = colors.textTertiary) }
}

@Composable
private fun DownloadBar(fraction: Float) {
    val shown by animateFloatAsState(fraction, label = "downloadProgress")
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(EchoTheme.radii.pill)
            .background(EchoTheme.colors.surfaceAccentSoft),
    ) {
        Box(
            Modifier
                .fillMaxWidth(shown)
                .fillMaxHeight()
                .clip(EchoTheme.radii.pill)
                .background(EchoTheme.colors.textAccent),
        )
    }
}

/** Picker subtitle: what the model is, then whether it is on the phone. */
internal fun modelPickerLine(description: String, publishedBytes: Long, state: ModelState, paused: Boolean = false): String =
    when (state) {
        ModelState.Absent -> "$description · ${formatBytes(publishedBytes)}${if (paused) " · paused" else ""}"
        is ModelState.Downloading -> "$description · downloading ${(state.fraction * 100).toInt()}%"
        is ModelState.Ready -> "$description · on this phone"
        is ModelState.Failed -> "$description · download failed"
    }
