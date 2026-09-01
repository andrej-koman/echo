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

/**
 * One language the device offers.
 *
 * @param installed true when the model is already on the phone and can be used right away;
 *   false when the device supports it but the model still has to be downloaded.
 */
data class LanguageOption(
    val tag: String,
    val label: String,
    val installed: Boolean,
)

data class RecordUiState(
    val isRecording: Boolean = false,
    /** Speech that has settled and will not change. */
    val committedText: String = "",
    /** The recognizer's current guess, replaced as it refines. */
    val partialText: String = "",
    val level: Float = 0f,
    val languages: List<LanguageOption> = emptyList(),
    val language: LanguageOption? = null,
    /** Set while a language model download is in flight. */
    val downloadingLanguage: String? = null,
    val availability: Availability = Availability.Available,
    val error: FailureReason? = null,
) {
    /** What the user sees: settled words plus the guess currently in flight. */
    val displayText: String = listOf(committedText, partialText)
        .filter { it.isNotBlank() }
        .joinToString(" ")

    /** Recording is only possible once the chosen language's model is on the device. */
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

    /**
     * Asks the device which languages it can transcribe, and picks one that actually works.
     *
     * Worth calling again after a model download: the newly installed language only shows up
     * in a fresh support query.
     */
    fun refreshLanguages() {
        viewModelScope.launch {
            val support = engine.languageSupport()
            val options = support.all.map { tag ->
                LanguageOption(tag = tag, label = tag.displayName(), installed = tag in support.installed)
            }

            // Prefer the remembered language, but only if the device still offers it; otherwise
            // fall back to something installed so the app is usable without a download.
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

        // A language the device supports but has not downloaded is useless until the model
        // arrives, so ask the system for it rather than failing when the user hits record.
        if (!option.installed) {
            engine.requestModelDownload(tag)
            _uiState.value = _uiState.value.copy(downloadingLanguage = tag)
        }
    }

    fun startRecording() {
        if (listenJob?.isActive == true) return

        val language = _uiState.value.language ?: return
        if (!language.installed) {
            // Nothing to record with yet. The download was already requested in setLanguage.
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
                // Once a failure has stopped the session, ignore anything still in flight.
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

/** "en-GB" becomes "English (United Kingdom)" in the user's own language. */
private fun String.displayName(): String =
    Locale.forLanguageTag(this).getDisplayName(Locale.getDefault()).ifBlank { this }
