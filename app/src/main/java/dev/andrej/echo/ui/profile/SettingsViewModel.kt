package dev.andrej.echo.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.andrej.echo.ai.AiCardState
import dev.andrej.echo.data.DerivedRepository
import dev.andrej.echo.data.ImportDay
import dev.andrej.echo.data.SettingsStore
import dev.andrej.echo.data.StorageUsage
import dev.andrej.echo.data.TranscriptRepository
import dev.andrej.echo.speech.whisper.WhisperModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** [code] is a Whisper language code such as `sl`; empty means auto-detect. */
data class LanguageOption(val code: String, val label: String)

data class SettingsUiState(
    val languageCode: String = "",
    val languageLabel: String = "",
    val languages: List<LanguageOption> = emptyList(),
    val speechModel: WhisperModel? = null,
    val speechModels: List<WhisperModel> = emptyList(),
    val importDay: ImportDay? = null,
    val litertModelBytes: Long = 0,
    val autoAnalyze: Boolean = true,
    val detectSpeakers: Boolean = true,
    val wifiOnlyDownload: Boolean = false,
    val storage: StorageUsage = StorageUsage(emptyList()),
    val notesCount: Int = 0,
    val tasksCount: Int = 0,
) {
    val storageBytes: Long get() = storage.totalBytes
}

class SettingsViewModel(
    private val settings: SettingsStore,
    private val whisperLanguages: () -> List<String>,
    private val speechModels: List<WhisperModel> = emptyList(),
    private val currentSpeechModel: () -> WhisperModel? = { null },
    transcripts: TranscriptRepository,
    derived: DerivedRepository,
    val aiCardState: Flow<AiCardState>,
    private val litertModelBytes: Long,
    val onDownloadModel: () -> Unit,
    val onCancelDownload: () -> Unit,
    val onDeleteModel: () -> Unit,
    private val storageUsage: () -> StorageUsage,
    val onTestModelDownloadNotification: () -> Unit = {},
    private val onDeleteAllData: suspend () -> Unit = {},
    val onReplayOnboarding: () -> Unit = {},
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(fromSettings(notesCount = 0, tasksCount = 0))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refreshStorage()
        viewModelScope.launch {
            combine(transcripts.transcripts, derived.items) { t, d -> t.size to d.size }
                .collect { (notesCount, tasksCount) ->
                    _state.value = fromSettings(notesCount, tasksCount)
                        .copy(languages = _state.value.languages, storage = _state.value.storage)
                }
        }
        refreshLanguages()
    }

    private fun fromSettings(notesCount: Int, tasksCount: Int) = SettingsUiState(
        languageCode = settings.transcriptionLanguage.orEmpty(),
        languageLabel = settings.transcriptionLanguage?.let(::displayLanguage) ?: AUTO_DETECT,
        speechModel = currentSpeechModel(),
        speechModels = speechModels,
        importDay = settings.importDay,
        litertModelBytes = litertModelBytes,
        autoAnalyze = settings.autoAnalyze,
        detectSpeakers = settings.detectSpeakers,
        wifiOnlyDownload = settings.wifiOnlyDownload,
        notesCount = notesCount,
        tasksCount = tasksCount,
    )

    fun refreshStorage() {
        viewModelScope.launch {
            val usage = withContext(computeDispatcher) { storageUsage() }
            _state.value = _state.value.copy(storage = usage)
        }
    }

    private fun refreshLanguages() {
        viewModelScope.launch {
            val languages = withContext(computeDispatcher) {
                whisperLanguages().map { LanguageOption(it, displayLanguage(it)) }.sortedBy { it.label }
            }
            _state.value = _state.value.copy(languages = listOf(LanguageOption("", AUTO_DETECT)) + languages)
        }
    }

    /** Empty [code] means auto-detect. */
    fun setLanguage(code: String) {
        settings.transcriptionLanguage = code.ifEmpty { null }
        _state.value = _state.value.copy(
            languageCode = code,
            languageLabel = if (code.isEmpty()) AUTO_DETECT else displayLanguage(code),
        )
    }

    /** Takes effect from the next transcription, which downloads it if needed. */
    fun setSpeechModel(model: WhisperModel) {
        settings.speechModel = model.id
        _state.value = _state.value.copy(speechModel = model)
    }

    /** Null asks on every import. */
    fun setImportDay(day: ImportDay?) {
        settings.importDay = day
        _state.value = _state.value.copy(importDay = day)
    }

    fun setDetectSpeakers(value: Boolean) {
        settings.detectSpeakers = value
        _state.value = _state.value.copy(detectSpeakers = value)
    }

    fun setAutoAnalyze(value: Boolean) {
        settings.autoAnalyze = value
        _state.value = _state.value.copy(autoAnalyze = value)
    }

    fun setWifiOnlyDownload(value: Boolean) {
        settings.wifiOnlyDownload = value
        _state.value = _state.value.copy(wifiOnlyDownload = value)
    }


    fun deleteAllData() {
        viewModelScope.launch {
            onDeleteAllData()
            refreshStorage()
        }
    }
}

private const val AUTO_DETECT = "Auto-detect"

internal fun displayLanguage(code: String): String =
    java.util.Locale.forLanguageTag(code).getDisplayName(java.util.Locale.getDefault())
        .ifBlank { code }
        .replaceFirstChar { it.titlecase() }

internal fun formatBytes(bytes: Long): String {
    return when {
        bytes == 0L -> "0 MB"
        bytes < 100_000 -> "< 0.1 MB"
        bytes < 1_000_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        else -> "%.2f GB".format(bytes / 1_000_000_000.0)
    }
}
