package dev.andrej.echo.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import dev.andrej.echo.BuildConfig
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.ai.AiCardState
import dev.andrej.echo.ai.ModelState
import dev.andrej.echo.ai.NotesAiModel
import dev.andrej.echo.ai.NotesAiModelStatus
import dev.andrej.echo.billing.PlanPeriod
import dev.andrej.echo.ui.billing.periodOf
import dev.andrej.echo.ui.components.BadgeTone
import dev.andrej.echo.ui.components.ButtonSize
import dev.andrej.echo.ui.components.ButtonVariant
import dev.andrej.echo.ui.components.CardPadding
import dev.andrej.echo.ui.components.CardTone
import dev.andrej.echo.ui.components.ConfirmSheet
import dev.andrej.echo.ui.components.EchoBadge
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoSwitch
import dev.andrej.echo.ui.components.PickerSheet
import dev.andrej.echo.ui.components.Sheet
import dev.andrej.echo.speech.whisper.WhisperModel
import dev.andrej.echo.data.ImportDay
import dev.andrej.echo.data.ThemeMode
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.echoShadow
import java.text.DateFormat
import java.util.Date

// Placeholders until they are published; replace both before release.
private const val FEEDBACK_EMAIL = "feedback@example.com"
private const val PRIVACY_POLICY_URL = "https://example.com/echo/privacy"

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
    onIdleNotificationChange: (Boolean) -> Unit,
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
    onSaveBackup: (Uri) -> Unit = {},
    onRestoreBackup: (Uri) -> Unit = {},
    onBackupStatusSeen: () -> Unit = {},
    modifier: Modifier = Modifier,
    isPro: Boolean = false,
    activePlan: String? = null,
    renewsAt: Long? = null,
    onUpgrade: () -> Unit = {},
    onManageSubscription: () -> Unit = {},
    onRestorePurchases: () -> Unit = {},
    restoringPurchases: Boolean = false,
    billingMessage: String? = null,
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
    var managingBackup by remember { mutableStateOf(false) }
    var restoreCandidate by remember { mutableStateOf<Uri?>(null) }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(onSaveBackup)
    }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            managingBackup = false
            restoreCandidate = uri
        }
    }
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "?"
    }
    val activeNotesModel = state.notesAiModels.firstOrNull { it.active && it.state is ModelState.Ready }?.model
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = EchoTheme.spacing.gutterScreen)
            .padding(top = EchoTheme.spacing.s5)
            .navigationBarsPadding()
            .padding(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text(
            text = "Settings",
            style = EchoTheme.typography.display,
            color = EchoTheme.colors.textPrimary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        if (isPro) ProPlanCard(activePlan, renewsAt, onManageSubscription) else ProUpsellCard(onUpgrade)

        Section(
            title = "TRANSCRIPTION",
            rows = listOf(
                {
                    SettingsRow(
                        iconRes = R.drawable.ic_audio_waveform,
                        label = "Speech model",
                        subtitle = state.speechModel?.let { "${it.hint.substringBefore(",").substringBefore(" ·")} · ${it.name}" }
                            ?: "Unavailable",
                        onClick = { pickingSpeechModel = true },
                    )
                },
                {
                    SettingsRow(
                        iconRes = R.drawable.ic_languages,
                        label = "Language",
                        value = state.languageLabel,
                        onClick = { pickingLanguage = true },
                    )
                },
                {
                    SettingsRow(
                        iconRes = R.drawable.ic_book_open,
                        label = "Dictionary",
                        subtitle = "Names and words Echo should spell right",
                        onClick = onOpenDictionary,
                    )
                },
                {
                    SwitchSettingsRow(
                        iconRes = R.drawable.ic_users,
                        label = "Detect speakers",
                        subtitle = "Label who said what · 41 MB download",
                        checked = state.detectSpeakers,
                        onCheckedChange = onDetectSpeakersChange,
                    )
                },
            ),
        )

        Section(
            title = "NOTES AI",
            rows = listOf(
                {
                    SettingsRow(
                        iconRes = R.drawable.ic_sparkles,
                        label = "Notes model",
                        subtitle = activeNotesModel?.let { "${it.title} · summaries and tasks" } ?: "None · notes aren't analyzed",
                        onClick = { managingAiModel = true },
                    )
                },
                {
                    SwitchSettingsRow(
                        iconRes = R.drawable.ic_list_checks,
                        label = "Summary and tasks",
                        subtitle = "Write them after every note",
                        checked = state.autoAnalyze,
                        onCheckedChange = onAutoAnalyzeChange,
                    )
                },
            ),
        )

        Section(
            title = "RECORDINGS",
            rows = listOf(
                {
                    SettingsRow(
                        iconRes = R.drawable.ic_calendar,
                        label = "Date for imported audio",
                        subtitle = "Which calendar day an import goes on",
                        value = when (state.importDay) {
                            null -> "Ask"
                            ImportDay.Recorded -> "Recorded"
                            ImportDay.Today -> "Import day"
                        },
                        onClick = { pickingImportDay = true },
                    )
                },
                {
                    SwitchSettingsRow(
                        iconRes = R.drawable.ic_mic,
                        label = "Tap-to-record notification",
                        subtitle = "Keep a quiet record shortcut in the notification shade",
                        checked = state.idleNotification,
                        onCheckedChange = onIdleNotificationChange,
                    )
                },
            ),
        )

        TaskReminderSettings()

        Section(
            title = "APPEARANCE",
            rows = listOf(
                {
                    SettingsRow(
                        iconRes = R.drawable.ic_sun_moon,
                        label = "Theme",
                        value = themeMode.name,
                        onClick = { pickingTheme = true },
                    )
                },
            ),
        )

        Section(
            title = "STORAGE AND DATA",
            rows = listOf(
                {
                    SettingsRow(
                        iconRes = R.drawable.ic_hard_drive,
                        label = "Storage on this phone",
                        subtitle = "Models, recordings and notes",
                        value = formatBytes(state.storageBytes),
                        onClick = onOpenStorage,
                    )
                },
                {
                    SettingsRow(
                        iconRes = R.drawable.ic_archive,
                        label = "Back up notes",
                        subtitle = "Save everything to one file for a new phone",
                        onClick = { managingBackup = true },
                    )
                },
                {
                    SettingsRow(
                        iconRes = R.drawable.ic_trash,
                        label = "Delete all data",
                        end = RowEnd.None,
                        danger = true,
                        onClick = { confirmingDeleteAll = true },
                    )
                },
            ),
        )

        Section(
            title = "ABOUT",
            rows = listOf(
                {
                    SettingsRow(
                        iconRes = R.drawable.ic_rotate_ccw,
                        label = if (restoringPurchases) "Restoring purchases…" else "Restore purchases",
                        subtitle = billingMessage,
                        end = RowEnd.None,
                        onClick = { if (!restoringPurchases) onRestorePurchases() },
                    )
                },
                {
                    SettingsRow(
                        iconRes = R.drawable.ic_message_square,
                        label = "Send feedback",
                        end = RowEnd.External,
                        onClick = {
                            val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$FEEDBACK_EMAIL"))
                                .putExtra(Intent.EXTRA_SUBJECT, "Echo $versionName feedback")
                            runCatching { context.startActivity(mail) }
                        },
                    )
                },
                {
                    SettingsRow(
                        iconRes = R.drawable.ic_shield,
                        label = "Privacy policy",
                        end = RowEnd.External,
                        onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))) } },
                    )
                },
            ),
        )

        if (BuildConfig.DEBUG) {
            Section(
                title = "DEBUG",
                rows = listOf(
                    {
                        SettingsRow(
                            iconRes = R.drawable.ic_bell,
                            label = "Test: model download",
                            end = RowEnd.None,
                            onClick = onTestModelDownloadNotification,
                        )
                    },
                    {
                        SettingsRow(
                            iconRes = R.drawable.ic_refresh_cw,
                            label = "Replay onboarding",
                            end = RowEnd.None,
                            onClick = onReplayOnboarding,
                        )
                    },
                ),
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_wifi_off),
                    contentDescription = null,
                    tint = EchoTheme.colors.textTertiary,
                    modifier = Modifier.size(14.dp),
                )
                Text("Works fully offline", style = EchoTheme.typography.caption, color = EchoTheme.colors.textTertiary)
            }
            Text("Echo · version $versionName", style = EchoTheme.typography.caption, color = EchoTheme.colors.textTertiary)
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
        title = "Date for imported audio",
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
        selected = activeNotesModel,
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

    Sheet(visible = managingBackup, onDismiss = {
        managingBackup = false
        onBackupStatusSeen()
    }) {
        BackupSheet(
            status = state.backup,
            onSave = { saveBackup.launch("Echo backup ${java.time.LocalDate.now()}.zip") },
            onRestore = { pickBackup.launch(arrayOf("application/zip", "application/octet-stream")) },
        )
    }
    ConfirmSheet(
        visible = restoreCandidate != null,
        title = "Replace the notes on this phone?",
        message = "Every note, recording and task here is replaced by the backup, then Echo restarts. " +
            "Downloaded models and your settings stay.",
        confirmLabel = "Restore and restart",
        onConfirm = {
            restoreCandidate?.let(onRestoreBackup)
            restoreCandidate = null
            managingBackup = true
        },
        onDismiss = { restoreCandidate = null },
    )

    Sheet(visible = confirmingDeleteAll, onDismiss = { confirmingDeleteAll = false }) {
        DeleteAllSheet(
            onBackUpFirst = {
                confirmingDeleteAll = false
                managingBackup = true
            },
            onDelete = {
                confirmingDeleteAll = false
                onDeleteAllData()
            },
            onCancel = { confirmingDeleteAll = false },
        )
    }
}

@Composable
private fun ProUpsellCard(onUpgrade: () -> Unit) {
    val colors = EchoTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .echoShadow(EchoTheme.elevation.shadow2, cornerRadius = 20.dp)
            .clip(shape)
            .background(colors.surfaceRaised)
            .border(0.5.dp, colors.borderSubtle, shape),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 90.dp, y = (-110).dp)
                .size(260.dp, 220.dp)
                .background(Brush.radialGradient(listOf(colors.aura, Color.Transparent))),
        )
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.drawable.mascot_echo), contentDescription = null, modifier = Modifier.size(44.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Echo Pro", style = EchoTheme.typography.heading, color = colors.textPrimary)
                    Text(
                        "Transcribe every minute, still fully offline",
                        style = EchoTheme.typography.caption,
                        color = colors.textSecondary,
                    )
                }
            }
            Text(
                "Free notes stop at 2 minutes. Pro turns the whole recording into text, a summary and tasks.",
                style = EchoTheme.typography.bodySm,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 12.dp, bottom = 14.dp),
            )
            EchoButton(text = "See plans", onClick = onUpgrade, fullWidth = true)
        }
    }
}

@Composable
private fun ProPlanCard(activePlan: String?, renewsAt: Long?, onManage: () -> Unit) {
    val colors = EchoTheme.colors
    val plan = when (periodOf(activePlan)) {
        PlanPeriod.Lifetime -> "Lifetime"
        PlanPeriod.Yearly -> "Yearly plan"
        PlanPeriod.Monthly -> "Monthly plan"
        null -> "Pro is active"
    }
    val renewal = renewsAt?.let { "Renews ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))}" }
    EchoCard(modifier = Modifier.fillMaxWidth(), tone = CardTone.Raised, padding = CardPadding.None) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(painterResource(R.drawable.mascot_echo), contentDescription = null, modifier = Modifier.size(40.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("You're on Pro", style = EchoTheme.typography.body.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                    EchoBadge(text = "Active", tone = BadgeTone.Success)
                }
                Text(plan, style = EchoTheme.typography.caption, color = colors.textSecondary)
                renewal?.let { Text(it, style = EchoTheme.typography.caption, color = colors.textTertiary) }
            }
            if (periodOf(activePlan) != PlanPeriod.Lifetime) {
                Text(
                    "Manage",
                    style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textAccent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onManage)
                        .padding(horizontal = 6.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun BackupSheet(status: BackupStatus, onSave: () -> Unit, onRestore: () -> Unit) {
    val colors = EchoTheme.colors
    val working = status is BackupStatus.Working
    Text("Back up notes", style = EchoTheme.typography.heading, color = colors.textPrimary)
    Text(
        "Saves every note, recording, transcript and task to one file. Keep it somewhere safe, like Drive or a " +
            "computer, and restore it on a new phone.",
        style = EchoTheme.typography.bodySm,
        color = colors.textSecondary,
    )
    when (status) {
        is BackupStatus.Working -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LinearProgressIndicator(
                modifier = Modifier.weight(1f),
                color = colors.textAccent,
                trackColor = colors.surfaceAccentSoft,
            )
            Text(status.label, style = EchoTheme.typography.caption, color = colors.textSecondary)
        }
        is BackupStatus.Done -> Text(status.message, style = EchoTheme.typography.bodySm, color = colors.textSuccess)
        is BackupStatus.Failed -> Text(status.message, style = EchoTheme.typography.bodySm, color = colors.textDanger)
        BackupStatus.Idle -> Unit
    }
    Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3)) {
        EchoButton(text = "Save backup", onClick = { if (!working) onSave() }, size = ButtonSize.Lg, fullWidth = true)
        EchoButton(
            text = "Restore from backup",
            onClick = { if (!working) onRestore() },
            variant = ButtonVariant.Secondary,
            size = ButtonSize.Lg,
            fullWidth = true,
        )
    }
}

@Composable
private fun DeleteAllSheet(onBackUpFirst: () -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    val colors = EchoTheme.colors
    Box(
        modifier = Modifier.size(48.dp).clip(CircleShape).background(colors.statusDangerBg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_trash), contentDescription = null, tint = colors.textDanger, modifier = Modifier.size(22.dp))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Delete all data?", style = EchoTheme.typography.titleSm, color = colors.textPrimary)
        Text(
            "Every note, recording, transcript and task on this phone will be removed. This can't be undone.",
            style = EchoTheme.typography.body,
            color = colors.textSecondary,
        )
        Text(
            "Downloaded models stay, so you won't need to download them again.",
            style = EchoTheme.typography.caption,
            color = colors.textTertiary,
        )
    }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(colors.surfaceCard)
            .border(0.5.dp, colors.borderSubtle, shape)
            .clickable(onClick = onBackUpFirst)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_archive), contentDescription = null, tint = colors.textAccent, modifier = Modifier.size(16.dp))
        Text(
            "Back up notes first",
            style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.Medium),
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(16.dp))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        EchoButton(text = "Delete everything", onClick = onDelete, variant = ButtonVariant.Danger, size = ButtonSize.Lg, fullWidth = true)
        EchoButton(text = "Cancel", onClick = onCancel, variant = ButtonVariant.Secondary, size = ButtonSize.Lg, fullWidth = true)
    }
}

internal enum class RowEnd { Chevron, External, None }

@Composable
internal fun Section(
    title: String,
    rows: List<@Composable () -> Unit>,
    footer: (@Composable () -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = EchoTheme.typography.microCaps,
            color = EchoTheme.colors.textTertiary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        EchoCard(modifier = Modifier.fillMaxWidth(), tone = CardTone.Raised, padding = CardPadding.None) {
            rows.forEachIndexed { index, row ->
                row()
                if (index != rows.lastIndex) {
                    // Inset to the label, past the 16 dp padding, 20 dp icon and 14 dp gap.
                    HorizontalDivider(color = EchoTheme.colors.borderSubtle, modifier = Modifier.padding(start = 50.dp))
                }
            }
            footer?.invoke()
        }
    }
}

@Composable
internal fun SettingsRow(
    iconRes: Int,
    label: String,
    onClick: (() -> Unit)?,
    subtitle: String? = null,
    value: String? = null,
    end: RowEnd = RowEnd.Chevron,
    danger: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = EchoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (subtitle == null) 56.dp else 62.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (danger) colors.textDanger else colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = label,
                style = EchoTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                color = if (danger) colors.textDanger else colors.textPrimary,
            )
            subtitle?.let {
                Text(text = it, style = EchoTheme.typography.caption, color = colors.textTertiary)
            }
        }
        if (!value.isNullOrEmpty()) {
            Text(text = value, style = EchoTheme.typography.bodySm, color = colors.textSecondary, maxLines = 1)
        }
        trailing?.invoke()
        when (end) {
            RowEnd.Chevron -> Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
            RowEnd.External -> Icon(
                painter = painterResource(R.drawable.ic_arrow_up_right),
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
            RowEnd.None -> Unit
        }
    }
}

@Composable
internal fun SwitchSettingsRow(
    iconRes: Int,
    label: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsRow(
        iconRes = iconRes,
        label = label,
        subtitle = subtitle,
        end = RowEnd.None,
        onClick = { onCheckedChange(!checked) },
        trailing = { EchoSwitch(checked = checked, onCheckedChange = onCheckedChange) },
    )
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
