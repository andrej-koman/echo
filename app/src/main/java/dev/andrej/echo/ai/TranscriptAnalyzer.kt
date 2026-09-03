package dev.andrej.echo.ai

import dev.andrej.echo.data.Transcript
import java.time.Instant
import java.time.ZoneId

/**
 * Owns the prompt and the parsing for every backend, so a new [LlmRunner] costs one class and
 * no prompt tuning. Never throws: a failed analysis leaves the transcript as it was.
 */
class TranscriptAnalyzer(
    private val runner: LlmRunner,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    suspend fun analyze(transcript: Transcript): Analysis? {
        if (transcript.text.isBlank()) return null
        if (runner.availability() != LlmAvailability.Ready) return null

        val today = Instant.ofEpochMilli(transcript.createdAt).atZone(zone).toLocalDate()
        val prompt = buildAnalysisPrompt(transcript.text, today)

        return attempt(prompt, LlmRunner.DEFAULT_TEMPERATURE)
            ?: attempt(prompt, temperature = 0f)
    }

    private suspend fun attempt(prompt: String, temperature: Float): Analysis? {
        val raw = try {
            runner.generate(prompt, temperature)
        } catch (e: Exception) {
            return null
        }
        return parseAnalysis(raw, zone)?.takeIf { it.hasContent }
    }
}

private val Analysis.hasContent: Boolean
    get() = title != null || summary != null || tasks.isNotEmpty() || reminders.isNotEmpty()
