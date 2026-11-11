package dev.andrej.echo.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.data.ImportDay
import dev.andrej.echo.transcribe.ImportFile
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.EchoSwitch
import dev.andrej.echo.ui.components.PickerSheet
import dev.andrej.echo.ui.components.Sheet
import dev.andrej.echo.ui.profile.LanguageOption
import dev.andrej.echo.ui.profile.formatBytes
import dev.andrej.echo.ui.profile.languageLabel
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.hairline
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Asks for an imported file's title and, unless [fixedDay] says the user settled it for good,
 * which day it belongs to.
 */
@Composable
internal fun ImportSheet(
    file: ImportFile?,
    fixedDay: ImportDay?,
    languages: List<LanguageOption>,
    defaultLanguage: String,
    onImport: (file: ImportFile, createdAt: Long, title: String, always: ImportDay?, language: String) -> Unit,
    onDismiss: () -> Unit,
    now: () -> Long = System::currentTimeMillis,
) {
    Sheet(visible = file != null, onDismiss = onDismiss) {
        // The sheet animates out after the last file is gone, so it renders the last one it had.
        var shown by remember { mutableStateOf(file) }
        if (file != null) shown = file
        val current = shown ?: return@Sheet
        ImportSheetContent(current, fixedDay, languages, defaultLanguage, onImport, now)
    }
}

@Composable
private fun ImportSheetContent(
    file: ImportFile,
    fixedDay: ImportDay?,
    languages: List<LanguageOption>,
    defaultLanguage: String,
    onImport: (file: ImportFile, createdAt: Long, title: String, always: ImportDay?, language: String) -> Unit,
    now: () -> Long,
) {
    val colors = EchoTheme.colors
    val today = remember(file) { now() }
    var day by remember(file) {
        mutableStateOf(fixedDay ?: if (file.recordedAt != null) ImportDay.Recorded else ImportDay.Today)
    }
    var title by remember(file) { mutableStateOf("") }
    var always by remember(file) { mutableStateOf(false) }
    var language by remember(file) { mutableStateOf(defaultLanguage) }
    var pickingLanguage by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(EchoTheme.radii.md)
                .background(colors.surfaceCream)
                .padding(EchoTheme.spacing.s4),
            horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(EchoTheme.radii.md)
                    .background(colors.surfaceAccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_audio_waveform),
                    contentDescription = null,
                    tint = colors.textAccent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = file.name,
                    style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOfNotNull(file.durationMs?.let(::clock), file.sizeBytes?.let(::formatBytes))
                if (details.isNotEmpty()) {
                    Text(text = details.joinToString(" · "), style = EchoTheme.typography.micro, color = colors.textTertiary)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3)) {
            Text(text = "Title", style = EchoTheme.typography.micro, color = colors.textSecondary)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(EchoTheme.radii.md)
                    .background(colors.surfaceCard)
                    .hairline(colors.borderStrong, cornerRadius = 12.dp)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (title.isEmpty()) {
                    Text(
                        text = file.name.substringBeforeLast('.').ifBlank { "Leave empty and Echo names it" },
                        style = EchoTheme.typography.body,
                        color = colors.textTertiary,
                    )
                }
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    textStyle = EchoTheme.typography.body.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.textAccent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3)) {
            Text(text = "Language", style = EchoTheme.typography.micro, color = colors.textSecondary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(EchoTheme.radii.md)
                    .background(colors.surfaceCard)
                    .hairline(colors.borderStrong, cornerRadius = 12.dp)
                    .clickable { pickingLanguage = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (language.isEmpty()) "🌐 Auto-detect" else languageLabel(language),
                    style = EchoTheme.typography.body,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        if (fixedDay == null) {
            Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s2)) {
                Text(text = "Which day does it belong to?", style = EchoTheme.typography.heading, color = colors.textPrimary)
                Text(
                    text = "The note shows up on this day in your calendar.",
                    style = EchoTheme.typography.bodySm,
                    color = colors.textSecondary,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3)) {
                DayOption(
                    label = "When it was recorded",
                    hint = if (file.recordedAt != null) "From the file's details" else "The file doesn't say",
                    date = file.recordedAt?.let(::dayLabel),
                    selected = day == ImportDay.Recorded,
                    enabled = file.recordedAt != null,
                    onClick = { day = ImportDay.Recorded },
                )
                DayOption(
                    label = "Today",
                    hint = "When you import it",
                    date = dayLabel(today),
                    selected = day == ImportDay.Today,
                    onClick = { day = ImportDay.Today },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Always use this choice", style = EchoTheme.typography.bodySm, color = colors.textPrimary)
                    Text(
                        text = "Stops asking for the day. Change it in Settings.",
                        style = EchoTheme.typography.micro,
                        color = colors.textTertiary,
                    )
                }
                EchoSwitch(checked = always, onCheckedChange = { always = it })
            }
        }

        EchoButton(
            text = "Start transcribing",
            onClick = {
                val createdAt = if (day == ImportDay.Recorded) file.recordedAt ?: today else today
                onImport(file, createdAt, title, day.takeIf { always && fixedDay == null }, language)
            },
            fullWidth = true,
        )
    }

    PickerSheet(
        visible = pickingLanguage,
        title = "Language",
        options = languages.map { "${it.flag} ${it.label}" to it.code },
        selected = language,
        dividerBefore = { code -> languages.firstOrNull { it.code == code }?.dividerBefore == true },
        onPick = {
            language = it
            pickingLanguage = false
        },
        onDismiss = { pickingLanguage = false },
    )
}

@Composable
private fun DayOption(
    label: String,
    hint: String,
    date: String?,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val colors = EchoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(EchoTheme.radii.md)
            .background(if (selected) colors.surfaceAccentSoft else colors.surfaceCard)
            .hairline(if (selected) colors.borderAccent else colors.borderDefault, cornerRadius = 12.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(EchoTheme.spacing.s4),
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(1.5.dp, if (selected) colors.textAccent else colors.borderStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(colors.textAccent),
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold),
                color = if (enabled) colors.textPrimary else colors.textTertiary,
            )
            Text(text = hint, style = EchoTheme.typography.micro, color = colors.textTertiary)
        }
        if (date != null) {
            Text(text = date, style = EchoTheme.typography.micro, color = colors.textSecondary)
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())

private fun dayLabel(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dayFormat)
