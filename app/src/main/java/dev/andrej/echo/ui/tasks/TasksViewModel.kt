package dev.andrej.echo.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.andrej.echo.ai.AiCardState
import dev.andrej.echo.data.DerivedRepository
import dev.andrej.echo.data.Task
import dev.andrej.echo.data.TranscriptRepository
import dev.andrej.echo.ui.transcripts.title
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TaskGroup(
    val sourceTranscriptId: String,
    val sourceTitle: String,
    val tasks: List<Task>,
)

data class TasksUiState(
    val groups: List<TaskGroup> = emptyList(),
    val loaded: Boolean = false,
    /** No usable backend and nothing to show — the empty state should point at AccountScreen. */
    val aiOff: Boolean = false,
)

class TasksViewModel(
    private val derived: DerivedRepository,
    repository: TranscriptRepository,
    aiCardState: Flow<AiCardState>,
) : ViewModel() {

    val state: StateFlow<TasksUiState> =
        combine(derived.tasks, repository.transcripts, aiCardState) { tasks, transcripts, ai ->
            val groups = tasks
                .groupBy { it.sourceTranscriptId }
                .map { (transcriptId, group) ->
                    val transcript = transcripts.firstOrNull { it.id == transcriptId }
                    TaskGroup(
                        sourceTranscriptId = transcriptId,
                        sourceTitle = transcript?.title ?: transcript?.let { title(it.text) } ?: "Recording",
                        tasks = group,
                    )
                }
                .sortedByDescending { group -> group.tasks.maxOf { it.createdAt } }

            TasksUiState(
                groups = groups,
                loaded = true,
                aiOff = tasks.isEmpty() && ai !is AiCardState.NanoReady && ai !is AiCardState.LiteRtReady,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    fun setDone(taskId: String, done: Boolean) {
        viewModelScope.launch { derived.setDone(taskId, done) }
    }
}
