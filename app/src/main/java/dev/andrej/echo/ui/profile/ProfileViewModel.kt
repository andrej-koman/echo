package dev.andrej.echo.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.andrej.echo.data.DerivedRepository
import dev.andrej.echo.data.Transcript
import dev.andrej.echo.data.TodoItem
import dev.andrej.echo.data.TranscriptRepository
import dev.andrej.echo.ui.notes.spokenTotal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class WeekDay(val label: String, val fraction: Float, val active: Boolean)

data class ProfileUiState(
    val notesCount: Int = 0,
    val tasksDoneCount: Int = 0,
    val capturedTotal: String = "0m",
    val week: List<WeekDay> = emptyList(),
    val streak: Int = 0,
)

class ProfileViewModel(
    private val repository: TranscriptRepository,
    private val derived: DerivedRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    val state: StateFlow<ProfileUiState> =
        combine(repository.transcripts, derived.items) { transcripts, items ->
            buildState(transcripts, items, now(), zone)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())
}

internal fun buildState(
    transcripts: List<Transcript>,
    items: List<TodoItem>,
    now: Long,
    zone: ZoneId,
): ProfileUiState {
    val byDay = transcripts.groupBy { it.createdAt.toLocalDate(zone) }
    val today = now.toLocalDate(zone)

    val durations = (6 downTo 0).map { offset ->
        val day = today.minusDays(offset.toLong())
        day to byDay[day].orEmpty().sumOf { it.durationMs }
    }
    val maxDuration = durations.maxOf { it.second }.coerceAtLeast(1L)
    val normalizedWeek = durations.map { (day, durationMs) ->
        WeekDay(
            label = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
            fraction = if (durationMs > 0) {
                (durationMs.toFloat() / maxDuration).coerceAtLeast(0.08f)
            } else {
                0f
            },
            active = durationMs > 0,
        )
    }

    var streak = 0
    var cursor = today
    while (byDay[cursor]?.isNotEmpty() == true) {
        streak++
        cursor = cursor.minusDays(1)
    }

    return ProfileUiState(
        notesCount = transcripts.size,
        tasksDoneCount = items.count { it.done },
        capturedTotal = spokenTotal(transcripts.sumOf { it.durationMs }),
        week = normalizedWeek,
        streak = streak,
    )
}

private fun Long.toLocalDate(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
