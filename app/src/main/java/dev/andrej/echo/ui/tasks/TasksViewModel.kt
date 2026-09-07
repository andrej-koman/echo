package dev.andrej.echo.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.andrej.echo.ai.AiCardState
import dev.andrej.echo.data.DerivedRepository
import dev.andrej.echo.data.TodoItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DueGroup(
    val label: String,
    val items: List<TodoItem>,
)

data class TasksUiState(
    val groups: List<DueGroup> = emptyList(),
    val loaded: Boolean = false,
    /** No usable backend and nothing to show — the empty state should point at AccountScreen. */
    val aiOff: Boolean = false,
)

class TasksViewModel(
    private val derived: DerivedRepository,
    aiCardState: Flow<AiCardState>,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    val state: StateFlow<TasksUiState> =
        combine(derived.items, aiCardState) { items, ai ->
            TasksUiState(
                groups = groupByDue(items, now(), zone),
                loaded = true,
                aiOff = items.isEmpty() && ai !is AiCardState.NanoReady && ai !is AiCardState.LiteRtReady,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    fun setDone(itemId: String, done: Boolean) {
        viewModelScope.launch { derived.setDone(itemId, done) }
    }
}

private fun dayOf(epochMilli: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(epochMilli).atZone(zone).toLocalDate()

/** An exact-time item is overdue once its clock time passes; a date-only item, only the next day. */
internal fun isOverdue(item: TodoItem, now: Long, zone: ZoneId): Boolean =
    if (item.hasTime) item.dueAt < now else dayOf(item.dueAt, zone) < dayOf(now, zone)

private val groupLabelFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

/** Membership ignores [TodoItem.done] — a ticked overdue row stays under Overdue, struck through. */
internal fun groupByDue(items: List<TodoItem>, now: Long, zone: ZoneId): List<DueGroup> {
    val today = dayOf(now, zone)
    val tomorrow = today.plusDays(1)

    val (overdue, upcoming) = items.partition { isOverdue(it, now, zone) }

    val overdueGroup = if (overdue.isNotEmpty()) {
        listOf(DueGroup("Overdue", sortWithinGroup(overdue)))
    } else {
        emptyList()
    }

    val byDay = upcoming
        .groupBy { dayOf(it.dueAt, zone) }
        .toSortedMap()
        .map { (day, group) ->
            val label = when (day) {
                today -> "Today"
                tomorrow -> "Tomorrow"
                else -> groupLabelFormat.format(day)
            }
            DueGroup(label, sortWithinGroup(group))
        }

    return overdueGroup + byDay
}

/** Timed items first, soonest first; date-only items after, oldest-created first. */
private fun sortWithinGroup(items: List<TodoItem>): List<TodoItem> =
    items.sortedWith(compareBy({ !it.hasTime }, { if (it.hasTime) it.dueAt else it.createdAt }))
