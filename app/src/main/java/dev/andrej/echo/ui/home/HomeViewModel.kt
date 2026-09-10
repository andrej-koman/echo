package dev.andrej.echo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.andrej.echo.data.AnalysisQueueRepository
import dev.andrej.echo.data.DerivedRepository
import dev.andrej.echo.data.PendingAnalysis
import dev.andrej.echo.data.SettingsStore
import dev.andrej.echo.data.TodoItem
import dev.andrej.echo.data.Transcript
import dev.andrej.echo.data.TranscriptRepository
import dev.andrej.echo.ui.midnight
import dev.andrej.echo.ui.notes.PendingSeverity
import dev.andrej.echo.ui.notes.asRow
import dev.andrej.echo.ui.notes.clock
import dev.andrej.echo.ui.notes.pendingCopyByTranscript
import dev.andrej.echo.ui.notes.title
import dev.andrej.echo.ui.tasks.groupByDue
import dev.andrej.echo.ui.tasks.isOverdue
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What sits in the hero slot: the next item due today, or nothing to raise. */
sealed interface HeroState {
    data class NextUp(
        val item: TodoItem,
        val overdue: Boolean,
        val whenLabel: String,
    ) : HeroState

    data object Clear : HeroState
}

data class HomeTaskRow(
    val item: TodoItem,
    val sourceTitle: String,
    val overdue: Boolean,
)

data class LatestNote(
    val id: String,
    val title: String,
    val excerpt: String,
    val duration: String,
    val relativeTime: String,
    val taskCount: Int,
    val pendingText: String?,
    val pendingFailed: Boolean,
)

data class HomeUiState(
    val loaded: Boolean = false,
    val hasAnyTranscripts: Boolean = false,
    val greeting: String = "",
    val dateLabel: String = "",
    val analyzingCount: Int = 0,
    val hero: HeroState = HeroState.Clear,
    val heroBody: String = "",
    /** "Then" beside today's remaining items, "Coming up" when the hero has nothing to raise. */
    val thenLabel: String = "Then",
    val thenDateLabel: String? = null,
    val thenShowDate: Boolean = false,
    val thenTotalCount: Int = 0,
    val thenRows: List<HomeTaskRow> = emptyList(),
    val thenMoreUntimed: Int = 0,
    val latestNote: LatestNote? = null,
)

private const val THEN_ROW_COUNT = 2

class HomeViewModel(
    private val repository: TranscriptRepository,
    private val derived: DerivedRepository,
    private val queue: AnalysisQueueRepository,
    private val settings: SettingsStore,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    val state: StateFlow<HomeUiState> =
        combine(repository.transcripts, derived.items, queue.pending) { transcripts, items, pending ->
            buildState(transcripts, items, pending, now(), zone)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setDone(itemId: String, done: Boolean) {
        viewModelScope.launch { derived.setDone(itemId, done) }
    }

    /** Pushes an overdue item forward by the user's configured snooze duration rather than opening the full editor. */
    fun snooze(item: TodoItem) {
        viewModelScope.launch {
            derived.update(
                item.id,
                item.text,
                snoozeTime(now(), settings.snoozeMinutes),
                hasTime = true,
                hasDate = true,
                notify = item.notify,
            )
        }
    }
}

internal fun snoozeTime(now: Long, minutes: Int): Long =
    now + TimeUnit.MINUTES.toMillis(minutes.toLong())

internal fun buildState(
    transcripts: List<Transcript>,
    items: List<TodoItem>,
    pending: List<PendingAnalysis>,
    now: Long,
    zone: ZoneId,
): HomeUiState {
    fun sourceTitleFor(transcriptId: String) =
        transcripts.firstOrNull { it.id == transcriptId }?.let { it.title ?: title(it.text) } ?: "Recording"

    val groups = groupByDue(items, now, zone)
    val today = groups.firstOrNull { it.label == "Today" }?.items.orEmpty()
    val tomorrow = groups.firstOrNull { it.label == "Tomorrow" }?.items.orEmpty()
    val upcoming = groups.firstOrNull { it.label == "Upcoming" }?.items.orEmpty()

    val hero: HeroState
    val heroBody: String
    val thenLabel: String
    val thenDateLabel: String?
    val thenShowDate: Boolean
    val thenSource: List<TodoItem>
    var thenMoreUntimed = 0

    if (today.isNotEmpty()) {
        val heroItem = today.first()
        val overdue = isOverdue(heroItem, now, zone)
        hero = HeroState.NextUp(
            item = heroItem,
            overdue = overdue,
            whenLabel = heroWhenLabel(heroItem, overdue, now),
        )
        heroBody = ""
        thenLabel = "Then"
        thenDateLabel = dateStamp(now)
        thenShowDate = false

        val remaining = today.drop(1)
        val timed = remaining.filter { it.hasTime }.sortedBy { it.dueAt }
        val untimed = remaining.filterNot { it.hasTime }
        val shown = (timed + untimed).take(THEN_ROW_COUNT)
        thenMoreUntimed = (untimed.size - shown.count { !it.hasTime }).coerceAtLeast(0)
        thenSource = shown
    } else {
        hero = HeroState.Clear
        val comingUp = tomorrow + upcoming
        heroBody = if (comingUp.isNotEmpty()) {
            "${comingUp.size} ${if (comingUp.size == 1) "task is" else "tasks are"} waiting further out. " +
                "Say what you need to do and Echo files it with a time."
        } else {
            "Nothing on deck. Say what you need to do and Echo files it with a time."
        }
        thenLabel = "Coming up"
        thenDateLabel = null
        thenShowDate = true
        thenSource = comingUp.take(THEN_ROW_COUNT)
    }

    val thenTotalCount = if (today.isNotEmpty()) today.size - 1 else tomorrow.size + upcoming.size

    val thenRows = thenSource.map { item ->
        HomeTaskRow(item, sourceTitleFor(item.sourceTranscriptId), isOverdue(item, now, zone))
    }

    val taskCounts = items.groupingBy { it.sourceTranscriptId }.eachCount()
    val pendingByTranscript = pendingCopyByTranscript(pending)
    val latest = transcripts.maxByOrNull { it.createdAt }
    val latestNote = latest?.let {
        val row = it.asRow(pendingByTranscript[it.id], taskCounts[it.id] ?: 0)
        LatestNote(
            id = row.id,
            title = row.title,
            excerpt = row.excerpt,
            duration = clock(it.durationMs),
            relativeTime = relativeTime(it.createdAt, now),
            taskCount = row.taskCount,
            pendingText = row.pending?.text,
            pendingFailed = row.pending?.severity == PendingSeverity.Failed,
        )
    }

    return HomeUiState(
        loaded = true,
        hasAnyTranscripts = transcripts.isNotEmpty(),
        greeting = greetingWord(now, zone),
        dateLabel = dateStamp(now),
        analyzingCount = pendingByTranscript.values.count { it.severity == PendingSeverity.Parked },
        hero = hero,
        heroBody = heroBody,
        thenLabel = thenLabel,
        thenDateLabel = thenDateLabel,
        thenShowDate = thenShowDate,
        thenTotalCount = thenTotalCount,
        thenRows = thenRows,
        thenMoreUntimed = thenMoreUntimed,
        latestNote = latestNote,
    )
}

private val dayStampFormat = SimpleDateFormat("d MMM", Locale.getDefault())
private val dateStampFormat = SimpleDateFormat("EEE d MMM", Locale.getDefault())
private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dayTimeFormat = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())

private fun greetingWord(now: Long, zone: ZoneId): String {
    val hour = java.time.Instant.ofEpochMilli(now).atZone(zone).hour
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

private fun dateStamp(now: Long): String = dateStampFormat.format(Date(now)).uppercase()

private fun dayWord(dueAt: Long, now: Long): String {
    val days = TimeUnit.MILLISECONDS.toDays(dueAt.midnight() - now.midnight())
    return when (days) {
        0L -> "Today"
        -1L -> "Yesterday"
        1L -> "Tomorrow"
        else -> dayStampFormat.format(Date(dueAt))
    }
}

private fun heroWhenLabel(item: TodoItem, overdue: Boolean, now: Long): String {
    if (!item.hasTime) return "${dayWord(item.dueAt, now)} · NO TIME SET".uppercase()

    val time = clockFormat.format(Date(item.dueAt))
    val base = "${dayWord(item.dueAt, now)}, $time".uppercase()
    return if (overdue) "$base · OVERDUE" else base
}

private fun relativeTime(createdAt: Long, now: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(now - createdAt)
    val days = TimeUnit.MILLISECONDS.toDays(createdAt.midnight() - now.midnight())
    return when {
        days == 0L && minutes < 1 -> "JUST NOW"
        days == 0L && minutes < 60 -> "$minutes MIN AGO"
        days == 0L -> clockFormat.format(Date(createdAt)).uppercase()
        days == -1L -> "YESTERDAY, ${clockFormat.format(Date(createdAt))}".uppercase()
        else -> dayTimeFormat.format(Date(createdAt)).uppercase()
    }
}
