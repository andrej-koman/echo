package dev.andrej.echo.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.R
import dev.andrej.echo.data.Folder
import dev.andrej.echo.data.Segment
import dev.andrej.echo.data.Speaker
import dev.andrej.echo.data.TodoItem
import dev.andrej.echo.data.TaskTextLink
import dev.andrej.echo.data.taskLinks
import dev.andrej.echo.export.ExportFormat
import dev.andrej.echo.export.ExportNote
import dev.andrej.echo.export.canExport
import dev.andrej.echo.export.ExportOptions
import dev.andrej.echo.export.Exporter
import dev.andrej.echo.transcribe.TranscriptionStatus
import dev.andrej.echo.ui.components.ActionDivider
import dev.andrej.echo.ui.components.ActionRow
import dev.andrej.echo.ui.components.ActionSheet
import dev.andrej.echo.ui.components.BarCenter
import dev.andrej.echo.ui.folders.FolderPickerSheet
import dev.andrej.echo.ui.components.ButtonSize
import dev.andrej.echo.ui.components.ButtonVariant
import dev.andrej.echo.ui.components.ConfirmSheet
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.IconButtonSize
import dev.andrej.echo.ui.components.LocalBottomBarHeight
import dev.andrej.echo.ui.folders.FolderIcon
import dev.andrej.echo.ui.folders.folderColor
import dev.andrej.echo.ui.notes.NotesViewModel
import dev.andrej.echo.ui.notes.PendingCopy
import dev.andrej.echo.ui.notes.PendingSeverity
import dev.andrej.echo.ui.notes.clock
import dev.andrej.echo.ui.notes.dateLabel
import dev.andrej.echo.ui.notes.title
import dev.andrej.echo.ui.tasks.TaskRow
import dev.andrej.echo.ui.tasks.isOverdue
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.hairline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.ZoneId

private enum class NoteTab(val label: String, val icon: Int) {
    BySpeaker("By speaker", R.drawable.ic_users),
    FullText("Full text", R.drawable.ic_file_text),
    Tasks("Tasks", R.drawable.ic_list_checks),
}

private enum class NoteSheet { More, Rename, Delete, Share, Folder }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteDetailScreen(
    noteId: String,
    viewModel: NotesViewModel,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
    onEditTask: (String) -> Unit,
    openTasks: Boolean = false,
    isPro: Boolean = false,
    onUpgrade: () -> Unit = {},
    recording: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val transcripts by viewModel.transcripts.collectAsStateWithLifecycle()
    val analyzingId by viewModel.analyzingId.collectAsStateWithLifecycle()
    val analysisProgress by viewModel.analysisProgress.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val transcribing by viewModel.transcribing.collectAsStateWithLifecycle()
    val transcription by viewModel.transcription.collectAsStateWithLifecycle()
    val speakerNames by viewModel.speakerNames.collectAsStateWithLifecycle()
    val transcript = transcripts.firstOrNull { it.id == noteId }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val loadedSegments: List<Segment>? by remember(noteId) { viewModel.segments(noteId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val segments = loadedSegments.orEmpty()
    val speakers by remember(noteId) { viewModel.speakers(noteId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val audio = remember(noteId, transcribing) { viewModel.audio(noteId) }
    val player = rememberNotePlayer(audio)

    var tab by rememberSaveable { mutableStateOf(if (openTasks) NoteTab.Tasks else NoteTab.BySpeaker) }
    LaunchedEffect(noteId) { viewModel.noteOpened(noteId) }
    // Null until the note has loaded; tells "Full text just appeared" from "was always there".
    var wasTranscribed by remember(noteId) { mutableStateOf<Boolean?>(null) }
    var sheet by remember { mutableStateOf<NoteSheet?>(null) }
    var finding by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    val edits = remember { mutableStateMapOf<Int, TextFieldValue>() }
    val selected = remember { mutableStateListOf<Int>() }
    var speakerTarget by remember { mutableStateOf<SpeakerTarget?>(null) }
    var exportOptions by remember { mutableStateOf(ExportOptions()) }
    val listState = rememberLazyListState()
    var focusedTaskId by remember(noteId) { mutableStateOf<String?>(null) }
    var tabsHeight by remember { mutableIntStateOf(0) }
    val noteTasks = remember(tasks, noteId) { tasks.filter { it.sourceTranscriptId == noteId } }
    val sourceLinks = remember(segments, noteTasks, transcript?.text) {
        taskLinks(if (segments.isEmpty()) listOf(transcript?.text.orEmpty()) else segments.map { it.text }, noteTasks)
    }
    val openSourceTask: (String) -> Unit = { id ->
        focusedTaskId = id
        tab = NoteTab.Tasks
    }
    LaunchedEffect(focusedTaskId, tab, noteTasks) {
        val id = focusedTaskId ?: return@LaunchedEffect
        if (tab == NoteTab.Tasks) {
            val index = noteTasks.indexOfFirst { it.id == id }
            if (index >= 0) {
                // Title and sticky tabs precede the individual task rows.
                listState.animateScrollToItem(index + 2, -tabsHeight)
                delay(1800)
            }
            focusedTaskId = null
        } else focusedTaskId = null
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (transcript == null) {
                DetailHeader(onBack = onBack, actions = {})
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Note not found.", style = EchoTheme.typography.body, color = EchoTheme.colors.textTertiary)
                }
                return@Column
            }

            val entryPending = pending[noteId]
            val status = processingStatus(noteId, transcription[noteId], analysisProgress[noteId])
            // Typing runs on while speakers are found, so the last window's lump still drains.
            val typed = typedSegments(loadedSegments, typing = status != null)
            val typedChars = typed.sumOf { it.text.trim().length }
            val typing = status is TranscriptionStatus.Transcribing ||
                typedChars < segments.sumOf { it.text.trim().length }
            val liveLine = if (typing) typed.lastIndex else -1
            // Each tab appears once its stage is done: text after Whisper, speakers once found,
            // tasks once analysed. Until then the text fills in with no tabs at all.
            val transcribed = status == null || status is TranscriptionStatus.FindingSpeakers || status is TranscriptionStatus.Analyzing
            val visibleTabs = buildList {
                if (speakers.isNotEmpty()) add(NoteTab.BySpeaker)
                if (transcribed) add(NoteTab.FullText)
                if (transcript.analyzedAt != null) add(NoteTab.Tasks)
            }
            val loaded = loadedSegments != null
            LaunchedEffect(loaded, transcribed) {
                if (!loaded) return@LaunchedEffect
                if (wasTranscribed == false && transcribed) tab = NoteTab.FullText
                wasTranscribed = transcribed
            }
            val shown = tab.takeIf { it in visibleTabs } ?: NoteTab.FullText
            val showingTranscript = shown == NoteTab.BySpeaker || shown == NoteTab.FullText
            FollowLiveText(listState, following = status != null && typing && showingTranscript, typedChars)
            val editable = segments.isNotEmpty() && noteId !in transcribing
            val textSelection = edits.entries.firstOrNull { !it.value.selection.collapsed }
            val current = player?.takeIf { it.playing || it.positionMs > 0 }
                ?.let { segmentAt(segments, it.positionMs) } ?: -1
            val highlight = if (finding) findQuery else ""
            val reading = rememberAnalysisReading(
                noteId = noteId,
                analyzing = status is TranscriptionStatus.Analyzing && analyzingId == noteId,
                ready = loaded && !typing,
                showingFullText = shown == NoteTab.FullText,
                interacting = finding || editing || selected.isNotEmpty(),
                paragraphs = listOf(typed.joinToString(" ") { it.text.trim() }),
                listState = listState,
                firstParagraphIndex = 2 + if (visibleTabs.isNotEmpty()) 1 else 0,
                onStart = { tab = NoteTab.FullText },
            )

            DetailHeader(onBack = onBack) {
                HeaderAction(R.drawable.ic_search, "Find", active = finding) {
                    finding = !finding
                    if (!finding) findQuery = ""
                    if (finding && !showingTranscript) tab = NoteTab.FullText
                }
                if (editable) {
                    HeaderAction(R.drawable.ic_pen_line, "Edit", active = editing) {
                        if (editing) {
                            if (edits.isNotEmpty()) {
                                viewModel.saveSegments(noteId, applyEdits(segments, edits.mapValues { it.value.text }))
                            }
                            edits.clear()
                        } else if (!showingTranscript) {
                            tab = NoteTab.FullText
                        }
                        editing = !editing
                    }
                }
                HeaderAction(R.drawable.ic_share, "Share") { sheet = NoteSheet.Share }
                HeaderAction(R.drawable.ic_ellipsis, "More") { sheet = NoteSheet.More }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag(TAG_DETAIL_TEXT),
                contentPadding = PaddingValues(bottom = EchoTheme.spacing.s6),
            ) {
                item(key = "title") {
                    Column(
                        modifier = Modifier.padding(
                            start = EchoTheme.spacing.gutterScreen,
                            end = EchoTheme.spacing.gutterScreen,
                            top = EchoTheme.spacing.s3,
                        ),
                        verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
                    ) {
                        Text(
                            text = transcript.title ?: title(transcript.text),
                            style = EchoTheme.typography.title,
                            color = EchoTheme.colors.textPrimary,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${dateLabel(transcript.createdAt, System.currentTimeMillis())} · ${clock(transcript.durationMs)}",
                                style = EchoTheme.typography.micro,
                                color = EchoTheme.colors.textTertiary,
                            )
                            FolderChip(
                                folder = folders.firstOrNull { it.id == transcript.folderId },
                                onClick = { sheet = NoteSheet.Folder },
                            )
                        }
                        if (entryPending != null && status == null && analyzingId != noteId) {
                            StatusCard(
                                pending = entryPending,
                                retrying = analyzingId == noteId,
                                onRetry = { viewModel.analyze(noteId) },
                            )
                        }
                    }
                }

                if (visibleTabs.isNotEmpty()) {
                    stickyHeader(key = "tabs") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(EchoTheme.colors.surfacePage)
                                .onSizeChanged { tabsHeight = it.height }
                                .padding(horizontal = EchoTheme.spacing.gutterScreen, vertical = EchoTheme.spacing.s4),
                        ) {
                            Tabs(tabs = visibleTabs, selected = shown, onSelect = { reading.stop(); tab = it })
                        }
                    }
                }

                if (showingTranscript) {
                    item(key = "tools") {
                        Column(
                            modifier = Modifier.padding(horizontal = EchoTheme.spacing.gutterScreen),
                            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
                        ) {
                            when {
                                selected.isNotEmpty() -> SelectionBar(
                                    count = selected.size,
                                    onAssign = { speakerTarget = SpeakerTarget.Segments(selected.toSet()) },
                                    onCancel = { selected.clear() },
                                )
                                editing -> EditBar(
                                    canAssignSelection = textSelection != null,
                                    onAssignSelection = {
                                        textSelection?.let { (i, value) ->
                                            speakerTarget = SpeakerTarget.Selection(i, value.selection.min until value.selection.max)
                                        }
                                    },
                                    onDone = {
                                        if (edits.isNotEmpty()) {
                                            viewModel.saveSegments(noteId, applyEdits(segments, edits.mapValues { it.value.text }))
                                        }
                                        edits.clear()
                                        editing = false
                                    },
                                )
                            }
                            if (finding) {
                                FindReplaceBar(
                                    query = findQuery,
                                    onQueryChange = { findQuery = it },
                                    replacement = replacement,
                                    onReplacementChange = { replacement = it },
                                    matches = if (segments.isEmpty()) {
                                        matchRanges(transcript.text, findQuery).size
                                    } else {
                                        countMatches(segments, findQuery)
                                    },
                                    canReplace = editable && !editing,
                                    onReplaceAll = {
                                        viewModel.saveSegments(noteId, replaceAll(segments, findQuery, replacement))
                                    },
                                )
                            }
                        }
                    }
                }

                when {
                    shown == NoteTab.Tasks -> tasksTab(
                        tasks = noteTasks,
                        analyzed = transcript.analyzedAt != null,
                        onToggle = { viewModel.setTaskDone(it.id, !it.done) },
                        onEdit = { onEditTask(it.id) },
                        focusedTaskId = focusedTaskId,
                    )
                    typed.isEmpty() -> item(key = "plain") {
                        Paragraph(
                            text = when {
                                status != null -> "Listening…"
                                else -> transcript.text.trim().ifBlank { "Nothing transcribed yet." }
                            },
                            current = false,
                            highlight = highlight,
                            onClick = {},
                            modifier = Modifier.padding(horizontal = EchoTheme.spacing.gutterScreen),
                            live = typing,
                            taskLinks = taskLinks(listOf(transcript.text), noteTasks).single(),
                            onTaskClick = openSourceTask,
                        )
                    }
                    shown == NoteTab.FullText && !editing -> item(key = "p0") {
                        ContinuousTranscript(
                            segments = typed,
                            taskLinks = if (selected.isEmpty()) sourceLinks else emptyList(),
                            current = current,
                            highlight = highlight,
                            live = typing,
                            onTaskClick = { reading.stop(); openSourceTask(it) },
                            onSegmentClick = { reading.stop(); player?.playFrom(segments[it].startMs) },
                            modifier = Modifier.padding(horizontal = EchoTheme.spacing.gutterScreen),
                            reading = reading,
                        )
                    }
                    shown == NoteTab.FullText -> items(typed.indices.toList(), key = { "p$it" }) { i ->
                        FullTextLine(
                            value = edits[i] ?: TextFieldValue(typed[i].text.trim()),
                            editing = editing,
                            onValueChange = { edits[i] = it },
                            current = i == current,
                            highlight = highlight,
                            onClick = { reading.stop(); player?.playFrom(segments[i].startMs) },
                            live = i == liveLine,
                            taskLinks = emptyList(),
                            reading = reading,
                            readingIndex = i,
                        )
                    }
                    else -> items(runs(typed), key = { "run${it.first}" }) { run ->
                        SpeakerRun(
                            run = run,
                            segments = typed,
                            speakers = speakers,
                            current = current,
                            liveLine = liveLine,
                            editing = editing,
                            selected = selected,
                            highlight = highlight,
                            edits = edits,
                            taskLinks = sourceLinks,
                            onTaskClick = openSourceTask,
                            onHeaderClick = { if (editable) speakerTarget = SpeakerTarget.Segments(run.toSet()) },
                            onLineClick = { i ->
                                when {
                                    selected.isEmpty() -> player?.playFrom(segments[i].startMs)
                                    i in selected -> selected.remove(i)
                                    else -> selected.add(i)
                                }
                            },
                            onLineLongClick = { i -> if (editable && i !in selected) selected.add(i) },
                        )
                    }
                }
                if (showingTranscript && transcript.transcribedUntilMs != null && !isPro) {
                    item(key = "unlock-transcript") {
                        FreeCapCard(transcript.transcribedUntilMs, transcript.durationMs, onUpgrade)
                    }
                }
            }

            val playable = player?.takeIf { status == null }
            if (status != null) {
                TranscribingRow(status = status, model = viewModel.speechModel())
                BarCenter {
                    StopButton(
                        onStop = { viewModel.stopTranscription(noteId) }
                            .takeIf { status is TranscriptionStatus.Transcribing || status is TranscriptionStatus.FindingSpeakers },
                    )
                }
            }
            playable?.let { player ->
                ProgressRow(player)
                BarCenter { PlayButton(player) }
            }
            val barHeight = LocalBottomBarHeight.current
            val onBar = playable != null || status != null
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(if (onBar && !recording) (barHeight - PlayRowOverlap).coerceAtLeast(0.dp) else barHeight)
                    .then(if (onBar) Modifier.background(EchoTheme.colors.surfaceBar) else Modifier),
            )
        }

        if (transcript != null) {
            DetailSheets(
                sheet = sheet,
                onSheet = { sheet = it },
                title = transcript.title ?: title(transcript.text),
                subtitle = listOfNotNull(
                    dateLabel(transcript.createdAt, System.currentTimeMillis()),
                    clock(transcript.durationMs),
                    tasks.count { it.sourceTranscriptId == noteId }
                        .takeIf { it > 0 }
                        ?.let { if (it == 1) "1 task" else "$it tasks" },
                ).joinToString(" · "),
                folderId = transcript.folderId,
                folders = folders,
                canAnalyze = pending[noteId]?.severity != PendingSeverity.Parked,
                analyzing = analyzingId == noteId,
                audioSize = audio?.let { megabytes(it.length()) },
                timed = segments.isNotEmpty(),
                exportOptions = exportOptions,
                onExportOptions = { exportOptions = it },
                isPro = isPro,
                onUpgrade = { sheet = null; onUpgrade() },
                onShare = { format ->
                    if (!format.canExport(isPro)) {
                        sheet = null
                        onUpgrade()
                        return@DetailSheets
                    }
                    val note = ExportNote(
                        title = transcript.title ?: title(transcript.text),
                        date = exportDate.format(Date(transcript.createdAt)),
                        text = transcript.text,
                        segments = segments,
                        speakers = speakers,
                        summary = transcript.summary,
                        tasks = tasks.filter { it.sourceTranscriptId == noteId },
                    )
                    scope.launch { Exporter(context).share(format, note, exportOptions, audio) }
                },
                onMove = { viewModel.moveToFolder(noteId, it) },
                onRename = { viewModel.rename(noteId, it) },
                onAnalyze = { viewModel.analyze(noteId) },
                onCopy = { context.copyToClipboard(transcript.text) },
                onDelete = {
                    viewModel.delete(noteId)
                    onDeleted()
                },
            )
        }

        val target = speakerTarget
        val targetIndices = when (target) {
            is SpeakerTarget.Segments -> target.indices
            is SpeakerTarget.Selection -> setOf(target.index)
            null -> emptySet()
        }
        val targetSpeaker = (target as? SpeakerTarget.Segments)
            ?.let { t -> t.indices.map { segments.getOrNull(it)?.speaker }.distinct().singleOrNull() }
            ?.let { index -> speakers.firstOrNull { it.index == index } ?: Speaker(index, null) }
        SpeakerSheet(
            visible = target != null,
            speaker = targetSpeaker,
            lineCount = targetSpeaker?.let { s -> segments.count { it.speaker == s.index } } ?: 0,
            quote = targetSpeaker?.let { s -> segments.firstOrNull { it.speaker == s.index } },
            suggestions = speakerNames.filter { name -> speakers.none { it.name == name } },
            others = speakers.filter { it.index != targetSpeaker?.index },
            moveLabel = when {
                target is SpeakerTarget.Selection -> "the selection"
                targetIndices.size == 1 -> "this line"
                else -> "these ${targetIndices.size} lines"
            },
            onPlay = { player?.playFrom(it.startMs) },
            onRename = { name ->
                targetSpeaker?.let { viewModel.renameSpeaker(noteId, it.index, name) }
                selected.clear()
                speakerTarget = null
            },
            onMove = { speaker ->
                when (target) {
                    is SpeakerTarget.Segments -> viewModel.assignSpeaker(noteId, speaker) { assignSpeaker(segments, target.indices, it) }
                    is SpeakerTarget.Selection -> {
                        val withEdits = segments.mapIndexed { i, segment -> edits[i]?.let { segment.copy(text = it.text) } ?: segment }
                        viewModel.assignSpeaker(noteId, speaker) {
                            applyEdits(assignSelection(withEdits, target.index, target.range, it), emptyMap())
                        }
                        // The split shifts every index after it, so the open fields would point at the wrong segments.
                        edits.clear()
                        editing = false
                    }
                    null -> Unit
                }
                selected.clear()
                speakerTarget = null
            },
            onDismiss = { speakerTarget = null },
        )
    }
}

private sealed interface SpeakerTarget {
    data class Segments(val indices: Set<Int>) : SpeakerTarget
    data class Selection(val index: Int, val range: IntRange) : SpeakerTarget
}

/**
 * Keeps the newest line in view while text streams in, until the reader drags away; scrolling
 * back to the end picks it up again.
 */
@Composable
private fun FollowLiveText(listState: LazyListState, following: Boolean, typedChars: Int) {
    var follow by remember { mutableStateOf(true) }
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged) { if (dragged) follow = false }
    LaunchedEffect(listState) {
        snapshotFlow { listState.canScrollForward }.collect { if (!it) follow = true }
    }
    LaunchedEffect(typedChars, following) {
        // Instant: this runs per typed character, and a restarted animation would never land.
        if (following && follow && typedChars > 0) {
            listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1, Int.MAX_VALUE)
        }
    }
}

/** Consecutive segments from one speaker, as index ranges. */
internal fun runs(segments: List<Segment>): List<IntRange> {
    val runs = mutableListOf<IntRange>()
    var start = 0
    for (i in 1..segments.size) {
        if (i == segments.size || segments[i].speaker != segments[start].speaker) {
            runs += start until i
            start = i
        }
    }
    return runs
}

@Composable
private fun SpeakerRun(
    run: IntRange,
    segments: List<Segment>,
    speakers: List<Speaker>,
    current: Int,
    liveLine: Int,
    editing: Boolean,
    selected: List<Int>,
    highlight: String,
    edits: MutableMap<Int, TextFieldValue>,
    onHeaderClick: () -> Unit,
    onLineClick: (Int) -> Unit,
    onLineLongClick: (Int) -> Unit,
    taskLinks: List<List<TaskTextLink>> = emptyList(),
    onTaskClick: (String) -> Unit = {},
) {
    val first = segments[run.first]
    val playing = current in run && !editing
    Column(
        modifier = Modifier
            .padding(horizontal = EchoTheme.spacing.s4, vertical = EchoTheme.spacing.s1)
            .fillMaxWidth()
            .clip(EchoTheme.radii.lg)
            .background(if (playing) EchoTheme.colors.surfaceCard else Color.Transparent)
            .padding(horizontal = EchoTheme.spacing.s2, vertical = EchoTheme.spacing.s2),
    ) {
        SpeakerHeader(
            speaker = first.speaker,
            label = first.speaker?.let { index -> speakers.firstOrNull { it.index == index }?.label ?: "Speaker ${index + 1}" },
            startMs = first.startMs,
            endMs = segments[run.last].endMs,
            playing = playing,
            onClick = onHeaderClick,
        )
        if (!editing && selected.isEmpty()) {
            ContinuousTranscript(
                segments = run.map { segments[it] },
                taskLinks = run.map { taskLinks.getOrNull(it).orEmpty() },
                current = if (current in run) current - run.first else -1,
                highlight = highlight,
                live = liveLine in run,
                onTaskClick = onTaskClick,
                onSegmentClick = { onLineClick(run.first + it) },
                onSegmentLongClick = { onLineLongClick(run.first + it) },
            )
        } else run.forEach { i ->
            SegmentLine(
                value = edits[i] ?: TextFieldValue(segments[i].text.trim()),
                current = false,
                editing = editing,
                selected = i in selected,
                highlight = highlight,
                onValueChange = { edits[i] = it },
                onClick = { onLineClick(i) },
                onLongClick = { onLineLongClick(i) },
                live = i == liveLine,
            )
        }
    }
}

private fun LazyListScope.tasksTab(
    tasks: List<TodoItem>,
    analyzed: Boolean,
    onToggle: (TodoItem) -> Unit,
    onEdit: (TodoItem) -> Unit,
    focusedTaskId: String?,
) {
    if (tasks.isNotEmpty()) {
        items(tasks.size, key = { "task:${tasks[it].id}" }) { index ->
            val task = tasks[index]
            val first = index == 0
            val last = index == tasks.lastIndex
            val corners = EchoTheme.radii.lg
            val square = CornerSize(0.dp)
            val shape = corners.copy(
                topStart = if (first) corners.topStart else square,
                topEnd = if (first) corners.topEnd else square,
                bottomStart = if (last) corners.bottomStart else square,
                bottomEnd = if (last) corners.bottomEnd else square,
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = EchoTheme.spacing.gutterScreen)
                    .clip(shape)
                    .background(EchoTheme.colors.surfaceCard)
                    .background(
                        if (task.id == focusedTaskId) EchoTheme.colors.taskHighlight
                        else Color.Transparent,
                    )
                    .border(Dp.Hairline, EchoTheme.colors.borderSubtle, shape)
                    .padding(
                        start = EchoTheme.spacing.s4,
                        end = EchoTheme.spacing.s4,
                        top = if (first) EchoTheme.spacing.s2 else 0.dp,
                        bottom = if (last) EchoTheme.spacing.s2 else 0.dp,
                    ),
            ) {
                TaskRow(
                    task = task,
                    onToggle = { onToggle(task) },
                    onEdit = { onEdit(task) },
                    showDate = task.hasDate,
                    overdue = isOverdue(task, System.currentTimeMillis(), ZoneId.systemDefault()),
                )
            }
        }
    } else if (analyzed) {
        item(key = "tasks") {
                Text(
                    text = "No tasks in this note.",
                    style = EchoTheme.typography.bodySm,
                    color = EchoTheme.colors.textTertiary,
                    modifier = Modifier.padding(horizontal = EchoTheme.spacing.gutterScreen),
                )
        }
    }
}

@Composable
private fun DetailHeader(onBack: () -> Unit, actions: @Composable () -> Unit) {
    val colors = EchoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = EchoTheme.spacing.gutterScreen, vertical = EchoTheme.spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(EchoTheme.radii.pill)
                .background(colors.surfaceCard)
                .hairline(colors.borderSubtle, cornerRadius = 20.dp),
        ) {
            EchoIconButton(
                iconRes = R.drawable.ic_chevron_left,
                contentDescription = "Back",
                onClick = onBack,
                size = IconButtonSize.Md,
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clip(EchoTheme.radii.pill)
                .background(colors.surfaceCard)
                .hairline(colors.borderSubtle, cornerRadius = 20.dp)
                .padding(horizontal = EchoTheme.spacing.s2, vertical = EchoTheme.spacing.s1),
            horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s1),
        ) { actions() }
    }
}

@Composable
private fun HeaderAction(icon: Int, description: String, active: Boolean = false, onClick: () -> Unit) {
    EchoIconButton(
        iconRes = icon,
        contentDescription = description,
        onClick = onClick,
        tint = if (active) EchoTheme.colors.textAccent else null,
    )
}

@Composable
private fun FolderChip(folder: Folder?, onClick: () -> Unit) {
    val color = folder?.let { folderColor(it.color) } ?: EchoTheme.colors.textTertiary
    Row(
        modifier = Modifier
            .clip(EchoTheme.radii.sm)
            .background(color.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = EchoTheme.spacing.s3, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(folder?.let { FolderIcon.of(it.icon).drawable } ?: R.drawable.ic_folder),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = folder?.name ?: "No folder",
            style = EchoTheme.typography.micro.copy(fontWeight = FontWeight.Medium),
            color = color,
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_down),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun Tabs(tabs: List<NoteTab>, selected: NoteTab, onSelect: (NoteTab) -> Unit) {
    val colors = EchoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(EchoTheme.radii.pill)
            .background(colors.surfaceSunken)
            .padding(3.dp),
    ) {
        tabs.forEach { tab ->
            val active = tab == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(EchoTheme.radii.pill)
                    .background(if (active) colors.surfaceRaised else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(tab.icon),
                    contentDescription = null,
                    tint = if (active) colors.textPrimary else colors.textTertiary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = tab.label,
                    style = EchoTheme.typography.micro.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (active) colors.textPrimary else colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun DetailSheets(
    sheet: NoteSheet?,
    onSheet: (NoteSheet?) -> Unit,
    title: String,
    subtitle: String,
    folderId: String?,
    folders: List<Folder>,
    canAnalyze: Boolean,
    analyzing: Boolean,
    audioSize: String?,
    timed: Boolean,
    exportOptions: ExportOptions,
    onExportOptions: (ExportOptions) -> Unit,
    onShare: (ExportFormat) -> Unit,
    isPro: Boolean,
    onUpgrade: () -> Unit,
    onMove: (String?) -> Unit,
    onRename: (String) -> Unit,
    onAnalyze: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismiss = { onSheet(null) }
    ActionSheet(visible = sheet == NoteSheet.More, title = title, subtitle = subtitle, onDismiss = dismiss) {
        ActionRow(
            icon = R.drawable.ic_pen_line,
            label = "Rename",
            onClick = { onSheet(NoteSheet.Rename) },
        )
        ActionRow(
            icon = R.drawable.ic_folder,
            label = "Move to folder",
            trailing = folders.firstOrNull { it.id == folderId }?.name ?: "No folder",
            onClick = { onSheet(NoteSheet.Folder) },
        )
        ActionRow(
            icon = R.drawable.ic_copy,
            label = "Copy text",
            description = "Copies the full transcript",
            onClick = {
                onCopy()
                dismiss()
            },
        )
        ActionRow(
            icon = R.drawable.ic_sparkles,
            label = if (analyzing) "Analyzing…" else "Redo summary and tasks",
            description = when {
                analyzing -> "Echo is reading this note now"
                !canAnalyze -> "Available once transcription finishes"
                else -> "Replaces the current ones"
            },
            enabled = canAnalyze && !analyzing,
            onClick = {
                onAnalyze()
                dismiss()
            },
        )
        ActionDivider()
        ActionRow(
            icon = R.drawable.ic_trash,
            label = "Delete note",
            destructive = true,
            onClick = { onSheet(NoteSheet.Delete) },
        )
    }
    RenameNoteSheet(
        visible = sheet == NoteSheet.Rename,
        title = title,
        onSave = {
            onRename(it)
            dismiss()
        },
        onDismiss = dismiss,
    )
    ConfirmSheet(
        visible = sheet == NoteSheet.Delete,
        title = "Delete this note?",
        message = "The recording, transcript and its tasks are removed. This can't be undone.",
        confirmLabel = "Delete note",
        onConfirm = {
            dismiss()
            onDelete()
        },
        onDismiss = dismiss,
    )
    FolderPickerSheet(
        visible = sheet == NoteSheet.Folder,
        title = "Move to folder",
        folders = folders,
        selected = folderId,
        onPick = {
            onMove(it)
            dismiss()
        },
        onDismiss = dismiss,
    )
    ShareSheet(
        visible = sheet == NoteSheet.Share,
        title = title,
        audioSize = audioSize,
        timed = timed,
        options = exportOptions,
        onOptions = onExportOptions,
        isPro = isPro,
        onUpgrade = { dismiss(); onUpgrade() },
        onShare = {
            onShare(it)
            dismiss()
        },
        onDismiss = dismiss,
    )
}

@Composable
private fun StatusCard(pending: PendingCopy, retrying: Boolean, onRetry: () -> Unit) {
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
                        "The summary appears on the note card and tasks appear under Tasks as soon as Echo is done."
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

private val exportDate = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())

private fun megabytes(bytes: Long) = "%.1f MB".format(bytes / 1_000_000.0)

internal fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Echo transcript", text))
}

const val TAG_DETAIL_TEXT = "detail_text"

@Composable
internal fun FullTextLine(
    value: TextFieldValue,
    editing: Boolean,
    onValueChange: (TextFieldValue) -> Unit,
    current: Boolean = false,
    highlight: String = "",
    onClick: () -> Unit = {},
    live: Boolean = false,
    taskLinks: List<TaskTextLink> = emptyList(),
    onTaskClick: (String) -> Unit = {},
    reading: AnalysisReading? = null,
    readingIndex: Int = -1,
) {
    Box(Modifier.padding(horizontal = EchoTheme.spacing.gutterScreen)) {
        if (editing) {
            SegmentLine(value, current, true, false, highlight, onValueChange, onClick, {}, live)
        } else {
            Paragraph(
                value.text, current, highlight, onClick, live = live,
                taskLinks = taskLinks, onTaskClick = onTaskClick,
                reading = reading, readingIndex = readingIndex,
            )
        }
    }
}
