package dev.andrej.echo.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.R
import dev.andrej.echo.ui.components.BadgeTone
import dev.andrej.echo.ui.components.EchoBadge
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.IconButtonSize
import dev.andrej.echo.ui.components.IconButtonVariant
import dev.andrej.echo.ui.components.Mascot
import dev.andrej.echo.ui.components.MascotMood
import dev.andrej.echo.ui.components.RecordButton
import dev.andrej.echo.ui.components.RecordButtonSize
import dev.andrej.echo.ui.components.RecordButtonState
import dev.andrej.echo.ui.components.RecordRipples
import dev.andrej.echo.ui.components.RecordingTimer
import dev.andrej.echo.ui.components.Waveform
import dev.andrej.echo.ui.record.RecordUiState
import dev.andrej.echo.ui.record.RecordViewModel
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.rememberEchoHaptics

const val TAG_RECORD_BUTTON = "record_button"
const val TAG_PAUSE_BUTTON = "pause_button"

private val capturePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
} else {
    arrayOf(Manifest.permission.RECORD_AUDIO)
}

/**
 * Recording only: stopping files the take away and leaves at once. Whisper runs in the
 * background, and the note's badge shows how far it has got.
 */
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
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var started by remember { mutableStateOf(false) }
    // onDone pops the back stack; a second call would pop Home too.
    var left by remember { mutableStateOf(false) }
    val leave = {
        if (!left) {
            left = true
            onDone()
        }
    }

    // Notifications ride along: the recording controls and transcription progress live there,
    // but only the microphone is needed to record.
    val permissionLauncher = rememberLauncherForActivityResult(RequestMultiplePermissions()) { results ->
        val granted = results[Manifest.permission.RECORD_AUDIO] == true
        permissionGranted = granted
        if (granted) {
            viewModel.startRecording()
            started = true
        } else {
            leave()
        }
    }

    LaunchedEffect(permissionGranted) {
        if (started) return@LaunchedEffect
        if (!permissionGranted) {
            permissionLauncher.launch(capturePermissions)
        } else {
            viewModel.startRecording()
            started = true
        }
    }

    // Also covers a stop from the notification while this screen is open.
    LaunchedEffect(started, state.isRecording) {
        if (started && !state.isRecording && !left) {
            haptics.success()
            leave()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EchoTheme.colors.surfacePage)
            .warmWash(EchoTheme.colors.aura),
    ) {
        RecordingState(
            state = state,
            onStop = viewModel::stopRecording,
            onPause = viewModel::pause,
            onResume = viewModel::resume,
            onDiscard = {
                left = true
                viewModel.discard()
                onDone()
            },
            onClose = leave,
        )
    }
}

/** The aura the board puts behind the top of every capture screen. */
private fun Modifier.warmWash(aura: Color): Modifier = drawBehind {
    val radius = size.width * 0.9f
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(aura, aura.copy(alpha = 0f)),
            center = Offset(size.width / 2f, size.height * 0.18f),
            radius = radius,
        ),
        size = Size(size.width, size.height * 0.64f),
    )
}

@Composable
private fun RecordingState(
    state: RecordUiState,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    onClose: () -> Unit,
) {
    val live = state.isRecording && !state.isPaused

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
                contentDescription = "Keep recording in the background",
                onClick = onClose,
                size = IconButtonSize.Sm,
            )
            if (live) {
                EchoBadge(text = "Live", tone = BadgeTone.Live)
            } else {
                EchoBadge(text = "Paused", tone = BadgeTone.Neutral)
            }
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
            if (live) RecordRipples()
            Mascot(
                size = 104.dp,
                mood = if (live) MascotMood.Breathing else MascotMood.Still,
                contentDescription = "Echo is listening",
            )
        }

        Spacer(Modifier.height(16.dp))

        RecordingTimer(seconds = state.elapsedMs / 1000, live = live)

        Spacer(Modifier.height(8.dp))

        Text(
            text = state.statusText(),
            style = EchoTheme.typography.bodySm,
            color = if (state.micFailed) EchoTheme.colors.textDanger else EchoTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(28.dp))

        Waveform(
            level = state.level,
            live = live,
            color = EchoTheme.colors.recordLive,
        )

        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            EchoIconButton(
                iconRes = if (state.isPaused) R.drawable.ic_mic else R.drawable.ic_pause,
                contentDescription = if (state.isPaused) "Resume" else "Pause",
                onClick = if (state.isPaused) onResume else onPause,
                variant = IconButtonVariant.Soft,
                size = IconButtonSize.Md,
                modifier = Modifier.testTag(TAG_PAUSE_BUTTON),
            )
            Spacer(Modifier.width(28.dp))
            RecordButton(
                state = RecordButtonState.Recording,
                onClick = onStop,
                size = RecordButtonSize.Lg,
                modifier = Modifier.testTag(TAG_RECORD_BUTTON),
            )
            Spacer(Modifier.width(28.dp))
            // Balances the pause button so the stop button sits in the centre.
            Spacer(Modifier.size(IconButtonSize.Md.box))
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Tap to stop",
            style = EchoTheme.typography.bodySm,
            color = EchoTheme.colors.textTertiary,
        )
    }
}

private fun RecordUiState.statusText(): String = when {
    micFailed -> "The microphone is in use by another app. Tap resume to try again."
    interrupted -> "Paused for a call — picks up when it ends"
    isPaused -> "Paused"
    else -> "Echo is listening"
}
