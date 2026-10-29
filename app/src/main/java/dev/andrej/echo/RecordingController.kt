package dev.andrej.echo

import dev.andrej.echo.ai.PendingAnalysisWorker
import dev.andrej.echo.data.AnalysisQueueRepository
import dev.andrej.echo.data.SettingsStore
import dev.andrej.echo.data.TranscriptRepository
import dev.andrej.echo.speech.TranscriptionEngine
import dev.andrej.echo.speech.TranscriptionEvent
import dev.andrej.echo.ui.record.LanguageOption
import dev.andrej.echo.ui.record.RecordUiState
import dev.andrej.echo.ui.record.displayName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the one recording session the app can ever have, on [scope] rather than any
 * ViewModel/Service lifecycle, so it survives leaving [dev.andrej.echo.ui.capture.CaptureScreen]
 * and can be driven equally from there or from [RecordingService].
 */
class RecordingController(
    private val engine: TranscriptionEngine,
    private val repository: TranscriptRepository,
    private val queue: AnalysisQueueRepository,
    private val worker: PendingAnalysisWorker,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val _state = MutableStateFlow(
        RecordUiState(availability = engine.availability()),
    )
    val state: StateFlow<RecordUiState> = _state.asStateFlow()

    private var listenJob: Job? = null
    private var startedAt: Long = 0

    fun refreshLanguages() {
        scope.launch {
            val support = engine.languageSupport()
            val options = support.all.map { tag ->
                LanguageOption(tag = tag, label = tag.displayName(), installed = tag in support.installed)
            }

            val remembered = options.firstOrNull { it.tag == settings.languageTag }
            val selected = remembered
                ?: options.firstOrNull { it.installed }
                ?: options.firstOrNull()

            _state.value = _state.value.copy(
                languages = options,
                language = selected,
                availability = engine.availability(),
                downloadingLanguage = _state.value.downloadingLanguage
                    ?.takeIf { tag -> options.none { it.tag == tag && it.installed } },
            )
        }
    }

    fun startRecording() {
        if (_state.value.isRecording) return

        val language = _state.value.language ?: return
        if (!language.installed) {
            engine.requestModelDownload(language.tag)
            _state.value = _state.value.copy(downloadingLanguage = language.tag)
            return
        }

        startedAt = clock()
        _state.value = _state.value.copy(
            isRecording = true,
            isProcessing = false,
            startedAtMillis = startedAt,
            committedText = "",
            partialText = "",
            error = null,
        )

        listenJob = scope.launch {
            engine.transcribe(language.tag).collect { event ->
                if (!_state.value.isRecording) return@collect

                _state.value = when (event) {
                    is TranscriptionEvent.Partial -> _state.value.copy(partialText = event.text)

                    is TranscriptionEvent.Final -> _state.value.copy(
                        committedText = append(_state.value.committedText, event.text),
                        partialText = "",
                    )

                    is TranscriptionEvent.Level -> _state.value.copy(level = event.rms)

                    is TranscriptionEvent.Failed -> _state.value.copy(
                        isRecording = false,
                        error = event.reason,
                        level = 0f,
                    )
                }
            }
        }
    }

    fun stopRecording() {
        val state = stop() ?: return
        val text = state.displayText.trim()

        _state.value = _state.value.copy(isProcessing = true)

        scope.launch {
            if (text.isNotBlank()) {
                // Saved before analysis so a missing, failing or unsupported model can only cost
                // the extras, never the recording itself. A failure past this point lands in the
                // queue instead of vanishing — worker.requestDrainAndAwait keeps today's
                // perceived flow (Processing screen holds for one attempt) while it does.
                val transcript = repository.save(
                    text = text,
                    language = state.language?.tag.orEmpty(),
                    durationMs = clock() - startedAt,
                )
                if (settings.autoAnalyze) {
                    queue.enqueue(transcript.id)
                    worker.requestDrainAndAwait()
                }
            }
            _state.value = _state.value.copy(isProcessing = false)
        }
    }

    /** Stops without filing anything away. */
    fun discard() {
        stop()
    }

    private fun stop(): RecordUiState? {
        listenJob?.cancel()
        listenJob = null

        val state = _state.value
        if (!state.isRecording) return null

        _state.value = state.copy(
            isRecording = false,
            committedText = "",
            partialText = "",
            level = 0f,
        )
        return state
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun append(existing: String, addition: String): String =
        if (existing.isBlank()) addition.trim() else "${existing.trim()} ${addition.trim()}"
}
