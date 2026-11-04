package dev.andrej.echo.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.andrej.echo.data.DerivedRepository
import dev.andrej.echo.data.TodoItem
import dev.andrej.echo.data.Transcript
import dev.andrej.echo.data.TranscriptRepository
import dev.andrej.echo.ui.notes.title
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HistoryDay(
    val date: LocalDate,
    val inMonth: Boolean,
    val heatLevel: Int,
)

data class HistoryDetailRow(
    val id: String,
    val time: String,
    val title: String,
    val taskCount: Int,
)

data class HistoryUiState(
    val monthLabel: String = "",
    val weekdayLabels: List<String> = emptyList(),
    val days: List<HistoryDay> = emptyList(),
    val notesInMonth: Int = 0,
    val tasksInMonth: Int = 0,
    val activeDaysInMonth: Int = 0,
    val selectedDay: LocalDate = LocalDate.now(),
    val selectedDayLabel: String = "",
    val selectedDetail: List<HistoryDetailRow> = emptyList(),
    val canGoNext: Boolean = true,
    val weekDays: List<HistoryDay> = emptyList(),
    val previousNoteDay: LocalDate? = null,
)

class HistoryViewModel(
    private val repository: TranscriptRepository,
    private val derived: DerivedRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val today = Instant.ofEpochMilli(now()).atZone(zone).toLocalDate()
    private val displayedMonth = MutableStateFlow(YearMonth.from(today))
    private val selectedDay = MutableStateFlow(today)

    val state: StateFlow<HistoryUiState> =
        combine(repository.transcripts, derived.items, displayedMonth, selectedDay) { transcripts, items, month, selected ->
            buildHistoryState(transcripts, items, month, selected, today, zone)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun pick(day: LocalDate) {
        selectedDay.value = day
        displayedMonth.value = YearMonth.from(day)
    }

    fun previous(expanded: Boolean) = pick(calendarStep(selectedDay.value, expanded, -1))

    fun next(expanded: Boolean) = pick(calendarStep(selectedDay.value, expanded, 1))

    fun goToday() = pick(Instant.ofEpochMilli(now()).atZone(zone).toLocalDate())

}

internal fun calendarStep(day: LocalDate, expanded: Boolean, direction: Int): LocalDate =
    if (expanded) day.plusMonths(direction.toLong()) else day.plusWeeks(direction.toLong())

private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
private val dayDetailFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

internal fun buildHistoryState(
    transcripts: List<Transcript>,
    items: List<TodoItem>,
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): HistoryUiState {
    fun Transcript.day() = Instant.ofEpochMilli(createdAt).atZone(zone).toLocalDate()
    fun Long.day() = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

    val byDay = transcripts.groupBy { it.day() }

    val firstOfMonth = month.atDay(1)
    val gridStart = firstOfMonth.minusDays((firstOfMonth.dayOfWeek.value - 1).toLong())
    val gridSize = ((firstOfMonth.dayOfWeek.value - 1 + month.lengthOfMonth() + 6) / 7) * 7
    val days = (0 until gridSize).map { offset ->
        val date = gridStart.plusDays(offset.toLong())
        val count = byDay[date]?.size ?: 0
        HistoryDay(
            date = date,
            inMonth = YearMonth.from(date) == month,
            heatLevel = when {
                count == 0 -> 0
                count == 1 -> 1
                count == 2 -> 2
                else -> 3
            },
        )
    }

    val notesInMonth = transcripts.count { YearMonth.from(it.day()) == month }
    val tasksInMonth = items.count { YearMonth.from(it.createdAt.day()) == month }
    val activeDaysInMonth = byDay.keys.count { YearMonth.from(it) == month && byDay[it]!!.isNotEmpty() }

    val taskCounts = items.groupingBy { it.sourceTranscriptId }.eachCount()
    val selectedDetail = byDay[selected].orEmpty()
        .sortedByDescending { it.createdAt }
        .map { transcript ->
            HistoryDetailRow(
                id = transcript.id,
                time = timeFormat.format(Instant.ofEpochMilli(transcript.createdAt).atZone(zone)),
                title = transcript.title ?: title(transcript.text),
                taskCount = taskCounts[transcript.id] ?: 0,
            )
        }

    return HistoryUiState(
        monthLabel = monthFormat.format(month.atDay(1)),
        weekdayLabels = (0..6).map {
            gridStart.plusDays(it.toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        },
        days = days,
        notesInMonth = notesInMonth,
        tasksInMonth = tasksInMonth,
        activeDaysInMonth = activeDaysInMonth,
        selectedDay = selected,
        selectedDayLabel = if (selected == today) "Today, ${DateTimeFormatter.ofPattern("d MMMM").format(selected)}" else dayDetailFormat.format(selected),
        selectedDetail = selectedDetail,
        canGoNext = true,
        weekDays = (0..6).map { offset ->
            val date = selected.minusDays((selected.dayOfWeek.value - 1).toLong()).plusDays(offset.toLong())
            HistoryDay(date, YearMonth.from(date) == month, (byDay[date]?.size ?: 0).coerceAtMost(3))
        },
        previousNoteDay = byDay.keys.filter { it < selected }.maxOrNull(),
    )
}
