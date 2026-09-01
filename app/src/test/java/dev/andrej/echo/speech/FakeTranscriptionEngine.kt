package dev.andrej.echo.speech

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A [TranscriptionEngine] that replays a scripted list of events.
 *
 * Lets every layer above the engine be tested with no microphone, no device and no timing
 * games. After emitting its script the flow suspends forever, mirroring a real engine that
 * keeps listening until the collector is cancelled.
 */
class FakeTranscriptionEngine(
    private val script: List<TranscriptionEvent> = emptyList(),
    private val availability: Availability = Availability.Available,
    private var support: LanguageSupport = LanguageSupport(
        installed = listOf("en-GB"),
        supported = listOf("en-US", "de-DE"),
    ),
) : TranscriptionEngine {

    /** Languages passed to [transcribe], in call order. */
    val requestedLanguages = mutableListOf<String>()

    /** Languages passed to [requestModelDownload], in call order. */
    val downloadRequests = mutableListOf<String>()

    override fun availability(): Availability = availability

    override suspend fun languageSupport(): LanguageSupport = support

    override fun requestModelDownload(language: String) {
        downloadRequests += language
    }

    /** Pretends a background model download finished. */
    fun completeDownload(language: String) {
        support = support.copy(installed = support.installed + language)
    }

    override fun transcribe(language: String): Flow<TranscriptionEvent> = flow {
        requestedLanguages += language
        script.forEach { emit(it) }
        CompletableDeferred<Unit>().await() // keep listening until cancelled
    }
}
