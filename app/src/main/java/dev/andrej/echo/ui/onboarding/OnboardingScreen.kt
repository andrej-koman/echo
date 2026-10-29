package dev.andrej.echo.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.ui.components.ButtonSize
import dev.andrej.echo.ui.components.ButtonVariant
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.theme.EchoTheme

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onRecordFirstNote: () -> Unit,
) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val needsDownload by viewModel.needsDownload.collectAsStateWithLifecycle(initialValue = false)
    val context = LocalContext.current
    val activity = LocalActivity.current

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onMicrophoneResult(granted)
    }

    BackHandler {
        if (!viewModel.back()) activity?.finish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EchoTheme.colors.surfacePage)
            .safeDrawingPadding()
            .padding(
                start = EchoTheme.spacing.gutterScreen,
                end = EchoTheme.spacing.gutterScreen,
                top = EchoTheme.spacing.s6,
                bottom = 34.dp,
            ),
    ) {
        when (step) {
            OnboardingStep.Welcome -> WelcomeStep(onGetStarted = viewModel::getStarted)

            OnboardingStep.VoiceToTasks -> ProgressStep(index = 1, onSkip = viewModel::skipToMicrophone) {
                VoiceToTasksStep(onContinue = viewModel::next)
            }

            OnboardingStep.Privacy -> ProgressStep(index = 2, onSkip = viewModel::skipToMicrophone) {
                PrivacyStep(onContinue = viewModel::next)
            }

            OnboardingStep.Microphone -> ProgressStep(index = 3) {
                MicrophoneStep(
                    onAllow = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            viewModel.onMicrophoneResult(true)
                        } else {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onNotNow = viewModel::finish,
                )
            }

            OnboardingStep.FirstNote -> ProgressStep(index = 4) {
                FirstNoteStep(
                    needsDownload = needsDownload,
                    onDownload = viewModel::downloadNow,
                    onRecord = {
                        viewModel.finish()
                        onRecordFirstNote()
                    },
                    onLater = viewModel::finish,
                )
            }
        }
    }
}

private const val STEP_COUNT = 4

/** The numbered steps' shared frame: progress segments, optional Skip, then the step's own column. */
@Composable
private fun ProgressStep(
    index: Int,
    onSkip: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s8),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(EchoTheme.spacing.tapMin),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s6),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
            ) {
                repeat(STEP_COUNT) { segment ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(EchoTheme.radii.pill)
                            .background(
                                if (segment < index) EchoTheme.colors.actionPrimaryBg else EchoTheme.colors.indicatorIdle,
                            ),
                    )
                }
            }
            if (onSkip != null) {
                EchoButton(text = "Skip", onClick = onSkip, variant = ButtonVariant.Ghost, size = ButtonSize.Sm)
            } else {
                Spacer(modifier = Modifier.size(width = 54.dp, height = 34.dp))
            }
        }
        content()
    }
}
