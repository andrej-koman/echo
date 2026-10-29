package dev.andrej.echo.speech

import kotlinx.coroutines.flow.Flow

interface TranscriptionEngine {

    fun availability(): Availability

    suspend fun languageSupport(): LanguageSupport

    fun requestModelDownload(language: String)

    fun transcribe(language: String): Flow<TranscriptionEvent>
}

/**
 * @param installed languages with a model on the device, usable now.
 * @param supported languages usable once their model is downloaded.
 */
data class LanguageSupport(
    val installed: List<String>,
    val supported: List<String>,
) {
    val all: List<String> = installed + supported.filterNot { it in installed }

    /**
     * The language pack to fetch so the first recording works offline: none if one is already
     * installed, else the device's own tag, else the first supported tag in the device's language.
     */
    fun toPrefetch(deviceTag: String): String? {
        if (installed.isNotEmpty()) return null
        if (deviceTag in supported) return deviceTag
        val language = deviceTag.substringBefore('-')
        return supported.firstOrNull { it.substringBefore('-') == language }
    }
}

sealed interface Availability {
    data object Available : Availability
    data object NoRecognizer : Availability
}

sealed interface TranscriptionEvent {
    /** Best guess at what is being said right now. Replaces the previous partial. */
    data class Partial(val text: String) : TranscriptionEvent

    /** A settled segment. Appended to what came before. */
    data class Final(val text: String) : TranscriptionEvent

    /** Microphone loudness in dB, roughly -2f..10f. */
    data class Level(val rms: Float) : TranscriptionEvent

    data class Failed(val reason: FailureReason) : TranscriptionEvent
}

enum class FailureReason {
    MissingPermission,
    LanguageUnavailable,
    RecognizerBusy,
    Unknown,
}
