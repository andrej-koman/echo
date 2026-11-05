package dev.andrej.echo.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.andrej.echo.ui.components.ButtonVariant
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.Sheet
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.hairline

@Composable
internal fun RenameNoteSheet(
    visible: Boolean,
    title: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = EchoTheme.colors
    var name by remember(visible) { mutableStateOf(title) }
    val save = { if (name.isNotBlank()) onSave(name.trim()) }

    Sheet(visible = visible, onDismiss = onDismiss) {
        Text(text = "Rename note", style = EchoTheme.typography.heading, color = colors.textPrimary)
        Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3)) {
            Text(text = "Title", style = EchoTheme.typography.micro, color = colors.textSecondary)
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = EchoTheme.typography.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.textAccent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(EchoTheme.radii.md)
                    .background(colors.surfaceCard)
                    .hairline(colors.borderStrong, cornerRadius = 12.dp)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .semantics { contentDescription = "Note title" },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3)) {
            EchoButton(
                text = "Cancel",
                onClick = onDismiss,
                variant = ButtonVariant.Secondary,
                modifier = Modifier.weight(1f),
            )
            EchoButton(
                text = "Save",
                onClick = save,
                enabled = name.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
