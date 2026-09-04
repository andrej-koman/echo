package dev.andrej.echo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.andrej.echo.data.AnalysisQueueRepository
import dev.andrej.echo.data.DerivedRepository
import dev.andrej.echo.data.TodoItem
import dev.andrej.echo.data.Transcript
import dev.andrej.echo.data.TranscriptRepository
import dev.andrej.echo.ui.transcripts.TranscriptsUiState
import dev.andrej.echo.ui.transcripts.group
import dev.andrej.echo.ui.transcripts.pendingCopyByTranscript
import dev.andrej.echo.ui.transcripts.spokenTotal
import dev.andrej.echo.ui.transcripts.title
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class UpNextItem(
    val title: String,
    val detail: String,
    val isEvent: Boolean,
    val sourceTranscriptId: String,
)

private const val UP_NEXT_COUNT = 3

class HomeViewModel(
    private val repository: TranscriptRepository,
    private val derived: DerivedRepository,
    private val queue: AnalysisQueueRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val notes: StateFlow<TranscriptsUiState> =
        combine(repository.transcripts, query, queue.pending) { transcripts, text, pending ->
            val matches = transcripts.filter { it.text.contains(text.trim(), ignoreCase = true) }
            TranscriptsUiState(
                query = text,
                groups = group(matches, now(), pendingCopyByTranscript(pending)),
                total = transcripts.size,
                totalDuration = spokenTotal(transcripts.sumOf { it.durationMs }),
                loaded = true,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TranscriptsUiState())

    val upNext: StateFlow<List<UpNextItem>> =
        combine(derived.items, repository.transcripts) { items, transcripts ->
            buildUpNext(items, transcripts, now())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun search(text: String) {
        query.value = text
    }
}

internal fun buildUpNext(
    items: List<TodoItem>,
    transcripts: List<Transcript>,
    now: Long,
): List<UpNextItem> {
    fun sourceTitle(transcriptId: String) =
        transcripts.firstOrNull { it.id == transcriptId }?.let { it.title ?: title(it.text) } ?: "Recording"

    return items
        .filterNot { it.done }
        .sortedWith(compareBy({ it.dueAt }, { !it.hasTime }))
        .take(UP_NEXT_COUNT)
        .map {
            UpNextItem(
                title = it.text,
                detail = if (it.hasTime) formatDue(it.dueAt, now) else "From ${sourceTitle(it.sourceTranscriptId)}",
                isEvent = it.hasTime,
                sourceTranscriptId = it.sourceTranscriptId,
            )
        }
}

private val dueTimeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
private val dueDateFormat = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())

private fun formatDue(dueAt: Long, now: Long): String {
    val days = TimeUnit.MILLISECONDS.toDays(dueAt.midnight() - now.midnight())
    return when (days) {
        0L -> "Today, ${dueTimeFormat.format(Date(dueAt))}"
        1L -> "Tomorrow, ${dueTimeFormat.format(Date(dueAt))}"
        else -> dueDateFormat.format(Date(dueAt))
    }
}

private fun Long.midnight(): Long = Calendar.getInstance().apply {
    timeInMillis = this@midnight
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
