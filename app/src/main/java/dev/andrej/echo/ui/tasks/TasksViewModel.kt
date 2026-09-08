package dev.andrej.echo.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.andrej.echo.ai.AiCardState
import dev.andrej.echo.data.DerivedRepository
import dev.andrej.echo.data.TodoItem
import dev.andrej.echo.data.Transcript
import dev.andrej.echo.data.TranscriptRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DueGroup(
    val label: String,
    val items: List<TodoItem>,
    /** True only for the Upcoming group — its rows need a date, not just a time, in the "when" label. */
    val showDate: Boolean = false,
)

data class TasksUiState(
    val groups: List<DueGroup> = emptyList(),
    val completed: List<TodoItem> = emptyList(),
    val loaded: Boolean = false,
    val query: String = "",
    /** No usable backend and nothing to show — the empty state should point at AccountScreen. */
    val aiOff: Boolean = false,
)

class TasksViewModel(
    private val derived: DerivedRepository,
    transcriptRepository: TranscriptRepository,
    aiCardState: Flow<AiCardState>,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val transcripts: StateFlow<List<Transcript>> =
        transcriptRepository.transcripts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<TasksUiState> =
        combine(derived.items, aiCardState, query) { items, ai, text ->
            val filtered = filterByQuery(items, text)
            TasksUiState(
                groups = groupByDue(filtered, now(), zone),
                completed = completedByRecency(filtered),
                loaded = true,
                query = text,
                aiOff = items.isEmpty() && ai !is AiCardState.NanoReady && ai !is AiCardState.LiteRtReady,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    fun setDone(itemId: String, done: Boolean) {
        viewModelScope.launch { derived.setDone(itemId, done) }
    }

    fun search(text: String) {
        query.value = text
    }

    fun update(itemId: String, text: String, dueAt: Long, hasTime: Boolean, notify: Boolean) {
        viewModelScope.launch { derived.update(itemId, text, dueAt, hasTime, notify) }
    }

    fun delete(itemId: String) {
        viewModelScope.launch { derived.delete(itemId) }
    }
}

internal fun filterByQuery(items: List<TodoItem>, query: String): List<TodoItem> =
    items.filter { it.text.contains(query, ignoreCase = true) }

private fun dayOf(epochMilli: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(epochMilli).atZone(zone).toLocalDate()

/** An exact-time item is overdue once its clock time passes; a date-only item, only the next day. */
internal fun isOverdue(item: TodoItem, now: Long, zone: ZoneId): Boolean =
    if (item.hasTime) item.dueAt < now else dayOf(item.dueAt, zone) < dayOf(now, zone)

/**
 * Three buckets only: Today (includes every overdue item, sorted ahead of the rest of today's),
 * Tomorrow, and one flat Upcoming group for everything later. Done items never appear here —
 * they collapse into [completedByRecency] instead.
 */
internal fun groupByDue(items: List<TodoItem>, now: Long, zone: ZoneId): List<DueGroup> {
    val today = dayOf(now, zone)
    val tomorrow = today.plusDays(1)

    val undone = items.filterNot { it.done }
    val todayItems = undone.filter { dayOf(it.dueAt, zone) <= today }
    val tomorrowItems = undone.filter { dayOf(it.dueAt, zone) == tomorrow }
    val upcomingItems = undone.filter { dayOf(it.dueAt, zone) > tomorrow }

    val (overdue, dueToday) = todayItems.partition { isOverdue(it, now, zone) }

    val groups = mutableListOf<DueGroup>()
    if (todayItems.isNotEmpty()) {
        groups += DueGroup("Today", sortWithinGroup(overdue) + sortWithinGroup(dueToday))
    }
    if (tomorrowItems.isNotEmpty()) {
        groups += DueGroup("Tomorrow", sortWithinGroup(tomorrowItems))
    }
    if (upcomingItems.isNotEmpty()) {
        groups += DueGroup("Upcoming", sortWithinGroup(upcomingItems), showDate = true)
    }
    return groups
}

/** Timed items first, soonest first; date-only items after, oldest-created first. */
private fun sortWithinGroup(items: List<TodoItem>): List<TodoItem> =
    items.sortedWith(compareBy({ !it.hasTime }, { if (it.hasTime) it.dueAt else it.createdAt }))

/** Every done item, most recently checked off first — [JsonDerivedRepository.setDone] bumps [TodoItem.updatedAt]. */
internal fun completedByRecency(items: List<TodoItem>): List<TodoItem> =
    items.filter { it.done }.sortedByDescending { it.updatedAt }
