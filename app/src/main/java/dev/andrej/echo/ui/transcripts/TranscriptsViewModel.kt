package dev.andrej.echo.ui.transcripts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.andrej.echo.ai.TranscriptAnalyzer
import dev.andrej.echo.data.DerivedRepository
import dev.andrej.echo.data.Transcript
import dev.andrej.echo.data.TranscriptRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class TranscriptRow(
    val id: String,
    val title: String,
    val excerpt: String,
    val duration: String,
    val time: String,
)

data class TranscriptGroup(
    val label: String,
    val items: List<TranscriptRow>,
)

data class TranscriptsUiState(
    val query: String = "",
    val groups: List<TranscriptGroup> = emptyList(),
    val total: Int = 0,
    val totalDuration: String = "",
    val loaded: Boolean = false,
)

class TranscriptsViewModel(
    private val repository: TranscriptRepository,
    private val derived: DerivedRepository,
    private val analyzer: TranscriptAnalyzer,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val _analyzingId = MutableStateFlow<String?>(null)
    val analyzingId: StateFlow<String?> = _analyzingId

    val transcripts: StateFlow<List<Transcript>> = repository.transcripts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<TranscriptsUiState> =
        combine(repository.transcripts, query) { transcripts, text ->
            val matches = transcripts.filter { it.text.contains(text.trim(), ignoreCase = true) }
            TranscriptsUiState(
                query = text,
                groups = group(matches, now()),
                total = transcripts.size,
                totalDuration = spokenTotal(transcripts.sumOf { it.durationMs }),
                loaded = true,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TranscriptsUiState())

    fun search(text: String) {
        query.value = text
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun analyze(transcriptId: String) {
        if (_analyzingId.value != null) return

        viewModelScope.launch {
            _analyzingId.value = transcriptId
            try {
                val transcript = transcripts.value.firstOrNull { it.id == transcriptId } ?: return@launch
                val analysis = analyzer.analyze(transcript) ?: return@launch

                derived.replaceFor(
                    transcriptId = transcript.id,
                    tasks = analysis.tasks,
                    reminders = analysis.reminders,
                    createdAt = now(),
                )
                repository.attachAnalysis(
                    id = transcript.id,
                    title = analysis.title,
                    summary = analysis.summary,
                    analyzedAt = now(),
                )
            } finally {
                _analyzingId.value = null
            }
        }
    }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dayFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

internal fun group(transcripts: List<Transcript>, now: Long): List<TranscriptGroup> =
    transcripts
        .sortedByDescending { it.createdAt }
        .groupBy { dayLabel(it.createdAt, now) }
        .map { (label, items) -> TranscriptGroup(label, items.map(Transcript::asRow)) }

private fun Transcript.asRow() = TranscriptRow(
    id = id,
    title = title ?: title(text),
    excerpt = summary ?: text.trim(),
    duration = clock(durationMs),
    time = timeFormat.format(Date(createdAt)),
)

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

private fun dayLabel(createdAt: Long, now: Long): String {
    val days = daysBetween(createdAt, now)
    return when (days) {
        0 -> "Today"
        1 -> "Yesterday"
        else -> dayFormat.format(Date(createdAt))
    }
}

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
