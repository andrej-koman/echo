package dev.andrej.echo.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.andrej.echo.ai.AiCardState
import dev.andrej.echo.data.DerivedRepository
import dev.andrej.echo.data.SettingsStore
import dev.andrej.echo.data.TranscriptRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class SettingsUiState(
    val languageLabel: String = "",
    val autoAnalyze: Boolean = true,
    val wifiOnlyDownload: Boolean = false,
    val reminderLeadMinutes: Int = 15,
    val reminderMorningHour: Int = 9,
    val storageBytes: Long = 0,
    val notesCount: Int = 0,
    val tasksCount: Int = 0,
)

class SettingsViewModel(
    private val settings: SettingsStore,
    transcripts: TranscriptRepository,
    derived: DerivedRepository,
    val aiCardState: Flow<AiCardState>,
    val onDownloadModel: () -> Unit,
    val onCancelDownload: () -> Unit,
    val onDeleteModel: () -> Unit,
    private val storageUsedBytes: () -> Long,
    private val deleteAllRecordings: suspend () -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(fromSettings(notesCount = 0, tasksCount = 0))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(transcripts.transcripts, derived.items) { t, d -> t.size to d.size }
                .collect { (notesCount, tasksCount) ->
                    _state.value = fromSettings(notesCount, tasksCount)
                }
        }
    }

    private fun fromSettings(notesCount: Int, tasksCount: Int) = SettingsUiState(
        languageLabel = settings.languageTag?.let(::displayLanguage) ?: "Not set",
        autoAnalyze = settings.autoAnalyze,
        wifiOnlyDownload = settings.wifiOnlyDownload,
        reminderLeadMinutes = settings.reminderLeadMinutes,
        reminderMorningHour = settings.reminderMorningHour,
        storageBytes = storageUsedBytes(),
        notesCount = notesCount,
        tasksCount = tasksCount,
    )

    fun setAutoAnalyze(value: Boolean) {
        settings.autoAnalyze = value
        _state.value = _state.value.copy(autoAnalyze = value)
    }

    fun setWifiOnlyDownload(value: Boolean) {
        settings.wifiOnlyDownload = value
        _state.value = _state.value.copy(wifiOnlyDownload = value)
    }

    fun setReminderLeadMinutes(value: Int) {
        settings.reminderLeadMinutes = value
        _state.value = _state.value.copy(reminderLeadMinutes = value)
    }

    fun setReminderMorningHour(value: Int) {
        settings.reminderMorningHour = value
        _state.value = _state.value.copy(reminderMorningHour = value)
    }

    fun deleteAllRecordingsNow() {
        viewModelScope.launch { deleteAllRecordings() }
    }
}

internal fun displayLanguage(tag: String): String =
    java.util.Locale.forLanguageTag(tag).getDisplayName(java.util.Locale.getDefault()).ifBlank { tag }

internal fun formatBytes(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    return "%.1f MB".format(mb)
}
