package dev.andrej.echo.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.andrej.echo.data.SettingsStore
import dev.andrej.echo.data.TranscriptRepository
import dev.andrej.echo.speech.Availability
import dev.andrej.echo.speech.FailureReason
import dev.andrej.echo.speech.TranscriptionEngine
import dev.andrej.echo.speech.TranscriptionEvent
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** @param installed false means the device supports it but the model must be downloaded first. */
data class LanguageOption(
    val tag: String,
    val label: String,
    val installed: Boolean,
)

data class RecordUiState(
    val isRecording: Boolean = false,
    val committedText: String = "",
    val partialText: String = "",
    val level: Float = 0f,
    val languages: List<LanguageOption> = emptyList(),
    val language: LanguageOption? = null,
    val downloadingLanguage: String? = null,
    val availability: Availability = Availability.Available,
    val error: FailureReason? = null,
) {
    val displayText: String = listOf(committedText, partialText)
        .filter { it.isNotBlank() }
        .joinToString(" ")

    val canRecord: Boolean = availability is Availability.Available && language?.installed == true
}

class RecordViewModel(
    private val engine: TranscriptionEngine,
    private val repository: TranscriptRepository,
    private val settings: SettingsStore,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        RecordUiState(availability = engine.availability()),
    )
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    private var listenJob: Job? = null
    private var startedAt: Long = 0

    init {
        refreshLanguages()
    }

    fun refreshLanguages() {
        viewModelScope.launch {
            val support = engine.languageSupport()
            val options = support.all.map { tag ->
                LanguageOption(tag = tag, label = tag.displayName(), installed = tag in support.installed)
            }

            val remembered = options.firstOrNull { it.tag == settings.languageTag }
            val selected = remembered
                ?: options.firstOrNull { it.installed }
                ?: options.firstOrNull()

            _uiState.value = _uiState.value.copy(
                languages = options,
                language = selected,
                availability = engine.availability(),
                downloadingLanguage = _uiState.value.downloadingLanguage
                    ?.takeIf { tag -> options.none { it.tag == tag && it.installed } },
            )
        }
    }

    fun setLanguage(tag: String) {
        val option = _uiState.value.languages.firstOrNull { it.tag == tag } ?: return

        settings.languageTag = tag
        _uiState.value = _uiState.value.copy(language = option, error = null)

        if (!option.installed) {
            engine.requestModelDownload(tag)
            _uiState.value = _uiState.value.copy(downloadingLanguage = tag)
        }
    }

    fun startRecording() {
        if (listenJob?.isActive == true) return

        val language = _uiState.value.language ?: return
        if (!language.installed) {
            engine.requestModelDownload(language.tag)
            _uiState.value = _uiState.value.copy(downloadingLanguage = language.tag)
            return
        }

        startedAt = clock()
        _uiState.value = _uiState.value.copy(
            isRecording = true,
            committedText = "",
            partialText = "",
            error = null,
        )

        listenJob = viewModelScope.launch {
            engine.transcribe(language.tag).collect { event ->
                if (!_uiState.value.isRecording) return@collect

                _uiState.value = when (event) {
                    is TranscriptionEvent.Partial -> _uiState.value.copy(partialText = event.text)

                    is TranscriptionEvent.Final -> _uiState.value.copy(
                        committedText = append(_uiState.value.committedText, event.text),
                        partialText = "",
                    )

                    is TranscriptionEvent.Level -> _uiState.value.copy(level = event.rms)

                    is TranscriptionEvent.Failed -> _uiState.value.copy(
                        isRecording = false,
                        error = event.reason,
                        level = 0f,
                    )
                }
            }
        }
    }

    fun stopRecording() {
        listenJob?.cancel()
        listenJob = null

        val state = _uiState.value
        val text = state.displayText.trim()

        _uiState.value = state.copy(
            isRecording = false,
            committedText = "",
            partialText = "",
            level = 0f,
        )

        if (text.isBlank()) return

        viewModelScope.launch {
            repository.save(
                text = text,
                language = state.language?.tag.orEmpty(),
                durationMs = clock() - startedAt,
            )
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun append(existing: String, addition: String): String =
        if (existing.isBlank()) addition.trim() else "${existing.trim()} ${addition.trim()}"
}

private fun String.displayName(): String =
    Locale.forLanguageTag(this).getDisplayName(Locale.getDefault()).ifBlank { this }
