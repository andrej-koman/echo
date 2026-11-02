package dev.andrej.echo.ui.record

import androidx.lifecycle.ViewModel
import dev.andrej.echo.RecordingController
import kotlinx.coroutines.flow.StateFlow

data class RecordUiState(
    /** A take is open, recording or paused. */
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    /** Paused by a phone call rather than the user; resumes on its own when the call ends. */
    val interrupted: Boolean = false,
    val micFailed: Boolean = false,
    /** Audio actually captured, so pauses don't count. */
    val elapsedMs: Long = 0,
    /** Microphone loudness, 0f..1f. */
    val level: Float = 0f,
)

/**
 * Thin wrapper over [RecordingController], the app-scoped singleton that actually owns the
 * recording session — so it also reflects a recording started from the notification/[dev.andrej.echo.RecordingService].
 */
class RecordViewModel(
    private val controller: RecordingController,
    /** Puts the take under the foreground service, so it keeps the mic when the app is left. */
    private val onStarted: () -> Unit = {},
    private val warmup: () -> Unit = {},
) : ViewModel() {

    val uiState: StateFlow<RecordUiState> = controller.state

    init {
        warmup()
    }

    fun startRecording() {
        controller.startRecording()
        onStarted()
    }

    fun pause() = controller.pause()

    fun resume() = controller.resume()

    fun stopRecording() = controller.stopRecording()

    /** Stops without filing anything away. */
    fun discard() = controller.discard()
}
