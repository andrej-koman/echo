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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.andrej.echo.ui.billing.ProArrival
import dev.andrej.echo.ui.billing.ProWelcome
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.ai.AiCardState
import dev.andrej.echo.ai.ModelState
import dev.andrej.echo.ai.NotesAiModel
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
import dev.andrej.echo.ui.theme.EchoHaptics
import dev.andrej.echo.ui.theme.echoShadow
import dev.andrej.echo.ui.theme.rememberEchoHaptics
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
    onDownloadModel: (NotesAiModel) -> Boolean,
    onCancelDownload: (String?) -> Unit,
    onDeleteModel: (String) -> Unit,
    onSelectNotesAiModel: (NotesAiModel) -> Unit,
    onDownloadSpeechModel: (WhisperModel) -> Boolean = { true },
    onCancelSpeechDownload: () -> Unit = {},
    onDeleteSpeechModel: (WhisperModel) -> Unit = {},
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
    hapticsEnabled: Boolean = true,
    onHapticsChange: (Boolean) -> Unit = {},
    reduceMotion: Boolean = false,
    onReduceMotionChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    var pickingLanguage by remember { mutableStateOf(false) }
    var pickingSpeechModel by remember { mutableStateOf(false) }
    var managingAiModel by remember { mutableStateOf(false) }
    var modelDetails by remember { mutableStateOf<NotesAiModel?>(null) }
    var speechDetails by remember { mutableStateOf<WhisperModel?>(null) }
    var waitingForWifi by remember { mutableStateOf<Any?>(null) }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
    var pickingImportDay by remember { mutableStateOf(false) }
    var previewingCelebration by remember { mutableStateOf(false) }
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
                {
                    SwitchSettingsRow(
                        iconRes = R.drawable.ic_audio_waveform,
                        label = "Haptics",
                        subtitle = "Taps, toggles and gestures you can feel",
                        checked = hapticsEnabled,
                        onCheckedChange = { on ->
                            onHapticsChange(on)
                            // The switch's own haptic was silenced while haptics were off.
                            if (on) EchoHaptics(view, context.applicationContext, enabled = true).toggle(true)
                        },
                    )
                },
                {
                    SwitchSettingsRow(
                        iconRes = R.drawable.ic_sparkles,
                        label = "Reduce motion",
                        subtitle = "Turn off decorative animation",
                        checked = reduceMotion,
                        onCheckedChange = onReduceMotionChange,
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
                    {
                        SettingsRow(
                            iconRes = R.drawable.ic_sparkles,
                            label = "Test: Pro celebration",
                            end = RowEnd.None,
                            onClick = { previewingCelebration = true },
                        )
                    },
                ),
            )
            if (previewingCelebration) {
                Dialog(
                    onDismissRequest = { previewingCelebration = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
                ) {
                    ProWelcome(
                        unlockNote = null, activePlan = activePlan, renewsAt = renewsAt,
                        onOpenNote = {}, onDone = { previewingCelebration = false },
                        arrival = ProArrival.Purchased,
                    )
                }
            }
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

    fun speechState(model: WhisperModel): ModelState =
        state.speechModelStates.getOrNull(state.speechModels.indexOf(model)) ?: ModelState.Absent

    state.speechModel?.let { current ->
        PickerSheet(
            visible = pickingSpeechModel && speechDetails == null,
            title = "Speech model",
            options = state.speechModels.map { it.name.removePrefix("Whisper ") to it },
            selected = current,
            subtitle = { modelPickerLine(it.hint.substringBefore(" ·"), it.spec.bytes, speechState(it)) },
            onPick = { speechDetails = it },
            onDismiss = { pickingSpeechModel = false },
        )
    }
    Sheet(visible = speechDetails != null && pendingDelete == null, onDismiss = {
        pickingSpeechModel = false
        speechDetails = null
    }) {
        val model = rememberLast(speechDetails) ?: return@Sheet
        val modelState = speechState(model)
        ModelDetails(
            kind = "Speech model",
            title = model.name,
            description = model.hint.substringBefore(" ·"),
            publishedBytes = model.spec.bytes,
            state = modelState,
            active = model == state.speechModel,
            paused = false,
            waitingForWifi = waitingForWifi == model,
            wifiOnly = state.wifiOnlyDownload,
            onWifiOnlyChange = onWifiOnlyDownloadChange,
            onBack = { speechDetails = null },
            onDownload = { waitingForWifi = if (onDownloadSpeechModel(model)) null else model },
            onCancel = onCancelSpeechDownload,
            onUse = {
                onSpeechModelChange(model)
                pickingSpeechModel = false
                speechDetails = null
            },
            onDelete = {
                pendingDelete = PendingDelete(
                    title = "Delete ${model.name}?",
                    message = "Frees ${formatBytes((modelState as? ModelState.Ready)?.file?.length() ?: model.spec.bytes)}. " +
                        if (model == state.speechModel) "Echo downloads it again before your next transcription." else "You can download it again any time.",
                    delete = { onDeleteSpeechModel(model) },
                )
            },
            footnote = if (model == state.speechModel && modelState !is ModelState.Ready) {
                "In use. Echo downloads it before your next transcription if you don't."
            } else {
                null
            },
        )
    }

    PickerSheet<NotesAiModel?>(
        visible = managingAiModel && modelDetails == null,
        title = "Notes model",
        options = listOf("None" to null) + state.notesAiModels.map { it.model.title to it.model },
        selected = activeNotesModel,
        subtitle = { model ->
            state.notesAiModels.firstOrNull { it.model == model }?.let {
                modelPickerLine(notesModelDescription(it.model), it.model.spec.bytes, it.state, it.pending)
            }
        },
        onPick = { model ->
            if (model == null) {
                managingAiModel = false
                confirmingNoModel = true
            } else {
                modelDetails = model
            }
        },
        onDismiss = { managingAiModel = false },
    )
    Sheet(visible = managingAiModel && modelDetails != null && pendingDelete == null, onDismiss = {
        managingAiModel = false
        modelDetails = null
    }) {
        val model = rememberLast(modelDetails) ?: return@Sheet
        val status = state.notesAiModels.firstOrNull { it.model == model }
        val modelState = status?.state ?: ModelState.Absent
        val active = status?.active == true && modelState is ModelState.Ready
        ModelDetails(
            kind = "Notes model",
            title = model.title,
            description = notesModelDescription(model),
            publishedBytes = model.spec.bytes,
            state = modelState,
            active = active,
            paused = status?.pending == true,
            waitingForWifi = waitingForWifi == model,
            wifiOnly = state.wifiOnlyDownload,
            onWifiOnlyChange = onWifiOnlyDownloadChange,
            onBack = { modelDetails = null },
            onDownload = { waitingForWifi = if (onDownloadModel(model)) null else model },
            onCancel = { onCancelDownload(model.id) },
            onUse = {
                onSelectNotesAiModel(model)
                managingAiModel = false
                modelDetails = null
            },
            onDelete = {
                pendingDelete = PendingDelete(
                    title = "Delete ${model.title}?",
                    message = "Frees ${formatBytes((modelState as? ModelState.Ready)?.file?.length() ?: model.spec.bytes)}. " +
                        if (active) {
                            "Echo switches to another downloaded notes model, or stops writing summaries until you download one."
                        } else {
                            "You can download it again any time."
                        },
                    delete = { onDeleteModel(model.id) },
                )
            },
            footnote = (aiState as? AiCardState.Unsupported)?.reason?.takeIf { active },
        )
    }
    ConfirmSheet(
        visible = pendingDelete != null,
        title = pendingDelete?.title.orEmpty(),
        message = pendingDelete?.message.orEmpty(),
        confirmLabel = "Delete",
        onConfirm = {
            pendingDelete?.delete?.invoke()
            pendingDelete = null
        },
        onDismiss = { pendingDelete = null },
    )
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
    val haptics = rememberEchoHaptics()
    SettingsRow(
        iconRes = iconRes,
        label = label,
        subtitle = subtitle,
        end = RowEnd.None,
        onClick = {
            haptics.toggle(!checked)
            onCheckedChange(!checked)
        },
        trailing = { EchoSwitch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

/** Keeps a sheet's content while it animates out after its subject was cleared. */
@Composable
private fun <T : Any> rememberLast(value: T?): T? {
    val last = remember { arrayOfNulls<Any>(1) }
    if (value != null) last[0] = value
    @Suppress("UNCHECKED_CAST")
    return last[0] as T?
}

private class PendingDelete(val title: String, val message: String, val delete: () -> Unit)

private fun notesModelDescription(model: NotesAiModel): String = when (model) {
    NotesAiModel.Fast -> "Quickest, shorter summaries"
    NotesAiModel.Default -> "Balanced summaries and tasks"
    NotesAiModel.Multilingual -> "Writes notes in their own language"
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
