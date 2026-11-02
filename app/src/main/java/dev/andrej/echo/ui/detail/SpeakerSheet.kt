package dev.andrej.echo.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.data.Segment
import dev.andrej.echo.data.Speaker
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.IconButtonVariant
import dev.andrej.echo.ui.components.Sheet
import dev.andrej.echo.ui.notes.clock
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.hairline

@Composable
internal fun speakerColor(index: Int?): Color {
    val palette = EchoTheme.colors.palette
    return if (index == null) EchoTheme.colors.textTertiary else palette[index % palette.size]
}

@Composable
internal fun SpeakerDot(index: Int?, size: Int = 8) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(EchoTheme.radii.pill)
            .background(speakerColor(index)),
    )
}

/**
 * Names [speaker] (when the lines have one), and moves the lines to another speaker.
 * [moveLabel] says which lines move: "this line", "3 lines", "the selection".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpeakerSheet(
    visible: Boolean,
    speaker: Speaker?,
    lineCount: Int,
    quote: Segment?,
    suggestions: List<String>,
    others: List<Speaker>,
    moveLabel: String,
    onPlay: (Segment) -> Unit,
    onRename: (String) -> Unit,
    onMove: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = EchoTheme.colors
    var name by remember(visible, speaker) { mutableStateOf(speaker?.name.orEmpty()) }

    Sheet(visible = visible, onDismiss = onDismiss) {
        if (speaker != null) {
            Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s2)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpeakerDot(speaker.index)
                    Text(
                        text = "${speaker.label} · " + if (lineCount == 1) "1 segment" else "$lineCount segments",
                        style = EchoTheme.typography.micro,
                        color = colors.textTertiary,
                    )
                }
                Text(text = "Who is this?", style = EchoTheme.typography.heading, color = colors.textPrimary)
            }
            if (quote != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(EchoTheme.radii.md)
                        .background(colors.surfaceSunken)
                        .padding(EchoTheme.spacing.s4),
                    horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EchoIconButton(
                        iconRes = R.drawable.ic_play,
                        contentDescription = "Play",
                        onClick = { onPlay(quote) },
                        variant = IconButtonVariant.Inverse,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s2)) {
                        Text(
                            text = "“${quote.text.trim()}”",
                            style = EchoTheme.typography.bodySm,
                            color = colors.textPrimary,
                            maxLines = 3,
                        )
                        Text(
                            text = "${clock(quote.startMs)} – ${clock(quote.endMs)}",
                            style = EchoTheme.typography.micro,
                            color = colors.textTertiary,
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3)) {
                Text(text = "Name", style = EchoTheme.typography.micro, color = colors.textSecondary)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(EchoTheme.radii.md)
                            .background(colors.surfaceCard)
                            .hairline(colors.borderStrong, cornerRadius = 12.dp)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        if (name.isEmpty()) {
                            Text(text = speaker.label, style = EchoTheme.typography.body, color = colors.textTertiary)
                        }
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it },
                            singleLine = true,
                            textStyle = EchoTheme.typography.body.copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.textAccent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onRename(name) }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    EchoButton(text = "Save", onClick = { onRename(name) })
                }
                val chips = suggestions.filter { it != name.trim() }.take(8)
                if (chips.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s2),
                        verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s2),
                    ) {
                        chips.forEach { Chip(text = it, onClick = { name = it }) }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3)) {
            Text(
                text = "Move $moveLabel to",
                style = EchoTheme.typography.micro,
                color = colors.textSecondary,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s2),
                verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s2),
            ) {
                others.forEach { other -> Chip(text = other.label, dot = other.index, onClick = { onMove(other.index) }) }
                Chip(text = "New speaker", onClick = { onMove(null) })
            }
        }
    }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit, dot: Int? = null) {
    Row(
        modifier = Modifier
            .clip(EchoTheme.radii.pill)
            .background(EchoTheme.colors.surfaceAccentSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) SpeakerDot(dot)
        Text(text = text, style = EchoTheme.typography.micro, color = EchoTheme.colors.textAccent)
    }
}
