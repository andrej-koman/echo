package dev.andrej.echo.ui.record

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.speech.Availability
import dev.andrej.echo.speech.FailureReason

@Composable
fun RecordScreen(
    viewModel: RecordViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (granted) viewModel.startRecording()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LanguagePicker(
            languages = state.languages,
            selected = state.language,
            enabled = !state.isRecording,
            onSelect = viewModel::setLanguage,
        )

        state.downloadingLanguage?.let { tag ->
            val label = state.languages.firstOrNull { it.tag == tag }?.label ?: tag
            Text(
                text = "Downloading the $label speech model. This happens once, in the " +
                    "background — reopen the app in a minute and it will be ready.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        TranscriptArea(
            text = state.displayText,
            isRecording = state.isRecording,
            modifier = Modifier.weight(1f),
        )

        state.error?.let { reason ->
            ErrorCard(reason = reason, onDismiss = viewModel::dismissError)
        }

        when (state.availability) {
            is Availability.NoRecognizer -> Text(
                text = "This device has no on-device speech recognition. " +
                    "Check Settings › System › Languages & input › On-device speech recognition.",
                style = MaterialTheme.typography.bodyMedium,
            )


            Availability.Available -> RecordButton(
                isRecording = state.isRecording,
                level = state.level,
                enabled = state.canRecord,
                onClick = {
                    when {
                        state.isRecording -> viewModel.stopRecording()
                        hasPermission -> viewModel.startRecording()
                        else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
            )
        }
    }
}

@Composable
private fun LanguagePicker(
    languages: List<LanguageOption>,
    selected: LanguageOption?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag(TAG_LANGUAGE_ROW),
    ) {
        items(languages, key = { it.tag }) { language ->
            FilterChip(
                selected = language.tag == selected?.tag,
                enabled = enabled,
                onClick = { onSelect(language.tag) },
                label = { Text(language.label + if (language.installed) "" else " ↓") },
            )
        }
    }
}

@Composable
private fun TranscriptArea(
    text: String,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            when {
                text.isNotBlank() -> Text(
                    text = text,
                    modifier = Modifier.testTag(TAG_TRANSCRIPT),
                    style = MaterialTheme.typography.bodyLarge,
                )

                isRecording -> Text(
                    text = "Listening…",
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> Text(
                    text = "Tap record and start talking.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecordButton(
    isRecording: Boolean,
    level: Float,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // Android reports RMS roughly in -2..10 dB.
    val targetScale = if (isRecording) 1f + (level.coerceIn(0f, 10f) / 40f) else 1f
    val scale by animateFloatAsState(targetValue = targetScale, label = "recordPulse")

    Box(contentAlignment = Alignment.Center) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            modifier = Modifier
                .size(96.dp)
                .scale(scale)
                .testTag(TAG_RECORD_BUTTON),
        ) {
            Text(if (isRecording) "Stop" else "Record")
        }
    }
}

@Composable
private fun ErrorCard(reason: FailureReason, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = reason.message(),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(TAG_ERROR),
            )
            TextButton(onClick = onDismiss) { Text("Dismiss", color = Color.Unspecified) }
        }
    }
}

private fun FailureReason.message(): String = when (this) {
    FailureReason.MissingPermission -> "Microphone permission is needed to transcribe."
    FailureReason.LanguageUnavailable ->
        "No offline speech model for this language. Install it in system speech settings, or pick the other language."
    FailureReason.RecognizerBusy -> "Speech recognition is busy. Try again in a moment."
    FailureReason.Unknown -> "Transcription stopped unexpectedly. Try again."
}

const val TAG_LANGUAGE_ROW = "language_row"
const val TAG_RECORD_BUTTON = "record_button"
const val TAG_TRANSCRIPT = "transcript_text"
const val TAG_ERROR = "record_error"
