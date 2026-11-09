package dev.andrej.echo.ui.record

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import dev.andrej.echo.RecordingController
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import dev.andrej.echo.transcribe.FREE_SAMPLES
import dev.andrej.echo.speech.SAMPLE_RATE

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
    isPro: StateFlow<Boolean> = MutableStateFlow(false),
) : ViewModel() {

    val uiState: StateFlow<RecordUiState> = controller.state

    private val upgrade = MutableStateFlow(false)
    val upgradeRequested = upgrade.asStateFlow()
    private var prompted = false

    init {
        viewModelScope.launch {
            combine(uiState, isPro) { recording, pro -> recording to pro }.collect { (recording, pro) ->
                if (!recording.isRecording) {
                    prompted = false
                    upgrade.value = false
                } else if (!pro && !prompted && recording.elapsedMs > FREE_SAMPLES * 1000L / SAMPLE_RATE) {
                    prompted = true
                    upgrade.value = true
                }
            }
        }
    }

    fun consumeUpgrade() { upgrade.value = false }

    fun startRecording() {
        warmup()
        controller.startRecording()
        onStarted()
    }

    fun pause() = controller.pause()

    fun resume() = controller.resume()

    fun stopRecording() = controller.stopRecording()

    /** Stops without filing anything away. */
    fun discard() = controller.discard()
}
