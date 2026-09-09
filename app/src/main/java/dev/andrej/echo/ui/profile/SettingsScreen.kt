package dev.andrej.echo.ui.profile

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import dev.andrej.echo.R
import dev.andrej.echo.ai.AiCardState
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
import dev.andrej.echo.ui.components.PickerSheet
import dev.andrej.echo.ui.theme.EchoTheme

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    aiState: AiCardState,
    onBack: () -> Unit,
    onAutoAnalyzeChange: (Boolean) -> Unit,
    onWifiOnlyDownloadChange: (Boolean) -> Unit,
    onReminderLeadMinutesChange: (Int) -> Unit,
    onReminderMorningHourChange: (Int) -> Unit,
    onDownloadModel: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteModel: () -> Unit,
    onDeleteAllRecordings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pickingLead by remember { mutableStateOf(false) }
    var pickingHour by remember { mutableStateOf(false) }
    var confirmingDeleteAll by remember { mutableStateOf(false) }
    val notificationsAllowed = remember { NotificationManagerCompat.from(context).areNotificationsEnabled() }
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
                .padding(EchoTheme.spacing.gutterScreen),
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.gapSection),
        ) {
            Section(title = "RECORDING") {
                DisclosureRow(
                    iconRes = R.drawable.ic_mic,
                    label = "Language",
                    value = state.languageLabel,
                    onClick = {},
                )
                SwitchRow(
                    iconRes = R.drawable.ic_audio_waveform,
                    label = "Sort new notes with AI",
                    subtitle = "Pull tasks and reminders out automatically after recording",
                    checked = state.autoAnalyze,
                    onCheckedChange = onAutoAnalyzeChange,
                )
            }

            Section(title = "ON-DEVICE AI") {
                AiCard(state = aiState, onDownload = onDownloadModel, onCancel = onCancelDownload, onDelete = onDeleteModel)
                SwitchRow(
                    iconRes = R.drawable.ic_settings,
                    label = "Download on Wi-Fi only",
                    subtitle = null,
                    checked = state.wifiOnlyDownload,
                    onCheckedChange = onWifiOnlyDownloadChange,
                )
            }

            Section(title = "REMINDERS") {
                DisclosureRow(
                    iconRes = R.drawable.ic_bell,
                    label = "Ring before timed task",
                    value = "${state.reminderLeadMinutes} min",
                    onClick = { pickingLead = true },
                )
                DisclosureRow(
                    iconRes = R.drawable.ic_clock,
                    label = "Day without time",
                    value = "%d:00 %s".format(
                        if (state.reminderMorningHour % 12 == 0) 12 else state.reminderMorningHour % 12,
                        if (state.reminderMorningHour < 12) "AM" else "PM",
                    ),
                    onClick = { pickingHour = true },
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = EchoTheme.spacing.s3),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Notifications", style = EchoTheme.typography.body, color = EchoTheme.colors.textPrimary)
                    if (notificationsAllowed) {
                        EchoBadge(text = "Allowed")
                    } else {
                        EchoButton(
                            text = "Turn on",
                            size = ButtonSize.Sm,
                            variant = ButtonVariant.Secondary,
                            onClick = {
                                context.startActivity(
                                    Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
                                )
                            },
                        )
                    }
                }
            }

            Section(title = "FEEL") {
                SwitchRow(
                    iconRes = R.drawable.ic_audio_waveform,
                    label = "Haptics",
                    subtitle = "Following your phone right now",
                    checked = true,
                    onCheckedChange = {},
                    enabled = false,
                )
                SwitchRow(
                    iconRes = R.drawable.ic_audio_waveform,
                    label = "Reduce motion",
                    subtitle = "Following your phone right now",
                    checked = false,
                    onCheckedChange = {},
                    enabled = false,
                )
            }

            Section(title = "DATA") {
                DisclosureRow(
                    iconRes = R.drawable.ic_file_text,
                    label = "On this device",
                    value = formatBytes(state.storageBytes),
                    onClick = null,
                )
                DisclosureRow(
                    iconRes = R.drawable.ic_file_text,
                    label = "Export notes and tasks",
                    value = "",
                    onClick = {},
                )
                EchoButton(
                    text = "Delete all recordings",
                    onClick = { confirmingDeleteAll = true },
                    variant = ButtonVariant.Danger,
                    fullWidth = true,
                )
            }

            Text(
                text = "ECHO $versionName",
                style = EchoTheme.typography.microCaps,
                color = EchoTheme.colors.textTertiary,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(Modifier.navigationBarsPadding()) {}
        }
    }

    PickerSheet(
        visible = pickingLead,
        title = "Ring before timed task",
        options = listOf("5 min" to 5, "10 min" to 10, "15 min" to 15, "30 min" to 30, "60 min" to 60),
        selected = state.reminderLeadMinutes,
        onPick = {
            onReminderLeadMinutesChange(it)
            pickingLead = false
        },
        onDismiss = { pickingLead = false },
    )

    PickerSheet(
        visible = pickingHour,
        title = "Day without time",
        options = listOf("7 AM" to 7, "8 AM" to 8, "9 AM" to 9, "10 AM" to 10),
        selected = state.reminderMorningHour,
        onPick = {
            onReminderMorningHourChange(it)
            pickingHour = false
        },
        onDismiss = { pickingHour = false },
    )

    ConfirmSheet(
        visible = confirmingDeleteAll,
        title = "Delete all recordings?",
        message = "This removes ${state.notesCount} notes and ${state.tasksCount} tasks from this device. " +
            "This can't be undone.",
        confirmLabel = "Delete all recordings",
        onConfirm = {
            confirmingDeleteAll = false
            onDeleteAllRecordings()
        },
        onDismiss = { confirmingDeleteAll = false },
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3)) {
        Text(
            text = title,
            style = EchoTheme.typography.microCaps,
            color = EchoTheme.colors.textTertiary,
        )
        EchoCard(modifier = Modifier.fillMaxWidth(), padding = CardPadding.Md) {
            Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s2)) {
                content()
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
            .padding(vertical = EchoTheme.spacing.s3),
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
        modifier = Modifier.fillMaxWidth().padding(vertical = EchoTheme.spacing.s3),
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
private fun AiCard(
    state: AiCardState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    if (state is AiCardState.Checking) return

    Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3)) {
        Text(
            text = when (state) {
                AiCardState.NanoReady -> "Using your phone's built-in AI"
                is AiCardState.NeedsDownload ->
                    "Pull tasks and reminders out of your recordings. " +
                        "~${state.bytes / 1_000_000}MB, Wi-Fi recommended."

                is AiCardState.Downloading -> "Downloading…"
                AiCardState.LiteRtReady -> "Ready · on this device"
                is AiCardState.Unsupported -> "This phone can't run on-device AI."
                AiCardState.Checking -> ""
            },
            style = EchoTheme.typography.bodySm,
            color = EchoTheme.colors.textPrimary,
        )

        if (state is AiCardState.Downloading) {
            LinearProgressIndicator(
                progress = { state.fraction },
                modifier = Modifier.fillMaxWidth(),
                color = EchoTheme.colors.textAccent,
                trackColor = EchoTheme.colors.surfaceAccentSoft,
            )
        }

        when (state) {
            is AiCardState.NeedsDownload ->
                EchoButton(text = "Download", onClick = onDownload, variant = ButtonVariant.Secondary, fullWidth = true)

            is AiCardState.Downloading ->
                EchoButton(text = "Cancel", onClick = onCancel, variant = ButtonVariant.Secondary, fullWidth = true)

            AiCardState.LiteRtReady ->
                EchoButton(text = "Remove", onClick = onDelete, variant = ButtonVariant.Secondary, fullWidth = true)

            else -> Unit
        }
    }
}
