package dev.andrej.echo.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.andrej.echo.ai.AiCardState
import dev.andrej.echo.ai.LlmBackend
import dev.andrej.echo.data.DerivedRepository
import dev.andrej.echo.data.SettingsStore
import dev.andrej.echo.data.TranscriptRepository
import dev.andrej.echo.speech.TranscriptionEngine
import dev.andrej.echo.sync.SyncStatus
import dev.andrej.echo.ui.record.LanguageOption
import dev.andrej.echo.ui.record.displayName
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

data class SettingsUiState(
    val languageTag: String? = null,
    val languageLabel: String = "",
    val languages: List<LanguageOption> = emptyList(),
    val preferredLlmBackendId: String? = null,
    val litertModelBytes: Long = 0,
    val autoAnalyze: Boolean = true,
    val wifiOnlyDownload: Boolean = false,
    val reminderLeadMinutes: Int = 15,
    val reminderMorningHour: Int = 9,
    val reminderMorningMinute: Int = 0,
    val snoozeMinutes: Int = 60,
    val storageBytes: Long = 0,
    val notesCount: Int = 0,
    val tasksCount: Int = 0,
    val isSignedIn: Boolean = false,
    val syncEnabled: Boolean = false,
    val syncing: Boolean = false,
    val lastSyncedLabel: String? = null,
)

class SettingsViewModel(
    private val settings: SettingsStore,
    private val engine: TranscriptionEngine,
    transcripts: TranscriptRepository,
    derived: DerivedRepository,
    val aiCardState: Flow<AiCardState>,
    private val onSetLlmBackend: (LlmBackend) -> Unit,
    private val litertModelBytes: Long,
    val onDownloadModel: () -> Unit,
    val onCancelDownload: () -> Unit,
    val onDeleteModel: () -> Unit,
    private val storageUsedBytes: () -> Long,
    val onTestReminderNotification: () -> Unit = {},
    val onTestDailyBriefNotification: () -> Unit = {},
    val onTestModelDownloadNotification: () -> Unit = {},
    isSignedIn: Flow<Boolean> = flowOf(false),
    private val onSetSyncEnabled: (Boolean) -> Unit = {},
    syncStatus: Flow<SyncStatus> = flowOf(SyncStatus(syncing = false, lastSyncedAt = null)),
    private val onSyncNow: () -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow(fromSettings(notesCount = 0, tasksCount = 0))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(transcripts.transcripts, derived.items) { t, d -> t.size to d.size }
                .collect { (notesCount, tasksCount) ->
                    _state.value = fromSettings(notesCount, tasksCount)
                        .copy(languages = _state.value.languages, isSignedIn = _state.value.isSignedIn)
                }
        }
        viewModelScope.launch {
            isSignedIn.collect { signedIn -> _state.value = _state.value.copy(isSignedIn = signedIn) }
        }
        viewModelScope.launch {
            syncStatus.collect { status ->
                _state.value = _state.value.copy(
                    syncing = status.syncing,
                    lastSyncedLabel = formatLastSynced(status.lastSyncedAt),
                )
            }
        }
        refreshLanguages()
    }

    private fun fromSettings(notesCount: Int, tasksCount: Int) = SettingsUiState(
        languageTag = settings.languageTag,
        languageLabel = settings.languageTag?.let(::displayLanguage) ?: "Not set",
        preferredLlmBackendId = settings.preferredLlmBackend,
        litertModelBytes = litertModelBytes,
        autoAnalyze = settings.autoAnalyze,
        wifiOnlyDownload = settings.wifiOnlyDownload,
        reminderLeadMinutes = settings.reminderLeadMinutes,
        reminderMorningHour = settings.reminderMorningHour,
        reminderMorningMinute = settings.reminderMorningMinute,
        snoozeMinutes = settings.snoozeMinutes,
        storageBytes = storageUsedBytes(),
        notesCount = notesCount,
        tasksCount = tasksCount,
        syncEnabled = settings.syncEnabled,
    )

    private fun refreshLanguages() {
        viewModelScope.launch {
            val support = engine.languageSupport()
            val options = support.all.map { tag ->
                LanguageOption(tag = tag, label = tag.displayName(), installed = tag in support.installed)
            }
            _state.value = _state.value.copy(languages = options)
        }
    }

    fun setLanguage(tag: String) {
        val option = _state.value.languages.firstOrNull { it.tag == tag } ?: return
        settings.languageTag = tag
        _state.value = _state.value.copy(languageTag = tag, languageLabel = displayLanguage(tag))
        if (!option.installed) {
            engine.requestModelDownload(tag)
        }
    }

    fun setLlmBackend(backend: LlmBackend) {
        onSetLlmBackend(backend)
        _state.value = _state.value.copy(preferredLlmBackendId = backend.id)
    }

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

    fun setReminderMorningTime(hour: Int, minute: Int) {
        settings.reminderMorningHour = hour
        settings.reminderMorningMinute = minute
        _state.value = _state.value.copy(reminderMorningHour = hour, reminderMorningMinute = minute)
    }

    fun setSnoozeMinutes(value: Int) {
        settings.snoozeMinutes = value
        _state.value = _state.value.copy(snoozeMinutes = value)
    }

    fun setSyncEnabled(value: Boolean) {
        onSetSyncEnabled(value)
        _state.value = _state.value.copy(syncEnabled = value)
    }

    fun syncNow() {
        onSyncNow()
    }

}

internal fun displayLanguage(tag: String): String =
    java.util.Locale.forLanguageTag(tag).getDisplayName(java.util.Locale.getDefault()).ifBlank { tag }

internal fun formatBytes(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    return "%.1f MB".format(mb)
}

internal fun formatLastSynced(at: Long?): String? {
    if (at == null) return null
    val minutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - at)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} hr ago"
        else -> "${minutes / (24 * 60)} d ago"
    }
}
