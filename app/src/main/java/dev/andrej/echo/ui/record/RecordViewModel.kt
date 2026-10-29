package dev.andrej.echo.ui.record

import androidx.lifecycle.ViewModel
import dev.andrej.echo.RecordingController
import dev.andrej.echo.speech.Availability
import dev.andrej.echo.speech.FailureReason
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow

/** @param installed false means the device supports it but the model must be downloaded first. */
data class LanguageOption(
    val tag: String,
    val label: String,
    val installed: Boolean,
)

data class RecordUiState(
    val isRecording: Boolean = false,
    /** Set between stopping and the transcript being filed away. */
    val isProcessing: Boolean = false,
    /** Wall clock at which the current take began. The screen ticks its own timer off this. */
    val startedAtMillis: Long = 0,
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

/**
 * Thin wrapper over [RecordingController], the app-scoped singleton that actually owns the
 * recording session — so it also reflects a recording started from the notification/[RecordingService].
 */
class RecordViewModel(
    private val controller: RecordingController,
    private val warmup: () -> Unit = {},
) : ViewModel() {

    val uiState: StateFlow<RecordUiState> = controller.state

    init {
        controller.refreshLanguages()
        warmup()
    }

    fun refreshLanguages() = controller.refreshLanguages()

    fun startRecording() = controller.startRecording()

    fun stopRecording() = controller.stopRecording()

    /** Stops without filing anything away. */
    fun discard() = controller.discard()

    fun dismissError() = controller.dismissError()
}

internal fun String.displayName(): String =
    Locale.forLanguageTag(this).getDisplayName(Locale.getDefault()).ifBlank { this }
