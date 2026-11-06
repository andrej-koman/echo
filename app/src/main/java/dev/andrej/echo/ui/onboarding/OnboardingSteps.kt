package dev.andrej.echo.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andrej.echo.R
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import dev.andrej.echo.data.TodoItem
import dev.andrej.echo.ui.components.Waveform
import dev.andrej.echo.ui.components.EchoBadge
import dev.andrej.echo.ui.components.BadgeTone
import dev.andrej.echo.ui.components.PickerSheet
import dev.andrej.echo.ui.tasks.TaskRow
import dev.andrej.echo.speech.whisper.WhisperModel
import dev.andrej.echo.ui.profile.LanguageOption
import java.util.Calendar
import dev.andrej.echo.ui.components.ButtonSize
import dev.andrej.echo.ui.components.ButtonVariant
import dev.andrej.echo.ui.components.CardPadding
import dev.andrej.echo.ui.components.CardTone
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.Mascot
import dev.andrej.echo.ui.components.MascotMood
import dev.andrej.echo.ui.components.RecordButton
import dev.andrej.echo.ui.components.RecordButtonSize
import dev.andrej.echo.ui.components.RecordButtonState
import dev.andrej.echo.ui.theme.Bronze400
import dev.andrej.echo.ui.theme.Bronze500
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.echoShadow

@Composable
internal fun WelcomeStep(onGetStarted: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.gapSection, Alignment.CenterVertically),
        ) {
            val aura = EchoTheme.colors.aura
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .drawBehind {
                        // The design's glow is a 300dp disc; drawn past this box's bounds so its
                        // empty fade does not push the headline down. CSS radial-gradient sizes to
                        // the farthest corner, hence √2 × the disc's half-width.
                        val radius = 150.dp.toPx()
                        drawCircle(
                            Brush.radialGradient(
                                0f to aura,
                                0.28f to aura,
                                0.7f to aura.copy(alpha = 0f),
                                center = center,
                                radius = radius * 1.414f,
                            ),
                            radius = radius * 1.414f,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                // The design's image has transparent margin around the ghost; this raster is
                // tighter, so a smaller size draws the same visible ghost.
                Mascot(size = 120.dp, mood = MascotMood.Breathing, contentDescription = "Echo, the ghost mascot")
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Say it. Echo\nsorts the rest.",
                    style = EchoTheme.typography.display,
                    color = EchoTheme.colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Talk naturally and get a transcript, notes and todos, all on your phone.",
                    style = EchoTheme.typography.body,
                    color = EchoTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 300.dp),
                )
            }
            Row(
                modifier = Modifier
                    .clip(EchoTheme.radii.pill)
                    .background(EchoTheme.colors.statusSuccessBg)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_wifi_off),
                    contentDescription = null,
                    tint = EchoTheme.colors.statusSuccessFg,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = "Works fully offline",
                    style = EchoTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                    color = EchoTheme.colors.statusSuccessFg,
                )
            }
        }

        EchoButton(
            text = "Get started",
            onClick = onGetStarted,
            size = ButtonSize.Lg,
            fullWidth = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun ColumnScope.HowItWorksStep() {
    StepIntro(
        title = "Talk now, read it a moment later",
        body = "Echo writes down what you say, then pulls out the tasks and plans inside it.",
    )

    EchoCard(tone = CardTone.Raised, padding = CardPadding.Md, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_audio_waveform),
                    contentDescription = null,
                    tint = EchoTheme.colors.textSecondary,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = "You said",
                    style = EchoTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                    color = EchoTheme.colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "0:14",
                    style = EchoTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                    color = EchoTheme.colors.textSecondary,
                )
            }
            Waveform(modifier = Modifier.fillMaxWidth(), bars = 36)
            DemoTranscript()
        }
    }

    Row(
        modifier = Modifier.padding(horizontal = EchoTheme.spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_sparkles),
            contentDescription = null,
            tint = EchoTheme.colors.textSuccess,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = "A moment after you stop, tasks appear",
            style = EchoTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
            color = EchoTheme.colors.textSuccess,
        )
    }

    EchoCard(tone = CardTone.Paper, padding = CardPadding.Sm, modifier = Modifier.fillMaxWidth()) {
        val tasks = remember { demoTasks() }
        var done by remember { mutableStateOf(setOf<String>()) }
        Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s1)) {
            tasks.forEach { task ->
                TaskRow(
                    task = task.copy(done = task.id in done),
                    onToggle = { done = if (task.id in done) done - task.id else done + task.id },
                    onEdit = {},
                    showDate = true,
                )
            }
        }
    }
}

/** Illustration only: never stored. Dates are relative so the example never looks stale. */
private fun demoTasks(): List<TodoItem> {
    val now = System.currentTimeMillis()
    fun at(daysAhead: Int, hour: Int) = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, daysAhead)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
    }.timeInMillis
    return listOf(
        TodoItem("demo-1", "demo", "Call John about the client project", at(1, 9), hasTime = true, createdAt = now),
        TodoItem("demo-2", "demo", "Send notes before the FDC meeting", at(3, 13), hasTime = true, createdAt = now),
    )
}

private val DemoSegments = listOf(
    "Remind me to " to false,
    "call John about client project at nine tomorrow" to true,
    ". Oh, and I need to " to false,
    "send my notes" to true,
    " before the " to false,
    "FDC meeting on Friday at one" to true,
    "." to false,
)

@Composable
private fun DemoTranscript() {
    val highlight = SpanStyle(
        color = EchoTheme.colors.textPrimary,
        fontWeight = FontWeight.Medium,
        background = EchoTheme.colors.transcriptHighlight,
    )
    Text(
        text = buildAnnotatedString {
            DemoSegments.forEach { (text, emphasis) ->
                if (emphasis) withStyle(highlight) { append(text) } else append(text)
            }
        },
        style = EchoTheme.typography.body.copy(lineHeight = 24.3.sp),
        color = EchoTheme.colors.textSecondary,
    )
}

@Composable
internal fun ColumnScope.RecordStep(
    uses: Set<RecordUse>,
    languageLabel: String,
    onToggle: (RecordUse) -> Unit,
    onPickLanguage: () -> Unit,
) {
    StepIntro(
        title = "What will you record?",
        body = "Pick all that apply. It tunes the setup Echo suggests for your phone.",
    )
    RecordUse.entries.forEach { use ->
        val (title, subtitle) = when (use) {
            RecordUse.QuickThoughts -> "Quick thoughts" to "Reminders, ideas, to-dos"
            RecordUse.Meetings -> "Meetings" to "Several people talking"
            RecordUse.Lectures -> "Lectures" to "Long recordings, mostly one voice"
            RecordUse.ExistingAudio -> "Existing audio" to "Files you already have"
        }
        ChoiceCard(title, subtitle, selected = use in uses, onClick = { onToggle(use) })
    }
    EchoCard(tone = CardTone.Paper, padding = CardPadding.Sm, onClick = onPickLanguage, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = EchoTheme.spacing.s2, vertical = EchoTheme.spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
        ) {
            Text(
                text = "Language",
                style = EchoTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                color = EchoTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(text = languageLabel, style = EchoTheme.typography.bodySm, color = EchoTheme.colors.textSecondary)
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = EchoTheme.colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
internal fun ColumnScope.PriorityStep(
    options: List<PriorityOption>,
    selected: Priority,
    onSelect: (Priority) -> Unit,
) {
    StepIntro(
        title = "What matters most?",
        body = "Echo picks the speech model that fits. You can change it any time in Settings.",
    )
    options.forEach { option ->
        val (title, subtitle) = when (option.priority) {
            Priority.Faster -> "Faster results" to "Notes ready sooner, a few more mistakes"
            Priority.Accurate -> "More accurate" to "Best text, takes longer to process"
            Priority.HelpMeChoose -> "Help me choose" to "Echo picks for your phone"
        }
        val size = if (option.ready) "No download" else formatSize(option.speech.spec.bytes)
        ChoiceCard(
            title = title,
            subtitle = "$subtitle\n${option.speech.name} · $size",
            selected = option.priority == selected,
            onClick = { onSelect(option.priority) },
        )
    }
}

@Composable
internal fun ColumnScope.SetupStep(
    summary: SetupSummary?,
    speech: WhisperModel,
    speechChoices: List<WhisperModel>,
    comparing: Boolean,
    onCompare: () -> Unit,
    onDismissCompare: () -> Unit,
    onSelectSpeech: (WhisperModel) -> Unit,
) {
    StepIntro(
        title = "Your setup",
        body = "Recommended for your phone. ${speech.name} turns speech into text, and a small " +
            "Notes AI model writes the note and finds tasks. Everything stays on this device.",
    )
    EchoCard(tone = CardTone.Paper, padding = CardPadding.Md, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5)) {
            summary?.rows?.forEach { row ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
                ) {
                    Text(
                        text = row.label,
                        style = EchoTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                        color = EchoTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (row.ready) {
                        EchoBadge(text = "Ready", tone = BadgeTone.Success)
                    } else {
                        Text(
                            text = formatSize(row.totalBytes),
                            style = EchoTheme.typography.bodySm,
                            color = EchoTheme.colors.textSecondary,
                        )
                    }
                }
            }
            if (summary != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5)) {
                    Text(
                        text = if (summary.totalDownloadBytes == 0L) "Nothing to download" else "Total download",
                        style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.Medium),
                        color = EchoTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (summary.totalDownloadBytes > 0) {
                        Text(
                            text = formatSize(summary.totalDownloadBytes),
                            style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.Medium),
                            color = EchoTheme.colors.textPrimary,
                        )
                    }
                }
                Text(
                    text = "${formatSize(summary.freeBytes)} free on this phone",
                    style = EchoTheme.typography.micro,
                    color = EchoTheme.colors.textTertiary,
                )
            }
        }
    }
    EchoButton(
        text = "Compare models",
        onClick = onCompare,
        variant = ButtonVariant.Ghost,
        size = ButtonSize.Sm,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
    PickerSheet(
        visible = comparing,
        title = "Speech models",
        options = speechChoices.map { it.name to it },
        selected = speech,
        onPick = { onSelectSpeech(it); onDismissCompare() },
        onDismiss = onDismissCompare,
        subtitle = { it.hint },
    )
}

@Composable
internal fun SetupActions(
    onWifi: () -> Unit,
    onMobileData: () -> Unit,
    onLater: () -> Unit,
    nothingToDownload: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s2)) {
        if (nothingToDownload) {
            EchoButton(text = "Continue", onClick = onLater, size = ButtonSize.Lg, fullWidth = true, modifier = Modifier.fillMaxWidth())
        } else {
            EchoButton(text = "Download on Wi-Fi", onClick = onWifi, size = ButtonSize.Lg, fullWidth = true, modifier = Modifier.fillMaxWidth())
            EchoButton(
                text = "Download now with mobile data",
                onClick = onMobileData,
                variant = ButtonVariant.Soft,
                fullWidth = true,
                modifier = Modifier.fillMaxWidth(),
            )
            EchoButton(text = "Set up later", onClick = onLater, variant = ButtonVariant.Ghost, fullWidth = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun ColumnScope.FirstNoteStep(
    status: SetupStatus,
    microphoneBlocked: Boolean,
    onRecord: () -> Unit,
    onRetry: () -> Unit,
) {
    StepIntro(
        title = "Try your first note",
        body = "Tap the button and say what's on your mind. Echo turns it into a note when you stop.",
    )

    DownloadStatus(status, onRetry)

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = EchoTheme.spacing.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        RecordButton(state = RecordButtonState.Idle, onClick = onRecord, size = RecordButtonSize.Lg)
        Text(
            text = "Tap to start",
            style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.Medium),
            color = EchoTheme.colors.textSecondary,
        )
        if (microphoneBlocked) {
            Text(
                text = "Microphone blocked. Allow it in system settings, or import audio instead.",
                style = EchoTheme.typography.bodySm,
                color = EchoTheme.colors.textDanger,
                textAlign = TextAlign.Center,
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Try saying",
            style = EchoTheme.typography.microCaps.copy(fontWeight = FontWeight.Medium),
            color = EchoTheme.colors.textSecondary,
        )
        EchoCard(tone = CardTone.Cream, padding = CardPadding.Sm, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = EchoTheme.spacing.s2, vertical = EchoTheme.spacing.s1),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_audio_waveform),
                    contentDescription = null,
                    tint = EchoTheme.colors.textAccent,
                    modifier = Modifier.padding(top = EchoTheme.spacing.s1).size(16.dp),
                )
                Text(
                    text = "\"Remind me to water the plants tonight, and add milk and eggs to my shopping list.\"",
                    style = EchoTheme.typography.body.copy(lineHeight = 24.3.sp),
                    color = EchoTheme.colors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun DownloadStatus(status: SetupStatus, onRetry: () -> Unit) {
    if (status == SetupStatus.Idle) return
    EchoCard(tone = CardTone.Paper, padding = CardPadding.Sm, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(EchoTheme.spacing.s2),
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
        ) {
            when (status) {
                is SetupStatus.Downloading -> {
                    Text(
                        text = "Downloading models · ${(status.fraction * 100).toInt()}%",
                        style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.Medium),
                        color = EchoTheme.colors.textPrimary,
                    )
                    LinearProgressIndicator(
                        progress = { status.fraction },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(EchoTheme.radii.pill),
                        color = EchoTheme.colors.actionPrimaryBg,
                        trackColor = EchoTheme.colors.indicatorIdle,
                    )
                }

                SetupStatus.WaitingForWifi -> Text(
                    text = "Waiting for Wi-Fi. Downloads start by themselves once you connect.",
                    style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.Medium),
                    color = EchoTheme.colors.textPrimary,
                )

                SetupStatus.Failed -> {
                    Text(
                        text = "The download did not finish.",
                        style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.Medium),
                        color = EchoTheme.colors.textDanger,
                    )
                    EchoButton(text = "Retry", onClick = onRetry, variant = ButtonVariant.Soft, size = ButtonSize.Sm)
                }

                SetupStatus.Done -> Text(
                    text = "Models ready. Everything works offline.",
                    style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.Medium),
                    color = EchoTheme.colors.textSuccess,
                )

                SetupStatus.Idle -> Unit
            }
        }
    }
}

@Composable
private fun ChoiceCard(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    EchoCard(
        tone = if (selected) CardTone.Bronze else CardTone.Paper,
        padding = CardPadding.Sm,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = EchoTheme.spacing.s2, vertical = EchoTheme.spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s1)) {
                Text(
                    text = title,
                    style = EchoTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                    color = EchoTheme.colors.textPrimary,
                )
                Text(text = subtitle, style = EchoTheme.typography.bodySm, color = EchoTheme.colors.textSecondary)
            }
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = "Selected",
                    tint = EchoTheme.colors.textAccent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun StepIntro(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = title, style = EchoTheme.typography.title, color = EchoTheme.colors.textPrimary)
        Text(text = body, style = EchoTheme.typography.body, color = EchoTheme.colors.textSecondary)
    }
}
