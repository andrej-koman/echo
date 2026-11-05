package dev.andrej.echo.ui.profile

import dev.andrej.echo.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.ai.AiCardState
import dev.andrej.echo.ai.ModelState
import dev.andrej.echo.ai.NotesAiModel
import dev.andrej.echo.ai.NotesAiModelStatus
import dev.andrej.echo.ui.components.BadgeTone
import dev.andrej.echo.ui.components.ButtonVariant
import dev.andrej.echo.ui.components.CardPadding
import dev.andrej.echo.ui.components.ConfirmSheet
import dev.andrej.echo.ui.components.EchoBadge
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoSwitch
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.components.PickerSheet
import dev.andrej.echo.ui.components.Sheet
import dev.andrej.echo.speech.whisper.WhisperModel
import dev.andrej.echo.data.ImportDay
import dev.andrej.echo.data.ThemeMode
import dev.andrej.echo.ui.theme.EchoTheme

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    aiState: AiCardState,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onLanguageChange: (String) -> Unit,
    onSpeechModelChange: (WhisperModel) -> Unit,
    onImportDayChange: (ImportDay?) -> Unit,
    onAutoAnalyzeChange: (Boolean) -> Unit,
    onDetectSpeakersChange: (Boolean) -> Unit,
    onWifiOnlyDownloadChange: (Boolean) -> Unit,
    onDeleteAllData: () -> Unit,
    onDownloadModel: (NotesAiModel) -> Unit,
    onCancelDownload: (String?) -> Unit,
    onDeleteModel: (String) -> Unit,
    onSelectNotesAiModel: (NotesAiModel) -> Unit,
    onTestModelDownloadNotification: () -> Unit = {},
    onReplayOnboarding: () -> Unit = {},
    onOpenDictionary: () -> Unit = {},
    onOpenStorage: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pickingLanguage by remember { mutableStateOf(false) }
    var pickingSpeechModel by remember { mutableStateOf(false) }
    var managingAiModel by remember { mutableStateOf(false) }
    var modelDetails by remember { mutableStateOf<NotesAiModel?>(null) }
    var pickingImportDay by remember { mutableStateOf(false) }
    var pickingTheme by remember { mutableStateOf(false) }
    var confirmingNoModel by remember { mutableStateOf(false) }
    var confirmingDeleteAll by remember { mutableStateOf(false) }
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "?"
    }
    Column(modifier = modifier.fillMaxSize()) {
        EchoTopBar(title = "Settings")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(EchoTheme.spacing.gutterScreen)
                .navigationBarsPadding()
                .padding(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.gapSection),
        ) {
            Section(
                title = "TRANSCRIPTION",
                rows = listOf(
                    {
                        DisclosureRow(
                            iconRes = R.drawable.ic_audio_waveform,
                            label = "Speech model",
                            subtitle = state.speechModel?.name ?: "Unavailable",
                            onClick = { pickingSpeechModel = true },
                        )
                    },

                    {
                        DisclosureRow(
                            iconRes = R.drawable.ic_mic,
                            label = "Language",
                            value = state.languageLabel,
                            onClick = { pickingLanguage = true },
                        )
                    },
                    {
                        DisclosureRow(
                            iconRes = R.drawable.ic_pen_line,
                            label = "Dictionary",
                            onClick = onOpenDictionary,
                        )
                    },
                    {
                        SwitchRow(
                            iconRes = R.drawable.ic_user,
                            label = "Detect speakers",
                            subtitle = "Downloads about 41 MB once · label who said what after transcribing",
                            checked = state.detectSpeakers,
                            onCheckedChange = onDetectSpeakersChange,
                        )
                    },
                ),
            )

            Section(
                title = "NOTES",
                rows = listOf(
                    {
                        DisclosureRow(
                            iconRes = R.drawable.ic_cpu,
                            label = "Notes model",
                            subtitle = state.notesAiModels.firstOrNull { it.active && it.state is ModelState.Ready }?.model?.title ?: "None",
                            onClick = { managingAiModel = true },
                        )
                    },

                    {
                        SwitchRow(
                            iconRes = R.drawable.ic_sparkles,
                            label = "Analyze notes automatically",
                            subtitle = "Create summaries and tasks after transcription",
                            checked = state.autoAnalyze,
                            onCheckedChange = onAutoAnalyzeChange,
                        )
                    },
                    {
                        DisclosureRow(
                            iconRes = R.drawable.ic_upload,
                            label = "Imported files' day",
                            value = importDayLabel(state.importDay),
                            onClick = { pickingImportDay = true },
                        )
                    },
                ),
            )

            Section(
                title = "APPEARANCE",
                rows = listOf(
                    {
                        DisclosureRow(
                            iconRes = R.drawable.ic_settings,
                            label = "Theme",
                            value = themeMode.name,
                            onClick = { pickingTheme = true },
                        )
                    },
                ),
            )

            Section(
                title = "STORAGE & DATA",
                rows = listOf(
                    {
                        DisclosureRow(
                            iconRes = R.drawable.ic_file_text,
                            label = "On this device",
                            value = formatBytes(state.storageBytes),
                            onClick = onOpenStorage,
                        )
                    },
                    {
                        DisclosureRow(
                            iconRes = R.drawable.ic_trash,
                            label = "Delete all data",
                            value = "",
                            onClick = { confirmingDeleteAll = true },
                        )
                    },
                ),
            )

            if (BuildConfig.DEBUG) {
                Section(
                    title = "DEBUG",
                    rows = listOf(
                        {
                            DisclosureRow(
                                iconRes = R.drawable.ic_bell,
                                label = "Test: model download",
                                value = "",
                                onClick = onTestModelDownloadNotification,
                            )
                        },
                        {
                            DisclosureRow(
                                iconRes = R.drawable.ic_refresh_cw,
                                label = "Replay onboarding",
                                value = "",
                                onClick = onReplayOnboarding,
                            )
                        },
                    ),
                )
            }

            Text(
                text = "ECHO $versionName",
                style = EchoTheme.typography.microCaps,
                color = EchoTheme.colors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    PickerSheet(
        visible = pickingLanguage,
        title = "Language",
        options = state.languages.map { "${it.flag} ${it.label}" to it.code },
        selected = state.languageCode,
        dividerBefore = { code -> state.languages.firstOrNull { it.code == code }?.dividerBefore == true },
        onPick = {
            onLanguageChange(it)
            pickingLanguage = false
        },
        onDismiss = { pickingLanguage = false },
    )
    PickerSheet(
        visible = pickingImportDay,
        title = "Imported files' day",
        options = listOf(null, ImportDay.Recorded, ImportDay.Today).map { importDayLabel(it) to it },
        selected = state.importDay,
        onPick = {
            onImportDayChange(it)
            pickingImportDay = false
        },
        onDismiss = { pickingImportDay = false },
    )

    state.speechModel?.let { current ->
        PickerSheet(
            visible = pickingSpeechModel,
            title = "Speech model",
            options = state.speechModels.map { it.name.removePrefix("Whisper ") to it },
            selected = current,
            subtitle = { it.hint },
            onPick = {
                onSpeechModelChange(it)
                pickingSpeechModel = false
            },
            onDismiss = { pickingSpeechModel = false },
        )
    }

    PickerSheet<NotesAiModel?>(
        visible = managingAiModel && modelDetails == null,
        title = "Notes model",
        options = listOf("None" to null) + state.notesAiModels.map { it.model.title to it.model },
        selected = state.notesAiModels.firstOrNull { it.active && it.state is ModelState.Ready }?.model,
        subtitle = { model -> state.notesAiModels.firstOrNull { it.model == model }?.let(::modelStatusLine) },
        onPick = { model ->
            if (model == null) {
                managingAiModel = false
                confirmingNoModel = true
            } else if (state.notesAiModels.any { it.model == model && it.state is ModelState.Ready }) {
                onSelectNotesAiModel(model)
                managingAiModel = false
            } else {
                modelDetails = model
            }
        },
        onDismiss = { managingAiModel = false },
    )
    Sheet(visible = managingAiModel && modelDetails != null, onDismiss = {
        managingAiModel = false
        modelDetails = null
    }) {
        NotesAiModelSheet(
            models = state.notesAiModels,
            activeAiState = aiState,
            details = modelDetails,
            wifiOnly = state.wifiOnlyDownload,
            onWifiOnlyChange = onWifiOnlyDownloadChange,
            onOpenDetails = { modelDetails = it },
            onBack = { modelDetails = null },
            onDownload = onDownloadModel,
            onCancel = onCancelDownload,
            onDelete = onDeleteModel,
            onSelect = { model ->
                onSelectNotesAiModel(model)
                managingAiModel = false
                modelDetails = null
            },
        )
    }
    ConfirmSheet(
        visible = confirmingNoModel,
        title = "Use no Notes model?",
        message = "This will delete the active model. Analyzing notes will be unavailable until you select and download a model.",
        confirmLabel = "Delete active model",
        onConfirm = {
            confirmingNoModel = false
            onDeleteModel("none")
        },
        onDismiss = { confirmingNoModel = false },
    )

    PickerSheet(
        visible = pickingTheme,
        title = "Theme",
        options = ThemeMode.entries.map { it.name to it },
        selected = themeMode,
        subtitle = { if (it == ThemeMode.System) "Follow your phone's setting" else null },
        onPick = {
            onThemeModeChange(it)
            pickingTheme = false
        },
        onDismiss = { pickingTheme = false },
    )

    ConfirmSheet(
        visible = confirmingDeleteAll,
        title = "Delete all data?",
        message = "Every note and task on this phone is erased. The downloaded AI model and your " +
            "settings stay.",
        confirmLabel = "Delete everything",
        onConfirm = {
            confirmingDeleteAll = false
            onDeleteAllData()
        },
        onDismiss = { confirmingDeleteAll = false },
    )
}

@Composable
private fun Section(title: String, rows: List<@Composable () -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3)) {
        Text(
            text = title,
            style = EchoTheme.typography.microCaps,
            color = EchoTheme.colors.textTertiary,
        )
        EchoCard(modifier = Modifier.fillMaxWidth(), padding = CardPadding.None) {
            Column(modifier = Modifier.padding(horizontal = EchoTheme.spacing.s5)) {
                rows.forEachIndexed { index, row ->
                    row()
                    if (index != rows.lastIndex) {
                        HorizontalDivider(color = EchoTheme.colors.borderSubtle)
                    }
                }
            }
        }
    }
}

@Composable
private fun DisclosureRow(
    iconRes: Int,
    label: String,
    value: String = "",
    subtitle: String? = null,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(vertical = EchoTheme.spacing.s5),
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = EchoTheme.colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = EchoTheme.typography.body, color = EchoTheme.colors.textPrimary)
            subtitle?.let {
                Text(text = it, style = EchoTheme.typography.caption, color = EchoTheme.colors.textTertiary)
            }
        }
        if (value.isNotEmpty()) {
            Text(text = value, style = EchoTheme.typography.bodySm, color = EchoTheme.colors.textTertiary)
        }
        if (onClick != null) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_right),
                contentDescription = null,
                tint = EchoTheme.colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SwitchRow(
    iconRes: Int,
    label: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = EchoTheme.spacing.s5),
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = EchoTheme.colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = EchoTheme.typography.body, color = EchoTheme.colors.textPrimary)
            if (subtitle != null) {
                Text(text = subtitle, style = EchoTheme.typography.caption, color = EchoTheme.colors.textTertiary)
            }
        }
        EchoSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun NotesAiModelSheet(
    models: List<NotesAiModelStatus>,
    activeAiState: AiCardState,
    details: NotesAiModel?,
    wifiOnly: Boolean,
    onWifiOnlyChange: (Boolean) -> Unit,
    onOpenDetails: (NotesAiModel) -> Unit,
    onBack: () -> Unit,
    onDownload: (NotesAiModel) -> Unit,
    onCancel: (String?) -> Unit,
    onDelete: (String) -> Unit,
    onSelect: (NotesAiModel) -> Unit,
) {
    Column {
        if (details == null) {
            Text(text = "Choose a model", style = EchoTheme.typography.bodySm, color = EchoTheme.colors.textSecondary)
            if (models.isEmpty()) {
                Text(text = "Checking model files…", style = EchoTheme.typography.caption, color = EchoTheme.colors.textTertiary)
            }
            models.forEach { status ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onOpenDetails(status.model) }
                        .padding(vertical = EchoTheme.spacing.s5),
                    horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = status.model.title, style = EchoTheme.typography.body, color = EchoTheme.colors.textPrimary)
                        Text(
                            text = modelStatusLine(status),
                            style = EchoTheme.typography.caption,
                            color = EchoTheme.colors.textTertiary,
                        )
                    }
                    if (status.active) EchoBadge(text = "Active", tone = BadgeTone.Success)
                }
                if (status != models.last()) HorizontalDivider(color = EchoTheme.colors.borderSubtle)
            }
        } else {
            val status = models.firstOrNull { it.model == details }
            EchoButton(text = "All models", onClick = onBack, variant = ButtonVariant.Ghost)
            Text(text = details.title, style = EchoTheme.typography.heading, color = EchoTheme.colors.textPrimary)
            Text(
                text = status?.let(::modelStatusLine) ?: "Checking availability…",
                style = EchoTheme.typography.bodySm,
                color = EchoTheme.colors.textSecondary,
            )
            if (status?.state is ModelState.Downloading) {
                val downloading = status.state
                LinearProgressIndicator(
                    progress = { downloading.fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = EchoTheme.colors.textAccent,
                    trackColor = EchoTheme.colors.surfaceAccentSoft,
                )
            }
            HorizontalDivider(color = EchoTheme.colors.borderSubtle)
            SwitchRow(
                iconRes = R.drawable.ic_settings,
                label = "Download on Wi-Fi only",
                subtitle = "AI model downloads wait for Wi-Fi",
                checked = wifiOnly,
                onCheckedChange = onWifiOnlyChange,
            )
            when (status?.state) {
                is ModelState.Absent, is ModelState.Failed, null -> {
                    EchoButton(
                        text = "Download",
                        onClick = { onDownload(details) },
                        variant = ButtonVariant.Secondary,
                        fullWidth = true,
                    )
                }
                is ModelState.Downloading -> {
                    EchoButton(
                        text = "Cancel",
                        onClick = { onCancel(details.id) },
                        variant = ButtonVariant.Secondary,
                        fullWidth = true,
                    )
                }
                is ModelState.Ready -> {
                    if (status.active) {
                        Text(text = "This model is active", style = EchoTheme.typography.caption, color = EchoTheme.colors.textTertiary)
                    } else {
                        EchoButton(text = "Use this model", onClick = { onSelect(details) }, fullWidth = true)
                    }
                    EchoButton(
                        text = "Remove model",
                        onClick = { onDelete(details.id) },
                        variant = ButtonVariant.Danger,
                        fullWidth = true,
                    )
                }
            }
            if (status?.active == true && activeAiState is AiCardState.Unsupported) {
                Text(text = activeAiState.reason, style = EchoTheme.typography.caption, color = EchoTheme.colors.textTertiary)
            }
        }
    }
}

private fun modelStatusLine(status: NotesAiModelStatus): String = when (val state = status.state) {
    ModelState.Absent -> "${formatBytes(status.model.spec.bytes)} · ${if (status.pending) "Download paused" else "Not downloaded"}"
    is ModelState.Downloading -> "${formatBytes(status.model.spec.bytes)} · Downloading ${(state.fraction * 100).toInt()}%"
    is ModelState.Ready -> "${formatBytes(state.file.length())} · on this device"
    is ModelState.Failed -> "${formatBytes(status.model.spec.bytes)} · Download failed"
}

private fun modelStatus(state: AiCardState): String = when (state) {
    AiCardState.Ready -> "Ready"
    is AiCardState.NeedsDownload -> "Not downloaded"
    is AiCardState.Downloading -> "Downloading ${(state.fraction * 100).toInt()}%"
    is AiCardState.Unsupported -> "Unsupported"
    AiCardState.Checking -> "Checking…"
}

private fun importDayLabel(day: ImportDay?) = when (day) {
    null -> "Ask each time"
    ImportDay.Recorded -> "When recorded"
    ImportDay.Today -> "Day of import"
}
