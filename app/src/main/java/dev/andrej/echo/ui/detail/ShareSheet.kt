package dev.andrej.echo.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.export.ExportFormat
import dev.andrej.echo.export.canExport
import dev.andrej.echo.export.ExportOptions
import dev.andrej.echo.ui.components.CardPadding
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoSwitch
import dev.andrej.echo.ui.components.Sheet
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.hairline

/** [audioSize] null when the note has no audio file to send. [timed] false hides Subtitles. */
@Composable
internal fun ShareSheet(
    visible: Boolean,
    title: String,
    audioSize: String?,
    timed: Boolean,
    options: ExportOptions,
    onOptions: (ExportOptions) -> Unit,
    onShare: (ExportFormat) -> Unit,
    onDismiss: () -> Unit,
    isPro: Boolean = false,
    onUpgrade: () -> Unit = {},
) {
    Sheet(visible = visible, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s2)) {
            Text(text = "Share", style = EchoTheme.typography.heading, color = EchoTheme.colors.textPrimary)
            Text(
                text = title,
                style = EchoTheme.typography.micro,
                color = EchoTheme.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val tiles = listOfNotNull(
            Triple(ExportFormat.Text, R.drawable.ic_file_text, "Text" to ".txt"),
            Triple(ExportFormat.Markdown, R.drawable.ic_hash, "Markdown" to ".md"),
            if (timed) Triple(ExportFormat.Subtitles, R.drawable.ic_captions, "Subtitles" to ".srt") else null,
            audioSize?.let { Triple(ExportFormat.Audio, R.drawable.ic_audio_waveform, "Audio" to it) },
        )
        Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5)) {
            tiles.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5)) {
                    row.forEach { (format, icon, labels) ->
                        FormatTile(
                            icon = icon,
                            label = labels.first,
                            detail = labels.second,
                            locked = !format.canExport(isPro),
                            onClick = { if (format.canExport(isPro)) onShare(format) else onUpgrade() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Column(Modifier.weight(1f)) {}
                }
            }
        }
        EchoCard(modifier = Modifier.fillMaxWidth(), padding = CardPadding.None) {
            ToggleRow(
                label = "Speaker names",
                checked = options.speakerNames,
                onToggle = { onOptions(options.copy(speakerNames = !options.speakerNames)) },
            )
            HorizontalDivider(color = EchoTheme.colors.borderSubtle)
            ToggleRow(
                label = "Timestamps",
                checked = options.timestamps,
                onToggle = { onOptions(options.copy(timestamps = !options.timestamps)) },
            )
        }
    }
}

@Composable
private fun FormatTile(icon: Int, label: String, detail: String, locked: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = EchoTheme.colors
    Column(
        modifier = modifier
            .heightIn(min = 112.dp)
            .clip(EchoTheme.radii.md)
            .background(colors.surfaceCard)
            .hairline(colors.borderSubtle, cornerRadius = 12.dp)
            .clickable(onClick = onClick)
            .padding(EchoTheme.spacing.s6),
        verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = detail, style = EchoTheme.typography.micro, color = colors.textTertiary)
            if (locked) {
                Icon(painterResource(R.drawable.ic_lock), contentDescription = "Requires Pro", tint = colors.textAccent, modifier = Modifier.size(12.dp))
                Text(text = "Pro", style = EchoTheme.typography.micro, color = colors.textAccent)
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = EchoTheme.spacing.s6, vertical = EchoTheme.spacing.s6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = EchoTheme.typography.bodySm,
            color = EchoTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        EchoSwitch(checked = checked, onCheckedChange = { onToggle() })
    }
}
