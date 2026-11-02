package dev.andrej.echo.ai

import android.util.Log
import dev.andrej.echo.data.Segment
import dev.andrej.echo.data.Transcript
import dev.andrej.echo.data.TranscriptRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Runs on demand, not after every transcription: it rewrites the whole transcript, one model
 * call per chunk, where analysis only reads it. On the application scope, like analysis, so
 * leaving the note does not cancel it.
 */
class TranscriptPolisher(
    private val runners: suspend () -> LlmRunner,
    private val transcripts: TranscriptRepository,
    private val segments: suspend (transcriptId: String) -> List<Segment>,
    private val scope: CoroutineScope,
    private val logWarn: (String) -> Unit = { Log.w(TAG, it) },
) {

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    private val _failed = MutableStateFlow<Set<String>>(emptySet())

    /** Notes whose last polish produced nothing usable. Cleared by the next attempt. */
    val failed: StateFlow<Set<String>> = _failed.asStateFlow()

    fun polishAsync(transcriptId: String) {
        if (_activeId.value != null) return
        _activeId.value = transcriptId
        scope.launch {
            try {
                _failed.update { it - transcriptId }
                val transcript = transcripts.transcripts.first().firstOrNull { it.id == transcriptId }
                    ?: return@launch
                val polished = polish(transcript)
                if (polished == null) {
                    _failed.update { it + transcriptId }
                } else {
                    transcripts.setPolished(transcriptId, polished)
                }
            } finally {
                _activeId.value = null
            }
        }
    }

    suspend fun polish(transcript: Transcript): String? {
        val runner = runners()
        if (runner.availability() != LlmAvailability.Ready) return null

        val lines = segments(transcript.id).map { it.text }.ifEmpty { sentences(transcript.text) }
        val parts = chunkLines(lines, CHUNK_WORDS).map { chunk ->
            polishChunk(runner, chunk.joinToString(" ") { it.value }) ?: return null
        }
        return parts.joinToString("\n\n")
    }

    private suspend fun polishChunk(runner: LlmRunner, text: String): String? {
        for (temperature in listOf(LlmRunner.DEFAULT_TEMPERATURE, 0f)) {
            val raw = try {
                runner.generate(buildPolishPrompt(text), temperature)
            } catch (e: Exception) {
                logWarn("generate() failed at temperature=$temperature: ${e.message}")
                continue
            }
            parsePolish(raw, text)?.let { return it }
            logWarn("implausible polish at temperature=$temperature: $raw")
        }
        return null
    }

    private companion object {
        const val TAG = "TranscriptPolisher"

        /** The answer is as long as the chunk, so the chunk gets under half the 4096 tokens. */
        const val CHUNK_WORDS = 900
    }
}
