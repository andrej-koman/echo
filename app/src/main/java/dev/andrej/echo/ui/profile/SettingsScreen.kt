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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.ai.AiCardState
import dev.andrej.echo.ui.components.BadgeTone
import dev.andrej.echo.ui.components.ButtonSize
import dev.andrej.echo.ui.components.ButtonVariant
import dev.andrej.echo.ui.components.CardPadding
import dev.andrej.echo.ui.components.ConfirmSheet
import dev.andrej.echo.ui.components.EchoBadge
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.EchoSwitch
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.components.IntSliderSheet
import dev.andrej.echo.ui.components.PickerSheet
import dev.andrej.echo.ui.theme.EchoTheme

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    aiState: AiCardState,
    onBack: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onAutoAnalyzeChange: (Boolean) -> Unit,
    onWifiOnlyDownloadChange: (Boolean) -> Unit,
    onSnoozeMinutesChange: (Int) -> Unit,
    onDeleteAllData: () -> Unit,
    onDownloadModel: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteModel: () -> Unit,
    onTestModelDownloadNotification: () -> Unit = {},
    onReplayOnboarding: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pickingLanguage by remember { mutableStateOf(false) }
    var pickingSnooze by remember { mutableStateOf(false) }
    var confirmingDeleteAll by remember { mutableStateOf(false) }
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "?"
    }
    Column(modifier = modifier.fillMaxSize()) {
        EchoTopBar(
            title = "Settings",
            leading = {
                EchoIconButton(
                    iconRes = R.drawable.ic_chevron_left,
                    contentDescription = "Back",
                    onClick = onBack,
                )
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(EchoTheme.spacing.gutterScreen)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.gapSection),
        ) {
            Section(
                title = "RECORDING",
                rows = listOf(
                    {
                        DisclosureRow(
                            iconRes = R.drawable.ic_mic,
                            label = "Language",
                            value = state.languageLabel,
                            onClick = { pickingLanguage = true },
                        )
                    },
                    {
                        SwitchRow(
                            iconRes = R.drawable.ic_audio_waveform,
                            label = "Sort new notes with AI",
                            subtitle = "Pull tasks and reminders out automatically after recording",
                            checked = state.autoAnalyze,
                            onCheckedChange = onAutoAnalyzeChange,
                        )
                    },
                ),
            )

            Section(
                title = "ON-DEVICE AI",
                rows = listOf(
                    {
                        ModelCard(
                            aiState = aiState,
                            modelBytes = state.litertModelBytes,
                            wifiOnly = state.wifiOnlyDownload,
                            onWifiOnlyChange = onWifiOnlyDownloadChange,
                            onDownload = onDownloadModel,
                            onCancel = onCancelDownload,
                            onDelete = onDeleteModel,
                        )
                    },
                ),
            )

            Section(
                title = "TASKS",
                rows = listOf(
                    {
                        DisclosureRow(
                            iconRes = R.drawable.ic_clock,
                            label = "Snooze",
                            value = "${state.snoozeMinutes} min",
                            onClick = { pickingSnooze = true },
                        )
                    },
                ),
            )

            Section(
                title = "FEEL",
                rows = listOf(
                    {
                        SwitchRow(
                            iconRes = R.drawable.ic_audio_waveform,
                            label = "Haptics",
                            subtitle = "Following your phone right now",
                            checked = true,
                            onCheckedChange = {},
                            enabled = false,
                        )
                    },
                    {
                        SwitchRow(
                            iconRes = R.drawable.ic_audio_waveform,
                            label = "Reduce motion",
                            subtitle = "Following your phone right now",
                            checked = false,
                            onCheckedChange = {},
                            enabled = false,
                        )
                    },
                ),
            )

            Section(
                title = "DATA",
                rows = listOf(
                    {
                        DisclosureRow(
                            iconRes = R.drawable.ic_file_text,
                            label = "On this device",
                            value = formatBytes(state.storageBytes),
                            onClick = null,
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
        options = state.languages.map { option ->
            (if (option.installed) option.label else "${option.label} ↓") to option.tag
        },
        selected = state.languageTag.orEmpty(),
        onPick = {
            onLanguageChange(it)
            pickingLanguage = false
        },
        onDismiss = { pickingLanguage = false },
    )

    IntSliderSheet(
        visible = pickingSnooze,
        title = "Snooze",
        value = state.snoozeMinutes,
        range = 5..180,
        valueLabel = { "$it min" },
        onValueChange = onSnoozeMinutesChange,
        onDismiss = { pickingSnooze = false },
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
private fun DisclosureRow(iconRes: Int, label: String, value: String, onClick: (() -> Unit)?) {
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
        Text(
            text = label,
            style = EchoTheme.typography.body,
            color = EchoTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = EchoTheme.typography.bodySm,
            color = EchoTheme.colors.textTertiary,
        )
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
private fun ModelCard(
    aiState: AiCardState,
    modelBytes: Long,
    wifiOnly: Boolean,
    onWifiOnlyChange: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    if (aiState is AiCardState.Checking) return

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = EchoTheme.spacing.s5),
            horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(EchoTheme.colors.surfaceAccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_audio_waveform),
                    contentDescription = null,
                    tint = EchoTheme.colors.textAccent,
                    modifier = Modifier.size(16.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = MODEL_NAME, style = EchoTheme.typography.body, color = EchoTheme.colors.textPrimary)
                Text(
                    text = modelMeta(aiState, modelBytes),
                    style = EchoTheme.typography.caption,
                    color = EchoTheme.colors.textTertiary,
                )
            }
            modelBadge(aiState)?.let { (text, tone) -> EchoBadge(text = text, tone = tone) }
        }

        if (aiState is AiCardState.Downloading) {
            LinearProgressIndicator(
                progress = { aiState.fraction },
                modifier = Modifier.fillMaxWidth().padding(bottom = EchoTheme.spacing.s4),
                color = EchoTheme.colors.textAccent,
                trackColor = EchoTheme.colors.surfaceAccentSoft,
            )
        }

        HorizontalDivider(color = EchoTheme.colors.borderSubtle)
        SwitchRow(
            iconRes = R.drawable.ic_settings,
            label = "Download on Wi-Fi only",
            subtitle = "Model updates wait for Wi-Fi",
            checked = wifiOnly,
            onCheckedChange = onWifiOnlyChange,
        )

        when (aiState) {
            is AiCardState.NeedsDownload, is AiCardState.Downloading, AiCardState.Ready -> {
                HorizontalDivider(color = EchoTheme.colors.borderSubtle)
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = EchoTheme.spacing.s5)) {
                    when (aiState) {
                        is AiCardState.NeedsDownload ->
                            EchoButton(text = "Download", onClick = onDownload, variant = ButtonVariant.Secondary, fullWidth = true)

                        is AiCardState.Downloading ->
                            EchoButton(text = "Cancel", onClick = onCancel, variant = ButtonVariant.Secondary, fullWidth = true)

                        AiCardState.Ready ->
                            EchoButton(text = "Remove model", onClick = onDelete, variant = ButtonVariant.Danger, fullWidth = true)

                        else -> Unit
                    }
                }
            }

            else -> Unit
        }
    }
}

private const val MODEL_NAME = "Qwen3 1.7B"

private fun modelMeta(state: AiCardState, modelBytes: Long): String = when (state) {
    AiCardState.Ready -> "${formatBytes(modelBytes)} · on this device"
    is AiCardState.NeedsDownload -> "${formatBytes(state.bytes)} · not downloaded"
    is AiCardState.Downloading -> "Downloading… ${(state.fraction * 100).toInt()}%"
    is AiCardState.Unsupported -> state.reason
    AiCardState.Checking -> ""
}

private fun modelBadge(state: AiCardState): Pair<String, BadgeTone>? = when (state) {
    AiCardState.Ready -> "Ready" to BadgeTone.Success
    is AiCardState.NeedsDownload -> "Download" to BadgeTone.Neutral
    is AiCardState.Downloading -> "Downloading" to BadgeTone.Info
    is AiCardState.Unsupported -> "Unsupported" to BadgeTone.Warning
    AiCardState.Checking -> null
}
