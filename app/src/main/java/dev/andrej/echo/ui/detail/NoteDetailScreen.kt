package dev.andrej.echo.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import dev.andrej.echo.data.Segment
import dev.andrej.echo.ui.components.EchoSearchField
import dev.andrej.echo.ui.components.IconButtonSize
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.R
import dev.andrej.echo.ui.components.ButtonVariant
import dev.andrej.echo.ui.components.ButtonSize
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.components.IconButtonVariant
import dev.andrej.echo.ui.notes.NotesViewModel
import dev.andrej.echo.ui.notes.PendingCopy
import dev.andrej.echo.ui.notes.PendingSeverity
import dev.andrej.echo.ui.notes.clock
import dev.andrej.echo.ui.notes.title
import dev.andrej.echo.ui.tasks.TaskGroupCard
import dev.andrej.echo.ui.theme.EchoTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NoteDetailScreen(
    noteId: String,
    viewModel: NotesViewModel,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
    onEditTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val transcripts by viewModel.transcripts.collectAsStateWithLifecycle()
    val analyzingId by viewModel.analyzingId.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val polishingId by viewModel.polishingId.collectAsStateWithLifecycle()
    val polishFailed by viewModel.polishFailed.collectAsStateWithLifecycle()
    val transcript = transcripts.firstOrNull { it.id == noteId }
    val context = LocalContext.current

    val transcribing by viewModel.transcribing.collectAsStateWithLifecycle()
    val segments by remember(noteId) { viewModel.segments(noteId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val audio = remember(noteId, transcribing) { viewModel.audio(noteId) }
    val player = rememberNotePlayer(audio)

    var finding by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    val edits = remember { mutableStateMapOf<Int, String>() }

    Column(modifier = modifier.fillMaxSize()) {
        val entryPending = pending[noteId]
        val analyzing = analyzingId == noteId

        EchoTopBar(
            title = transcript?.let { it.title ?: title(it.text) } ?: "Note",
            subtitle = transcript?.let {
                "${detailDate.format(Date(it.createdAt))} · ${clock(it.durationMs)} · ${wordCount(it.text)} words"
            },
            leading = {
                EchoIconButton(
                    iconRes = R.drawable.ic_chevron_left,
                    contentDescription = "Back",
                    onClick = onBack,
                )
            },
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    EchoIconButton(
                        iconRes = R.drawable.ic_trash,
                        contentDescription = "Delete",
                        tint = EchoTheme.colors.textDanger,
                        onClick = {
                            viewModel.delete(noteId)
                            onDeleted()
                        },
                    )
                    EchoIconButton(
                        iconRes = R.drawable.ic_audio_waveform,
                        contentDescription = "Re-analyze",
                        variant = if (entryPending?.severity == PendingSeverity.Parked) {
                            IconButtonVariant.Ghost
                        } else {
                            IconButtonVariant.Soft
                        },
                        tint = if (entryPending?.severity == PendingSeverity.Parked) {
                            EchoTheme.colors.textTertiary
                        } else {
                            null
                        },
                        onClick = {
                            if (!analyzing && entryPending?.severity != PendingSeverity.Parked) {
                                viewModel.analyze(noteId)
                            }
                        },
                    )
                }
            },
        )

        if (transcript == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Note not found.",
                    style = EchoTheme.typography.body,
                    color = EchoTheme.colors.textTertiary,
                )
            }
            return@Column
        }

        val polished = transcript.polishedText != null
        val editable = !polished && segments.isNotEmpty() && noteId !in transcribing
        val current = player?.takeIf { it.playing || it.positionMs > 0 }?.let { segmentAt(segments, it.positionMs) } ?: -1
        val gutter = EchoTheme.spacing.gutterScreen

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(gutter),
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
        ) {
            item(key = "note") {
                Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5)) {
                    if (entryPending != null) {
                        AnalysisStatusCard(
                            pending = entryPending,
                            retrying = analyzing,
                            onRetry = { viewModel.analyze(noteId) },
                        )
                    } else {
                        transcript.summary?.let { summary ->
                            SectionCaption(label = "NOTE")
                            Text(
                                text = summary,
                                style = EchoTheme.typography.body,
                                color = EchoTheme.colors.textPrimary,
                            )
                        }

                        val notesTasks = tasks.filter { it.sourceTranscriptId == noteId }
                        if (notesTasks.isNotEmpty()) {
                            SectionCaption(label = "TASKS FROM THIS NOTE", trailingCount = notesTasks.size)
                            TaskGroupCard(
                                items = notesTasks,
                                showDate = false,
                                sourceTitleFor = { null },
                                onToggle = { viewModel.setTaskDone(it.id, !it.done) },
                                onEdit = { task ->
                                    val offset = task.sourceOffsetMs
                                    if (player != null && offset != null) player.playFrom(offset) else onEditTask(task.id)
                                },
                                onLongPress = { onEditTask(it.id) },
                            )
                        }
                    }

                    SectionCaption(
                        label = if (polished) "WHAT WAS SAID · POLISHED" else "WHAT WAS SAID",
                        trailingText = transcript.language,
                    )

                    val shown = transcript.polishedText ?: transcript.text
                    TranscriptActions(
                        editable = editable,
                        editing = editing,
                        finding = finding,
                        polished = polished,
                        polishing = polishingId == noteId,
                        onFind = {
                            finding = !finding
                            if (!finding) findQuery = ""
                        },
                        onEdit = {
                            if (editing) {
                                if (edits.isNotEmpty()) viewModel.saveSegments(noteId, applyEdits(segments, edits))
                                edits.clear()
                            }
                            editing = !editing
                        },
                        onCopy = { context.copyToClipboard(shown) },
                        onPolish = { if (polishingId == null) viewModel.polish(noteId) },
                        onRestore = { viewModel.restoreOriginal(noteId) },
                    )
                    if (noteId in polishFailed) {
                        Text(
                            text = "Couldn't polish this one. The transcript is unchanged.",
                            style = EchoTheme.typography.micro,
                            color = EchoTheme.colors.statusWarningFg,
                        )
                    }
                    if (finding && editable) {
                        FindReplaceBar(
                            query = findQuery,
                            onQueryChange = { findQuery = it },
                            replacement = replacement,
                            onReplacementChange = { replacement = it },
                            matches = countMatches(segments, findQuery),
                            onReplaceAll = {
                                viewModel.saveSegments(noteId, replaceAll(segments, findQuery, replacement))
                            },
                        )
                    }
                    if (polished || segments.isEmpty()) {
                        EchoCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = shown,
                                style = EchoTheme.typography.body,
                                color = EchoTheme.colors.textPrimary,
                                modifier = Modifier.testTag(TAG_DETAIL_TEXT),
                            )
                        }
                    }
                }
            }

            if (!polished) {
                itemsIndexed(segments, key = { i, _ -> "segment$i" }) { i, segment ->
                    SegmentRow(
                        segment = segment,
                        text = edits[i] ?: segment.text,
                        current = i == current,
                        editing = editing,
                        highlight = if (finding) findQuery else "",
                        onTextChange = { edits[i] = it },
                        onClick = { player?.playFrom(segment.startMs) },
                    )
                }
            }
        }

        player?.let { PlayerBar(it) }
    }
}

@Composable
private fun TranscriptActions(
    editable: Boolean,
    editing: Boolean,
    finding: Boolean,
    polished: Boolean,
    polishing: Boolean,
    onFind: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onPolish: () -> Unit,
    onRestore: () -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
    ) {
        if (editable) {
            EchoButton(
                text = if (editing) "Done" else "Edit",
                onClick = onEdit,
                variant = if (editing) ButtonVariant.Soft else ButtonVariant.Secondary,
                size = ButtonSize.Sm,
            )
            if (!editing) {
                EchoButton(
                    text = if (finding) "Close" else "Replace",
                    onClick = onFind,
                    variant = ButtonVariant.Secondary,
                    size = ButtonSize.Sm,
                )
            }
        }
        if (!editing) {
            EchoButton(
                text = "Copy",
                onClick = onCopy,
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Sm,
            )
            EchoButton(
                text = when {
                    polished -> "Restore original"
                    polishing -> "Polishing…"
                    else -> "Polish"
                },
                onClick = if (polished) onRestore else onPolish,
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Sm,
                loading = polishing,
            )
        }
    }
}

@Composable
private fun FindReplaceBar(
    query: String,
    onQueryChange: (String) -> Unit,
    replacement: String,
    onReplacementChange: (String) -> Unit,
    matches: Int,
    onReplaceAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3)) {
        EchoSearchField(value = query, onValueChange = onQueryChange, placeholder = "Find in transcript")
        EchoSearchField(
            value = replacement,
            onValueChange = onReplacementChange,
            placeholder = "Replace with",
            iconRes = R.drawable.ic_pen_line,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    query.isEmpty() -> ""
                    matches == 1 -> "1 match"
                    else -> "$matches matches"
                },
                style = EchoTheme.typography.micro,
                color = EchoTheme.colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            EchoButton(
                text = "Replace all",
                onClick = onReplaceAll,
                variant = ButtonVariant.Soft,
                size = ButtonSize.Sm,
                enabled = matches > 0,
            )
        }
    }
}

@Composable
private fun SegmentRow(
    segment: Segment,
    text: String,
    current: Boolean,
    editing: Boolean,
    highlight: String,
    onTextChange: (String) -> Unit,
    onClick: () -> Unit,
) {
    val colors = EchoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(EchoTheme.radii.sm)
            .background(if (current && !editing) colors.transcriptHighlight else Color.Transparent)
            .then(if (editing) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = EchoTheme.spacing.s3, vertical = EchoTheme.spacing.s2),
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
    ) {
        Text(
            text = clock(segment.startMs),
            style = EchoTheme.typography.micro,
            color = colors.textTertiary,
            modifier = Modifier.padding(top = 3.dp).width(36.dp),
        )
        if (editing) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = EchoTheme.typography.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.textAccent),
                modifier = Modifier
                    .weight(1f)
                    .clip(EchoTheme.radii.xs)
                    .background(colors.surfaceSunken)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        } else {
            Text(
                text = highlighted(text, highlight, colors.surfaceAccentSoft),
                style = EchoTheme.typography.body,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun highlighted(text: String, query: String, background: Color): AnnotatedString =
    buildAnnotatedString {
        append(text)
        matchRanges(text, query).forEach { range ->
            addStyle(SpanStyle(background = background), range.first, range.last + 1)
        }
    }

@Composable
private fun PlayerBar(player: NotePlayer) {
    val colors = EchoTheme.colors
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = player.durationMs.coerceAtLeast(1)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceBar)
            .drawBehind {
                drawLine(colors.borderSubtle, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
            }
            .navigationBarsPadding()
            .padding(horizontal = EchoTheme.spacing.gutterScreen, vertical = EchoTheme.spacing.s3),
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EchoIconButton(
            iconRes = if (player.playing) R.drawable.ic_pause else R.drawable.ic_play,
            contentDescription = if (player.playing) "Pause" else "Play",
            onClick = player::toggle,
            variant = IconButtonVariant.Soft,
            size = IconButtonSize.Md,
        )
        val shownMs = dragging?.let { (it * duration).toLong() } ?: player.positionMs
        Text(
            text = clock(shownMs),
            style = EchoTheme.typography.micro,
            color = colors.textSecondary,
        )
        Slider(
            value = dragging ?: (player.positionMs.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { player.seekTo((it * duration).toLong()) }
                dragging = null
            },
            colors = SliderDefaults.colors(
                thumbColor = colors.textAccent,
                activeTrackColor = colors.textAccent,
                inactiveTrackColor = colors.controlTrack,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = clock(player.durationMs),
            style = EchoTheme.typography.micro,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun SectionCaption(
    label: String,
    trailingText: String? = null,
    trailingCount: Int? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = EchoTheme.typography.microCaps,
            color = EchoTheme.colors.textTertiary,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = EchoTheme.colors.borderSubtle,
        )
        if (trailingText != null) {
            Text(
                text = trailingText,
                style = EchoTheme.typography.micro,
                color = EchoTheme.colors.textTertiary,
            )
        }
        if (trailingCount != null) {
            Text(
                text = trailingCount.toString(),
                style = EchoTheme.typography.caption,
                color = EchoTheme.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun AnalysisStatusCard(
    pending: PendingCopy,
    retrying: Boolean,
    onRetry: () -> Unit,
) {
    val failed = pending.severity == PendingSeverity.Failed
    val iconBg = if (failed) EchoTheme.colors.statusWarningBg else EchoTheme.colors.statusNeutralBg
    val iconFg = if (failed) EchoTheme.colors.statusWarningFg else EchoTheme.colors.textSecondary

    EchoCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(EchoTheme.radii.pill)
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(if (failed) R.drawable.ic_x else R.drawable.ic_clock),
                    contentDescription = null,
                    tint = iconFg,
                    modifier = Modifier.size(17.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = pending.text,
                    style = EchoTheme.typography.micro.copy(fontWeight = FontWeight.Medium),
                    color = iconFg,
                )
                Text(
                    text = if (failed) {
                        "The recording is safe — retry when the phone is idle, or leave it as a plain recording."
                    } else {
                        "Echo keeps recording either way. The note and its tasks appear here as soon as the model finishes."
                    },
                    style = EchoTheme.typography.micro,
                    color = EchoTheme.colors.textSecondary,
                )
                if (failed) {
                    EchoButton(
                        text = if (retrying) "Analyzing…" else "Try again",
                        onClick = onRetry,
                        variant = ButtonVariant.Soft,
                        size = ButtonSize.Sm,
                        loading = retrying,
                    )
                }
            }
        }
    }
}

private val detailDate = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())

private fun wordCount(text: String) = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }

private fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Echo transcript", text))
}

const val TAG_DETAIL_TEXT = "detail_text"
