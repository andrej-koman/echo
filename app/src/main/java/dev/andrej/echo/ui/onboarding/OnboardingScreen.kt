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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.R
import dev.andrej.echo.ui.components.ButtonSize
import dev.andrej.echo.ui.components.ButtonVariant
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.PickerSheet
import dev.andrej.echo.ui.profile.languageLabel
import dev.andrej.echo.ui.theme.EchoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onRecordFirstNote: () -> Unit,
    onImportFirst: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle(initialValue = null)
    val status by viewModel.setupStatus.collectAsStateWithLifecycle(initialValue = SetupStatus.Idle)
    val context = LocalContext.current
    val activity = LocalActivity.current

    var pickingLanguage by remember { mutableStateOf(false) }
    var comparing by remember { mutableStateOf(false) }
    val languages by produceState(emptyList<dev.andrej.echo.ui.profile.LanguageOption>()) {
        value = withContext(Dispatchers.Default) { viewModel.languageOptions() }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (viewModel.onMicrophoneResult(granted)) onRecordFirstNote()
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
        when (state.step) {
            OnboardingStep.Welcome -> WelcomeStep(onGetStarted = viewModel::getStarted)

            OnboardingStep.HowItWorks -> StepFrame(
                index = 1,
                onBack = { viewModel.back() },
                actions = { ContinueButton(viewModel::next) },
            ) { HowItWorksStep() }

            OnboardingStep.Record -> StepFrame(
                index = 2,
                onBack = { viewModel.back() },
                onSkip = viewModel::skip,
                actions = { ContinueButton(viewModel::next) },
            ) {
                RecordStep(
                    uses = state.uses,
                    languageLabel = if (state.language.isEmpty()) "Auto-detect" else languageLabel(state.language),
                    onToggle = viewModel::toggleUse,
                    onPickLanguage = { pickingLanguage = true },
                )
            }

            OnboardingStep.Priority -> StepFrame(
                index = 3,
                onBack = { viewModel.back() },
                onSkip = viewModel::skip,
                actions = { ContinueButton(viewModel::next) },
            ) {
                PriorityStep(
                    options = viewModel.priorityOptions(),
                    selected = state.priority,
                    onSelect = viewModel::setPriority,
                )
            }

            OnboardingStep.Setup -> StepFrame(
                index = 4,
                onBack = { viewModel.back() },
                actions = {
                    SetupActions(
                        onWifi = viewModel::downloadOnWifi,
                        onMobileData = viewModel::downloadWithMobileData,
                        onLater = viewModel::setUpLater,
                        nothingToDownload = summary?.totalDownloadBytes == 0L,
                    )
                },
            ) {
                SetupStep(
                    summary = summary,
                    speech = state.speech,
                    speechChoices = viewModel.speechChoices,
                    comparing = comparing,
                    onCompare = { comparing = true },
                    onDismissCompare = { comparing = false },
                    onSelectSpeech = viewModel::selectSpeech,
                )
            }

            OnboardingStep.FirstNote -> StepFrame(
                index = 5,
                onBack = { viewModel.back() },
                actions = {
                    Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s2)) {
                        EchoButton(
                            text = "Import audio",
                            onClick = { viewModel.finish(); onImportFirst() },
                            variant = ButtonVariant.Soft,
                            fullWidth = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        EchoButton(
                            text = "Explore first",
                            onClick = viewModel::finish,
                            variant = ButtonVariant.Ghost,
                            fullWidth = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
            ) {
                FirstNoteStep(
                    status = status,
                    microphoneBlocked = state.microphoneBlocked,
                    onRecord = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            if (viewModel.onMicrophoneResult(true)) onRecordFirstNote()
                        } else {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onRetry = viewModel::retryDownload,
                )
            }
        }
    }

    PickerSheet(
        visible = pickingLanguage,
        title = "Language",
        options = languages.map { "${it.flag} ${it.label}" to it.code },
        selected = state.language,
        onPick = { viewModel.setLanguage(it); pickingLanguage = false },
        onDismiss = { pickingLanguage = false },
        dividerBefore = { code -> languages.firstOrNull { it.code == code }?.dividerBefore == true },
    )
}

@Composable
private fun ContinueButton(onClick: () -> Unit) {
    EchoButton(text = "Continue", onClick = onClick, size = ButtonSize.Lg, fullWidth = true, modifier = Modifier.fillMaxWidth())
}

private const val STEP_COUNT = 5

/**
 * The numbered steps' shared frame: back arrow, progress segments, optional Skip, a scrolling
 * body (large font scales must not push content off screen), and actions pinned below it.
 */
@Composable
private fun StepFrame(
    index: Int,
    onBack: () -> Unit,
    onSkip: (() -> Unit)? = null,
    actions: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s6),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(EchoTheme.spacing.tapMin),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
        ) {
            EchoIconButton(iconRes = R.drawable.ic_chevron_left, contentDescription = "Back", onClick = onBack)
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
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s6),
            content = content,
        )
        actions()
    }
}
