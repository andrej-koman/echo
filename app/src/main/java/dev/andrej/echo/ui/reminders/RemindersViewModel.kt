package dev.andrej.echo.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.andrej.echo.data.DerivedRepository
import dev.andrej.echo.data.Reminder
import dev.andrej.echo.data.TranscriptRepository
import dev.andrej.echo.ui.transcripts.title
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ReminderGroup(
    val sourceTranscriptId: String,
    val sourceTitle: String,
    val reminders: List<Reminder>,
)

data class RemindersUiState(
    val groups: List<ReminderGroup> = emptyList(),
    val loaded: Boolean = false,
)

class RemindersViewModel(
    derived: DerivedRepository,
    repository: TranscriptRepository,
) : ViewModel() {

    val state: StateFlow<RemindersUiState> =
        combine(derived.reminders, repository.transcripts) { reminders, transcripts ->
            // derived.reminders is already sorted soonest-first, nulls last — preserve that
            // order across groups rather than re-sorting by group.
            val groups = LinkedHashMap<String, MutableList<Reminder>>()
            reminders.forEach { groups.getOrPut(it.sourceTranscriptId) { mutableListOf() }.add(it) }

            RemindersUiState(
                groups = groups.map { (transcriptId, items) ->
                    val transcript = transcripts.firstOrNull { it.id == transcriptId }
                    ReminderGroup(
                        sourceTranscriptId = transcriptId,
                        sourceTitle = transcript?.title ?: transcript?.let { title(it.text) } ?: "Recording",
                        reminders = items,
                    )
                },
                loaded = true,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersUiState())
}
