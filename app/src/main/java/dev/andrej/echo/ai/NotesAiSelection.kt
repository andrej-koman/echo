package dev.andrej.echo.ai

import dev.andrej.echo.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ModelChoice { Activated, NeedsDownload }

data class NotesAiModelStatus(
    val model: NotesAiModel,
    val state: ModelState,
    val active: Boolean,
    val pending: Boolean,
)

/** Persists the active model separately from a requested download, so failures never switch it. */
class NotesAiSelection(
    private val settings: SettingsStore,
    private val stores: Map<NotesAiModel, ModelStore>,
) {

    private val _active = MutableStateFlow(NotesAiModel.fromId(settings.notesAiModel?.removePrefix("none:")))
    val active: StateFlow<NotesAiModel> = _active.asStateFlow()

    fun statuses(states: List<ModelState>): List<NotesAiModelStatus> =
        NotesAiModel.entries.mapIndexed { index, model ->
            NotesAiModelStatus(
                model = model,
                state = states[index],
                active = settings.notesAiModel?.startsWith("none:") != true && _active.value == model,
                pending = settings.pendingNotesAiModel == model.id,
            )
        }

    init {
        val pending = NotesAiModel.entries.firstOrNull { it.id == settings.pendingNotesAiModel }
        if (pending != null && stores[pending]?.state?.value is ModelState.Ready) activate(pending)
    }

    fun choose(model: NotesAiModel): ModelChoice {
        if (stores[model]?.state?.value is ModelState.Ready) {
            activate(model)
            return ModelChoice.Activated
        }
        return ModelChoice.NeedsDownload
    }

    /** Called only after the user taps Download. */
    fun startDownload(model: NotesAiModel) {
        settings.pendingNotesAiModel = model.id
    }

    /** Completes a requested switch only after the complete file has reached disk. */
    fun modelStateChanged(model: NotesAiModel) {
        if (settings.pendingNotesAiModel == model.id) {
            when (stores[model]?.state?.value) {
                is ModelState.Ready -> activate(model)
                is ModelState.Failed -> downloadEnded(model)
                else -> Unit
            }
        }
    }

    /** A cancelled or failed download leaves the previous active choice intact. */
    fun downloadEnded(model: NotesAiModel) {
        if (settings.pendingNotesAiModel == model.id) settings.pendingNotesAiModel = null
    }

    fun disable() {
        settings.notesAiModel = "none:${_active.value.id}"
        settings.pendingNotesAiModel = null
    }

    fun modelRemoved(model: NotesAiModel) {
        if (settings.pendingNotesAiModel == model.id) settings.pendingNotesAiModel = null
        if (_active.value == model) {
            val available = NotesAiModel.entries.firstOrNull {
                it != model && stores[it]?.state?.value is ModelState.Ready
            }
            if (available != null) activate(available)
        }
    }

    private fun activate(model: NotesAiModel) {
        settings.notesAiModel = model.id
        settings.pendingNotesAiModel = null
        _active.value = model
    }
}
