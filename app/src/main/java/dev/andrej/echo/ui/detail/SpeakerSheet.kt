package dev.andrej.echo.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.data.Speaker
import dev.andrej.echo.ui.components.ButtonSize
import dev.andrej.echo.ui.components.ButtonVariant
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.Sheet
import dev.andrej.echo.ui.theme.EchoTheme

@Composable
internal fun speakerColor(index: Int?): Color {
    val palette = EchoTheme.colors.speakers
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

/** Pick who said it — an existing speaker, or a new one — and rename speakers in place. */
@Composable
internal fun SpeakerSheet(
    visible: Boolean,
    speakers: List<Speaker>,
    current: Int?,
    onPick: (Int?) -> Unit,
    onRename: (Int, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var renaming by remember { mutableStateOf<Int?>(null) }
    var name by remember { mutableStateOf("") }

    Sheet(visible = visible, onDismiss = { renaming = null; onDismiss() }) {
        Text(text = "Who said this?", style = EchoTheme.typography.heading, color = EchoTheme.colors.textPrimary)
        Column(
            Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            speakers.forEach { speaker ->
                if (renaming == speaker.index) {
                    RenameRow(
                        index = speaker.index,
                        value = name,
                        onValueChange = { name = it },
                        onSave = {
                            onRename(speaker.index, name)
                            renaming = null
                        },
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(speaker.index) }
                            .padding(vertical = EchoTheme.spacing.s3),
                        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SpeakerDot(speaker.index, size = 12)
                        Text(
                            text = speaker.label,
                            style = EchoTheme.typography.body,
                            color = EchoTheme.colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        if (speaker.index == current) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                tint = EchoTheme.colors.textAccent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        EchoIconButton(
                            iconRes = R.drawable.ic_pen_line,
                            contentDescription = "Rename ${speaker.label}",
                            tint = EchoTheme.colors.textTertiary,
                            onClick = {
                                name = speaker.name.orEmpty()
                                renaming = speaker.index
                            },
                        )
                    }
                }
            }
        }
        EchoButton(
            text = "New speaker",
            onClick = { onPick(null) },
            variant = ButtonVariant.Secondary,
            fullWidth = true,
        )
    }
}

@Composable
private fun RenameRow(index: Int, value: String, onValueChange: (String) -> Unit, onSave: () -> Unit) {
    val colors = EchoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = EchoTheme.spacing.s3),
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpeakerDot(index, size = 12)
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(EchoTheme.radii.xs)
                .background(colors.surfaceSunken)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            if (value.isEmpty()) {
                Text(text = "Speaker ${index + 1}", style = EchoTheme.typography.body, color = colors.textTertiary)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = EchoTheme.typography.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.textAccent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        EchoButton(text = "Save", onClick = onSave, variant = ButtonVariant.Soft, size = ButtonSize.Sm)
    }
}
