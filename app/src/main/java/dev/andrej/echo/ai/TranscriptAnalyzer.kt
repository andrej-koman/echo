package dev.andrej.echo.ai

import dev.andrej.echo.data.Transcript
import java.time.Instant
import java.time.ZoneId

/**
 * Owns the prompt and the parsing for every backend, so a new [LlmRunner] costs one class and
 * no prompt tuning. Never throws: a failed analysis leaves the transcript as it was.
 */
class TranscriptAnalyzer(
    private val runners: suspend () -> LlmRunner,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    /**
     * Pays the model's startup cost early. Worth ~19s on the LiteRT path, which would otherwise
     * land on the Processing screen after the user stops recording.
     */
    suspend fun warmup() {
        try {
            val runner = runners()
            if (runner.availability() == LlmAvailability.Ready) runner.warmup()
        } catch (e: Exception) {
            // The first analysis pays for it instead.
        }
    }

    suspend fun analyze(transcript: Transcript): Analysis? {
        if (transcript.text.isBlank()) return null

        val runner = runners()
        if (runner.availability() != LlmAvailability.Ready) return null

        val today = Instant.ofEpochMilli(transcript.createdAt).atZone(zone).toLocalDate()
        val prompt = buildAnalysisPrompt(transcript.text, today)

        return attempt(runner, prompt, LlmRunner.DEFAULT_TEMPERATURE)
            ?: attempt(runner, prompt, temperature = 0f)
    }

    private suspend fun attempt(
        runner: LlmRunner,
        prompt: String,
        temperature: Float,
    ): Analysis? {
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
