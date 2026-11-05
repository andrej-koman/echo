package dev.andrej.echo.ui.notes

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.andrej.echo.ai.AnalysisBlock
import dev.andrej.echo.ai.PendingAnalysisWorker
import dev.andrej.echo.data.AnalysisQueueRepository
import dev.andrej.echo.data.DerivedRepository
import dev.andrej.echo.data.Folder
import dev.andrej.echo.data.PendingAnalysis
import dev.andrej.echo.data.Segment
import dev.andrej.echo.data.Speaker
import dev.andrej.echo.data.TodoItem
import dev.andrej.echo.data.Transcript
import dev.andrej.echo.data.TranscriptRepository
import dev.andrej.echo.data.ImportDay
import dev.andrej.echo.transcribe.ImportFile
import dev.andrej.echo.transcribe.LiveTranscript
import dev.andrej.echo.transcribe.TranscriptionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class PendingSeverity { Parked, Failed }

data class PendingCopy(val text: String, val severity: PendingSeverity)

/** How far a note has got — drawn as the card's stripe. */
sealed interface NoteStage {
    /** Queued, getting a model, parked on a precondition, or never analyzed. */
    data object Waiting : NoteStage
    data class Transcribing(val fraction: Float) : NoteStage
    data object Analyzing : NoteStage
    data object Failed : NoteStage
    data object Done : NoteStage
}

data class NoteRow(
    val id: String,
    val title: String,
    val excerpt: String,
    val duration: String,
    val date: String,
    val stage: NoteStage = NoteStage.Done,
    val folder: Folder? = null,
    val pending: PendingCopy? = null,
)

data class NotesUiState(
    val query: String = "",
    val notes: List<NoteRow> = emptyList(),
    val total: Int = 0,
    val loaded: Boolean = false,
)

/** Everything in flight, keyed by transcript id. */
internal data class Work(
    val statuses: Map<String, TranscriptionStatus> = emptyMap(),
    val queued: Map<String, PendingAnalysis> = emptyMap(),
    val analyzingId: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(
    private val repository: TranscriptRepository,
    private val queue: AnalysisQueueRepository,
    private val worker: PendingAnalysisWorker,
    private val derived: DerivedRepository,
    transcription: Flow<Map<String, TranscriptionStatus>> = flowOf(emptyMap()),
    private val live: Flow<LiveTranscript?> = flowOf(null),
    val speechModel: () -> String = { "" },
    private val onImport: (List<Uri>) -> Unit = {},
    offered: Flow<List<ImportFile>> = flowOf(emptyList()),
    val importStarted: Flow<String> = flowOf(),
    private val onAcceptImport: (ImportFile, Long, String?, ImportDay?) -> Unit = { _, _, _, _ -> },
    private val onDeclineImport: (ImportFile) -> Unit = {},
    val importDay: () -> ImportDay? = { null },
    private val onRetryTranscription: (String) -> Unit = {},
    private val onStopTranscription: (String) -> Unit = {},
    private val onDeleted: (String) -> Unit = {},
    private val audioFile: (String) -> File? = { null },
    folders: Flow<List<Folder>> = flowOf(emptyList()),
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val analyzingId: StateFlow<String?> = worker.activeId
    val analysisProgress: StateFlow<Map<String, dev.andrej.echo.ai.AnalysisProgress>> = worker.progress

    val transcripts: StateFlow<List<Transcript>> = repository.transcripts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val work: Flow<Work> = combine(transcription, queue.pending, worker.activeId) { statuses, entries, active ->
        Work(statuses, entries.associateBy { it.transcriptId }, active)
    }

    val pending: StateFlow<Map<String, PendingCopy>> = work
        .map { it.pendingCopies() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val transcription: StateFlow<Map<String, TranscriptionStatus>> = transcription
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Ids still being transcribed: their segments are rewritten per chunk, so not editable yet. */
    val transcribing: StateFlow<Set<String>> = transcription
        .map { statuses -> statuses.filterValues { it != TranscriptionStatus.Failed }.keys }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val transcriptionFailed: StateFlow<Set<String>> = transcription
        .map { statuses -> statuses.filterValues { it == TranscriptionStatus.Failed }.keys }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val tasks: StateFlow<List<TodoItem>> = derived.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val speakerNames: StateFlow<List<String>> = repository.speakerNames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folders: StateFlow<List<Folder>> = folders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** A folder's notes, newest first like the Notes list. */
    fun notesIn(folderId: String): Flow<List<NoteRow>> =
        combine(repository.transcripts, work, this.folders) { transcripts, work, folders ->
            rows(transcripts.filter { it.folderId == folderId }, now(), work, folders)
        }

    /** Full note rows for the calendar, independent of the Notes search. */
    val calendarNotes: StateFlow<List<NoteRow>> =
        combine(repository.transcripts, work, this.folders) { transcripts, work, folders ->
            rows(transcripts, now(), work, folders)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun moveToFolder(transcriptId: String, folderId: String?) {
        viewModelScope.launch { repository.moveToFolder(transcriptId, folderId) }
    }

    private val searchHits: Flow<Set<String>?> = query.flatMapLatest { text ->
        if (text.isBlank()) flowOf(null) else repository.search(text)
    }

    val state: StateFlow<NotesUiState> =
        combine(repository.transcripts, query, searchHits, work, this.folders) { transcripts, text, hits, work, folders ->
            val matches = if (hits == null) transcripts else transcripts.filter { it.id in hits }
            NotesUiState(
                query = text,
                notes = rows(matches, now(), work, folders),
                total = transcripts.size,
                loaded = true,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    fun search(text: String) {
        query.value = text
    }

    fun import(uris: List<Uri>) = onImport(uris)

    /** The next file waiting for the import sheet. */
    val offeredImport: StateFlow<ImportFile?> = offered.map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun acceptImport(file: ImportFile, createdAt: Long, title: String, always: ImportDay?) =
        onAcceptImport(file, createdAt, title, always)

    fun declineImport(file: ImportFile) = onDeclineImport(file)

    fun delete(id: String) {
        onDeleted(id)
        viewModelScope.launch {
            derived.deleteFor(id)
            repository.delete(id)
        }
    }

    fun setTaskDone(itemId: String, done: Boolean) {
        viewModelScope.launch { derived.setDone(itemId, done) }
    }

    /** While Whisper runs on this note, the segments it has heard so far, ahead of the database. */
    fun segments(transcriptId: String): Flow<List<Segment>> =
        combine(repository.segmentsFlow(transcriptId), live) { stored, live ->
            if (live?.id == transcriptId) live.segments else stored
        }

    fun stopTranscription(transcriptId: String) = onStopTranscription(transcriptId)

    fun saveSegments(transcriptId: String, segments: List<Segment>) {
        viewModelScope.launch { repository.editSegments(transcriptId, segments) }
    }

    fun speakers(transcriptId: String): Flow<List<Speaker>> = repository.speakers(transcriptId)

    fun renameSpeaker(transcriptId: String, index: Int, name: String) {
        viewModelScope.launch { repository.renameSpeaker(transcriptId, index, name) }
    }

    /** [speaker] null makes a new one first; [rewrite] gets the index to assign. */
    fun assignSpeaker(transcriptId: String, speaker: Int?, rewrite: (Int) -> List<Segment>) {
        viewModelScope.launch {
            val index = speaker ?: repository.addSpeaker(transcriptId)
            repository.editSegments(transcriptId, rewrite(index))
        }
    }

    /** The recording or imported file, once there is one to play. */
    fun audio(transcriptId: String): File? = audioFile(transcriptId)

    /** The detail screen's one retry button: a failed transcription retries that, not analysis. */
    fun analyze(transcriptId: String) {
        if (transcriptId in transcriptionFailed.value) {
            onRetryTranscription(transcriptId)
            return
        }
        if (worker.activeId.value != null) return

        worker.analyzeNowAsync(transcriptId)
    }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dayFormat = SimpleDateFormat("d MMM", Locale.getDefault())
private val yearFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

internal fun rows(
    transcripts: List<Transcript>,
    now: Long,
    work: Work = Work(),
    folders: List<Folder> = emptyList(),
): List<NoteRow> {
    val pending = work.pendingCopies()
    val byId = folders.associateBy { it.id }
    return transcripts
        .sortedByDescending { it.createdAt }
        .map { transcript ->
            NoteRow(
                id = transcript.id,
                title = transcript.title ?: title(transcript.text),
                excerpt = transcript.summary ?: transcript.text.trim(),
                duration = clock(transcript.durationMs),
                date = dateLabel(transcript.createdAt, now),
                stage = stage(transcript, work),
                folder = transcript.folderId?.let(byId::get),
                pending = pending[transcript.id],
            )
        }
}

internal fun Work.pendingCopies(): Map<String, PendingCopy> =
    pendingCopyByTranscript(queued.values.toList()) +
        listOfNotNull(analyzingId?.let { it to PendingCopy("Analyzing…", PendingSeverity.Parked) }).toMap() +
        statuses.mapValues { it.value.toPendingCopy() }

/** Transcription wins over analysis: a note is analyzed only once its text is in. */
internal fun stage(transcript: Transcript, work: Work): NoteStage {
    when (val status = work.statuses[transcript.id]) {
        TranscriptionStatus.Queued, is TranscriptionStatus.DownloadingModel -> return NoteStage.Waiting
        is TranscriptionStatus.Transcribing -> return NoteStage.Transcribing(status.fraction)
        is TranscriptionStatus.FindingSpeakers, is TranscriptionStatus.Analyzing -> return NoteStage.Analyzing
        TranscriptionStatus.Failed -> return NoteStage.Failed
        null -> Unit
    }
    if (work.analyzingId == transcript.id) return NoteStage.Analyzing
    val entry = work.queued[transcript.id]
    if (entry != null) {
        return when (entry.lastBlock?.toPendingCopy()?.severity) {
            null -> if (entry.lastBlock == null) NoteStage.Analyzing else NoteStage.Done
            PendingSeverity.Parked -> NoteStage.Waiting
            PendingSeverity.Failed -> NoteStage.Failed
        }
    }
    return if (transcript.analyzedAt != null) NoteStage.Done else NoteStage.Waiting
}

/** Only entries the drain has actually attempted carry a [PendingAnalysis.lastBlock]. */
internal fun pendingCopyByTranscript(entries: List<PendingAnalysis>): Map<String, PendingCopy> =
    entries.mapNotNull { entry -> entry.lastBlock?.toPendingCopy()?.let { entry.transcriptId to it } }.toMap()

internal fun TranscriptionStatus.toPendingCopy(): PendingCopy = when (this) {
    TranscriptionStatus.Queued -> PendingCopy("Waiting to transcribe", PendingSeverity.Parked)
    is TranscriptionStatus.DownloadingModel ->
        PendingCopy("Getting the speech model… ${(fraction * 100).toInt()}%", PendingSeverity.Parked)
    is TranscriptionStatus.Transcribing ->
        PendingCopy("Transcribing… ${(fraction * 100).toInt()}%", PendingSeverity.Parked)
    is TranscriptionStatus.FindingSpeakers ->
        PendingCopy("Finding speakers… ${(fraction * 100).toInt()}%", PendingSeverity.Parked)
    is TranscriptionStatus.Analyzing ->
        PendingCopy("Analyzing… ${(fraction * 100).toInt()}%", PendingSeverity.Parked)
    TranscriptionStatus.Failed -> PendingCopy("Transcription failed — tap retry", PendingSeverity.Failed)
}

internal fun AnalysisBlock.toPendingCopy(): PendingCopy? = when (this) {
    AnalysisBlock.EmptyTranscript -> null

    is AnalysisBlock.WaitingForModel -> PendingCopy(
        text = if (bytes > 0) "Needs ~${bytes / 1_000_000}MB to analyze" else "Downloading the model…",
        severity = PendingSeverity.Parked,
    )

    is AnalysisBlock.NoBackend -> PendingCopy(
        text = "This phone can't analyze offline",
        severity = PendingSeverity.Parked,
    )

    is AnalysisBlock.GenerationFailed, AnalysisBlock.EmptyResult -> PendingCopy(
        text = "Analysis failed — tap retry",
        severity = PendingSeverity.Failed,
    )
}

/**
 * Fallback for a transcript analysis has not named yet — the opening of what was said, cut at
 * the first sentence end or at six words, whichever comes first.
 */
internal fun title(text: String): String {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return "Untitled"

    val sentence = trimmed.takeWhile { it != '.' && it != '?' && it != '!' }.trim()
    val words = sentence.split(' ').filter { it.isNotBlank() }
    return if (words.size <= 6) {
        sentence.ifEmpty { trimmed.take(40) }
    } else {
        words.take(6).joinToString(" ") + "…"
    }
}

internal fun clock(durationMs: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

internal fun spokenTotal(durationMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

internal fun dateLabel(createdAt: Long, now: Long): String {
    val date = Date(createdAt)
    val day = when (daysBetween(createdAt, now)) {
        0 -> "Today"
        1 -> "Yesterday"
        else -> if (sameYear(createdAt, now)) dayFormat.format(date) else yearFormat.format(date)
    }
    return "$day, ${timeFormat.format(date)}"
}

private fun sameYear(a: Long, b: Long): Boolean =
    Calendar.getInstance().apply { timeInMillis = a }.get(Calendar.YEAR) ==
        Calendar.getInstance().apply { timeInMillis = b }.get(Calendar.YEAR)

private fun daysBetween(then: Long, now: Long): Int {
    val start = Calendar.getInstance().apply { timeInMillis = then }.midnight()
    val end = Calendar.getInstance().apply { timeInMillis = now }.midnight()
    return TimeUnit.MILLISECONDS.toDays(end - start).toInt()
}

private fun Calendar.midnight(): Long {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    return timeInMillis
}
