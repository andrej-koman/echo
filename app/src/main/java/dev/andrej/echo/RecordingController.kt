package dev.andrej.echo

import dev.andrej.echo.data.TranscriptRepository
import dev.andrej.echo.speech.Interruptions
import dev.andrej.echo.speech.Recorder
import dev.andrej.echo.speech.SAMPLE_RATE
import dev.andrej.echo.ui.record.RecordUiState
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the one recording session the app can ever have, on [scope] rather than any
 * ViewModel/Service lifecycle, so it survives leaving [dev.andrej.echo.ui.capture.CaptureScreen]
 * and can be driven equally from there or from [RecordingService].
 *
 * A take is recorded to [audioFile] under a fresh id; only on stop does it become a transcript,
 * handed to [onRecorded] for transcription.
 */
class RecordingController(
    private val recorder: Recorder,
    private val interruptions: Interruptions,
    private val repository: TranscriptRepository,
    private val audioFile: (id: String) -> File,
    private val onRecorded: (id: String) -> Unit,
    private val onDiscarded: (id: String) -> Unit,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val _state = MutableStateFlow(RecordUiState())
    val state: StateFlow<RecordUiState> = _state.asStateFlow()

    private class Take(val id: String, val file: File, val startedAt: Long)

    private var take: Take? = null
    private var recordJob: Job? = null

    fun startRecording() {
        if (take != null) return
        val id = UUID.randomUUID().toString()
        take = Take(id, audioFile(id), clock())
        _state.value = RecordUiState(isRecording = true)
        interruptions.start(
            onInterrupted = { pause(interrupted = true) },
            onOver = { if (_state.value.interrupted) resume() },
        )
        capture()
    }

    private fun capture() {
        val file = take?.file ?: return
        // A paused capture may still be finishing its last read; two writers would interleave.
        val previous = recordJob
        recordJob = scope.launch {
            previous?.cancelAndJoin()
            try {
                recorder.record(file) { level, samples ->
                    _state.update { it.copy(level = level, elapsedMs = samples * 1000 / SAMPLE_RATE) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // What was recorded so far stays; the take sits paused until the mic is back.
                _state.update { it.copy(isPaused = true, level = 0f, micFailed = true) }
            }
        }
    }

    fun pause() = pause(interrupted = false)

    private fun pause(interrupted: Boolean) {
        val state = _state.value
        if (!state.isRecording || state.isPaused) return
        recordJob?.cancel()
        _state.value = state.copy(isPaused = true, interrupted = interrupted, level = 0f)
    }

    fun resume() {
        val state = _state.value
        if (!state.isRecording || !state.isPaused) return
        _state.value = state.copy(isPaused = false, interrupted = false, micFailed = false)
        capture()
    }

    fun stopRecording() {
        val take = take ?: return
        val job = end()
        scope.launch {
            job?.join()
            val samples = take.file.length() / 2
            if (samples == 0L) {
                onDiscarded(take.id)
                return@launch
            }
            // The transcript row exists from here on, so no transcription failure can lose the take.
            repository.save(
                id = take.id,
                text = "",
                language = "",
                durationMs = samples * 1000 / SAMPLE_RATE,
                createdAt = take.startedAt,
            )
            onRecorded(take.id)
        }
    }

    /** Stops without filing anything away. */
    fun discard() {
        val take = take ?: return
        val job = end()
        scope.launch {
            job?.join()
            onDiscarded(take.id)
        }
    }

    private fun end(): Job? {
        val job = recordJob
        job?.cancel()
        recordJob = null
        take = null
        interruptions.stop()
        _state.value = RecordUiState()
        return job
    }
}
