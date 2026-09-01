package dev.andrej.echo.speech

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Replays scripted events, then stays open like a real engine until cancelled. */
class FakeTranscriptionEngine(
    private val script: List<TranscriptionEvent> = emptyList(),
    private val availability: Availability = Availability.Available,
    private var support: LanguageSupport = LanguageSupport(
        installed = listOf("en-GB"),
        supported = listOf("en-US", "de-DE"),
    ),
) : TranscriptionEngine {

    val requestedLanguages = mutableListOf<String>()

    val downloadRequests = mutableListOf<String>()

    override fun availability(): Availability = availability

    override suspend fun languageSupport(): LanguageSupport = support

    override fun requestModelDownload(language: String) {
        downloadRequests += language
    }

    fun completeDownload(language: String) {
        support = support.copy(installed = support.installed + language)
    }

    override fun transcribe(language: String): Flow<TranscriptionEvent> = flow {
        requestedLanguages += language
        script.forEach { emit(it) }
        CompletableDeferred<Unit>().await()
    }
}
