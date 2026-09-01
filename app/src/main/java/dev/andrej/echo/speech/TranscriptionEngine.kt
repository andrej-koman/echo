package dev.andrej.echo.speech

import kotlinx.coroutines.flow.Flow

/**
 * A source of live speech-to-text.
 *
 * The app talks to this interface only, never to a concrete recognizer. That keeps the
 * platform-specific quirks in one place and leaves room for a different engine later
 * (an on-device Whisper build, for example) without changing any UI code.
 */
interface TranscriptionEngine {

    /** Whether this engine can run right now, and if not, why. */
    fun availability(): Availability

    /**
     * Which languages this device can actually transcribe.
     *
     * Asking the device rather than hardcoding locales matters: a phone may support en-US
     * while only having the en-GB model downloaded, and may not support a language at all.
     */
    suspend fun languageSupport(): LanguageSupport

    /**
     * Asks the system to download the model for [language].
     *
     * Fire and forget: the download runs in the background and shows up as a newly installed
     * language the next time [languageSupport] is called.
     */
    fun requestModelDownload(language: String)

    /**
     * Starts listening and emits events until the collecting coroutine is cancelled.
     *
     * The flow is expected to keep running across natural pauses in speech: a silence is
     * not the end of a session.
     */
    fun transcribe(language: String): Flow<TranscriptionEvent>
}

/**
 * @param installed languages with a model already on the device — usable right now.
 * @param supported languages the device can transcribe once their model is downloaded.
 */
data class LanguageSupport(
    val installed: List<String>,
    val supported: List<String>,
) {
    /** Everything offerable, installed first, with no duplicates. */
    val all: List<String> = installed + supported.filterNot { it in installed }
}

sealed interface Availability {
    data object Available : Availability

    /** No speech recognition service is installed on this device. */
    data object NoRecognizer : Availability

    /** The recognizer exists but has no model for the requested language. */
    data class LanguageUnavailable(val language: String) : Availability
}

sealed interface TranscriptionEvent {

    /** A best guess at what is being said right now. Replaces the previous partial. */
    data class Partial(val text: String) : TranscriptionEvent

    /** A settled segment of speech. Appended to everything said so far. */
    data class Final(val text: String) : TranscriptionEvent

    /** Current microphone loudness, for the level meter. Roughly -2f..10f from Android. */
    data class Level(val rms: Float) : TranscriptionEvent

    /** Something went wrong badly enough to stop transcribing. */
    data class Failed(val reason: FailureReason) : TranscriptionEvent
}

enum class FailureReason {
    /** RECORD_AUDIO was not granted, or was revoked mid-session. */
    MissingPermission,

    /** No speech model available for the chosen language. */
    LanguageUnavailable,

    /** The recognizer is busy, or the device is out of resources. */
    RecognizerBusy,

    /** Anything else, including undocumented OEM behaviour. */
    Unknown,
}
