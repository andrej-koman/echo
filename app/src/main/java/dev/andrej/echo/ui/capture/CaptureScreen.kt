package dev.andrej.echo.ui.capture

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.R
import dev.andrej.echo.speech.Availability
import dev.andrej.echo.speech.FailureReason
import dev.andrej.echo.ui.components.BadgeTone
import dev.andrej.echo.ui.components.ButtonSize
import dev.andrej.echo.ui.components.ButtonVariant
import dev.andrej.echo.ui.components.CardTone
import dev.andrej.echo.ui.components.EchoBadge
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.IconButtonSize
import dev.andrej.echo.ui.components.Mascot
import dev.andrej.echo.ui.components.MascotMood
import dev.andrej.echo.ui.components.RecordButton
import dev.andrej.echo.ui.components.RecordButtonSize
import dev.andrej.echo.ui.components.RecordButtonState
import dev.andrej.echo.ui.components.ProcessingSpinner
import dev.andrej.echo.ui.components.RecordRipples
import dev.andrej.echo.ui.components.RecordingTimer
import dev.andrej.echo.ui.components.SkeletonBar
import dev.andrej.echo.ui.components.ThinkingDots
import dev.andrej.echo.ui.components.Waveform
import dev.andrej.echo.ui.record.RecordUiState
import dev.andrej.echo.ui.record.RecordViewModel
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.Paper200
import dev.andrej.echo.ui.theme.rememberEchoHaptics
import kotlinx.coroutines.delay

const val TAG_RECORD_BUTTON = "record_button"
const val TAG_TRANSCRIPT = "transcript_text"
const val TAG_ERROR = "record_error"
const val TAG_LANGUAGE_ROW = "language_row"

@Composable
fun CaptureScreen(
    viewModel: RecordViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberEchoHaptics()

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    var started by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        permissionGranted = granted
        if (granted) {
            viewModel.startRecording()
            started = true
        } else {
            onDone()
        }
    }

    LaunchedEffect(permissionGranted, state.canRecord) {
        if (started) return@LaunchedEffect
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else if (state.canRecord) {
            viewModel.startRecording()
            started = true
        }
    }

    // Leave once the take has been filed away, not before.
    LaunchedEffect(started, state.isRecording, state.isProcessing) {
        if (started && !state.isRecording && !state.isProcessing && state.error == null) {
            haptics.success()
            onDone()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EchoTheme.colors.surfacePage)
            .warmWash(),
    ) {
        when {
            state.availability is Availability.NoRecognizer -> UnavailableState(onDone)
            state.isProcessing -> ProcessingState(state)
            else -> ListeningState(
                state = state,
                onStop = viewModel::stopRecording,
                onDiscard = {
                    viewModel.discard()
                    onDone()
                },
                onClose = {
                    viewModel.stopRecording()
                },
                onSelectLanguage = viewModel::setLanguage,
                onDismissError = viewModel::dismissError,
            )
        }
    }
}

/** The papaya-whip glow the board puts behind the top of every capture screen. */
private fun Modifier.warmWash(): Modifier = drawBehind {
    val radius = size.width * 0.9f
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Paper200, Paper200.copy(alpha = 0f)),
            center = Offset(size.width / 2f, size.height * 0.18f),
            radius = radius,
        ),
        size = Size(size.width, size.height * 0.64f),
    )
}

@Composable
private fun ListeningState(
    state: RecordUiState,
    onStop: () -> Unit,
    onDiscard: () -> Unit,
    onClose: () -> Unit,
    onSelectLanguage: (String) -> Unit,
    onDismissError: () -> Unit,
) {
    var elapsed by remember { mutableStateOf(0L) }
    LaunchedEffect(state.startedAtMillis, state.isRecording) {
        while (state.isRecording) {
            elapsed = (System.currentTimeMillis() - state.startedAtMillis) / 1000
            delay(250)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = EchoTheme.spacing.gutterScreen)
            .padding(top = EchoTheme.spacing.s7, bottom = EchoTheme.spacing.s10),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EchoIconButton(
                iconRes = R.drawable.ic_chevron_down,
                contentDescription = "Close",
                onClick = onClose,
                size = IconButtonSize.Sm,
            )
            EchoBadge(text = "Live", tone = BadgeTone.Live)
            EchoIconButton(
                iconRes = R.drawable.ic_trash,
                contentDescription = "Discard",
                onClick = onDiscard,
                size = IconButtonSize.Sm,
            )
        }

        Spacer(Modifier.height(36.dp))

        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center,
        ) {
            RecordRipples()
            Mascot(
                size = 104.dp,
                mood = MascotMood.Breathing,
                contentDescription = "Echo is listening",
            )
        }

        Spacer(Modifier.height(16.dp))

        RecordingTimer(seconds = elapsed, live = true)

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Echo is listening",
            style = EchoTheme.typography.bodySm,
            color = EchoTheme.colors.textSecondary,
        )

        Spacer(Modifier.height(10.dp))

        LanguagePicker(state = state, onSelect = onSelectLanguage)

        Spacer(Modifier.height(28.dp))

        Waveform(
            level = normalisedLevel(state.level),
            live = state.isRecording,
            color = EchoTheme.colors.recordLive,
        )

        if (state.displayText.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = state.displayText,
                style = EchoTheme.typography.body,
                color = EchoTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(TAG_TRANSCRIPT),
            )
        }

        if (state.error != null) {
            Spacer(Modifier.height(16.dp))
            ErrorCard(reason = state.error, onDismiss = onDismissError)
        }

        if (state.downloadingLanguage != null) {
            Spacer(Modifier.height(16.dp))
            EchoCard(tone = CardTone.Bronze) {
                Text(
                    text = "Downloading the ${state.downloadingLanguage} model. " +
                        "Recording starts once it lands.",
                    style = EchoTheme.typography.bodySm,
                    color = EchoTheme.colors.textAccent,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        RecordButton(
            state = RecordButtonState.Recording,
            onClick = onStop,
            size = RecordButtonSize.Lg,
            modifier = Modifier.testTag(TAG_RECORD_BUTTON),
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Tap to stop",
            style = EchoTheme.typography.bodySm,
            color = EchoTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun ProcessingState(state: RecordUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = EchoTheme.spacing.gutterScreen)
            .padding(top = EchoTheme.spacing.s7, bottom = EchoTheme.spacing.s10),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EchoBadge(text = "Processing", tone = BadgeTone.Info)

        Spacer(Modifier.height(36.dp))

        Column(
            modifier = Modifier.height(160.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            Mascot(
                size = 104.dp,
                mood = MascotMood.Nodding,
                contentDescription = "Echo is thinking",
            )
            ThinkingDots()
        }

        Spacer(Modifier.height(16.dp))

        RecordingTimer(
            seconds = (System.currentTimeMillis() - state.startedAtMillis) / 1000,
            live = false,
            color = EchoTheme.colors.textTertiary,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Sorting what you said",
            style = EchoTheme.typography.bodySm,
            color = EchoTheme.colors.textSecondary,
        )

        Spacer(Modifier.height(28.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SkeletonBar(widthFraction = 0.88f, delayMillis = 0)
            SkeletonBar(widthFraction = 0.96f, delayMillis = 180)
            SkeletonBar(widthFraction = 0.64f, delayMillis = 360)
        }

        Spacer(Modifier.weight(1f))

        ProcessingSpinner()

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Usually under five seconds",
            style = EchoTheme.typography.bodySm,
            color = EchoTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun UnavailableState(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(EchoTheme.spacing.s9),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Mascot(size = 96.dp)
        Text(
            text = "No speech recognition on this device",
            style = EchoTheme.typography.heading,
            color = EchoTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Echo needs an on-device recogniser to transcribe anything.",
            style = EchoTheme.typography.bodySm,
            color = EchoTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
        )
        EchoButton(text = "Back", onClick = onDone, variant = ButtonVariant.Secondary)
    }
}

@Composable
private fun LanguagePicker(
    state: RecordUiState,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = EchoTheme.colors

    Box(modifier = Modifier.testTag(TAG_LANGUAGE_ROW)) {
        Row(
            modifier = Modifier
                .clip(EchoTheme.radii.pill)
                .background(colors.statusNeutralBg)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.language?.label ?: "No language",
                style = EchoTheme.typography.caption,
                color = colors.textSecondary,
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_down),
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(14.dp),
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.languages.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (option.installed) option.label else "${option.label} ↓",
                            style = EchoTheme.typography.bodySm,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(option.tag)
                    },
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(reason: FailureReason, onDismiss: () -> Unit) {
    EchoCard(modifier = Modifier.testTag(TAG_ERROR), tone = CardTone.Paper) {
        Text(
            text = reason.message(),
            style = EchoTheme.typography.bodySm,
            color = EchoTheme.colors.textDanger,
        )
        Spacer(Modifier.height(10.dp))
        EchoButton(
            text = "Dismiss",
            onClick = onDismiss,
            variant = ButtonVariant.Ghost,
            size = ButtonSize.Sm,
        )
    }
}

private fun FailureReason.message(): String = when (this) {
    FailureReason.MissingPermission -> "Echo needs the microphone to hear you."
    FailureReason.LanguageUnavailable -> "That language isn't available on this device."
    FailureReason.RecognizerBusy -> "The recogniser is busy. Try again in a moment."
    FailureReason.Unknown -> "Something went wrong while listening."
}

/** The engine reports -2f..10f dB; the waveform wants 0f..1f. */
private fun normalisedLevel(level: Float): Float =
    ((level - NOISE_FLOOR_DB) / (LOUD_DB - NOISE_FLOOR_DB)).coerceIn(0f, 1f)

/** Room tone on this phone reads up to ~0.9 dB, so anything under this is nothing at all. */
private const val NOISE_FLOOR_DB = 1.5f

/**
 * Not headroom for shouting: the recogniser clamps at 10.0 and ordinary speech already reaches it,
 * so a raised voice reads the same as a normal one. The waveform's height is what keeps peaks
 * from dominating.
 */
private const val LOUD_DB = 10f
