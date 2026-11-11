package dev.andrej.echo.ai

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import dev.andrej.echo.data.Segment
import dev.andrej.echo.data.Transcript
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class AnalysisProgress(val transcriptId: String, val model: String, val fraction: Float)

/**
 * Owns the prompts and the parsing for every backend, so a new [LlmRunner] costs one class and
 * no prompt tuning. Never throws: a failed analysis leaves the transcript as it was.
 *
 * Summary and tasks are separate prompts, each short: the single combined prompt was ~850 tokens
 * of instructions before the transcript, and the 1.7B model followed little of it.
 */
class TranscriptAnalyzer(
    private val runners: suspend () -> LlmRunner,
    private val segments: suspend (transcriptId: String) -> List<Segment> = { emptyList() },
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val logDebug: (String) -> Unit = { Log.d(TAG, it) },
    private val logWarn: (String) -> Unit = { Log.w(TAG, it) },
) {

    /**
     * Pays the model's startup cost early. Worth ~19s on the LiteRT path, which would otherwise
     * land on the first analysis after a recording.
     */
    suspend fun warmup() {
        try {
            val runner = runners()
            if (runner.availability() == LlmAvailability.Ready) runner.warmup()
        } catch (e: Exception) {
            // The first analysis pays for it instead.
        }
    }

    suspend fun analyze(
        transcript: Transcript,
        onProgress: (AnalysisProgress) -> Unit = {},
    ): AnalysisOutcome {
        if (transcript.text.isBlank()) return AnalysisOutcome.Blocked(AnalysisBlock.EmptyTranscript)

        val runner = when (val ready = readyRunner()) {
            is ReadyRunner.Ready -> ready.runner
            is ReadyRunner.Blocked -> return AnalysisOutcome.Blocked(ready.reason)
        }

        val timed = segments(transcript.id)
        val lines = timed.map { it.text }.ifEmpty { sentences(transcript.text) }
        val chunks = chunkLines(lines, CHUNK_WORDS)

        val totalSteps = chunks.size * 3 + if (chunks.size > 1) 1 else 0
        var completedSteps = 0
        fun progress() = onProgress(AnalysisProgress(transcript.id, runner.modelName, completedSteps.toFloat() / totalSteps))
        fun completed() {
            completedSteps++
            progress()
        }
        progress()

        val recordedAt = Instant.ofEpochMilli(transcript.createdAt).atZone(zone).toLocalDateTime()
        // Deliberately today's wall-clock day, not recordedAt's: re-analysing a month-old note
        // should resolve its relative phrases against that note's own moment (the prompt),
        // but an item where nobody named a date is due now, not a month ago.
        val today = LocalDate.now(zone)

        return try {
            val parts = chunks.map { chunk ->
                generate(runner, buildSummaryPrompt(chunk.joinToString(" ") { it.value }), ::parseSummary).also { completed() }
            }
            val summary = parts.singleOrNull() ?: generate(runner, buildMergePrompt(parts), ::parseSummary).also { completed() }

            val items = chunks.flatMap { chunk ->
                val plans = generate(runner, buildIntentPrompt(chunk.joinToString(" ") { it.value }), ::parseIntent)
                completed()
                if (!plans) {
                    completed()
                    return@flatMap emptyList()
                }
                // A task whose text is only its time earns the retry; on the last attempt it is dropped.
                generate(runner, buildTasksPrompt(chunk, recordedAt)) { raw, last ->
                    val parsed = parseTasks(raw, zone, today, recordedAt) { line -> timed.getOrNull(line)?.startMs }
                        ?: return@generate null
                    val valid = parsed.filterNot { isScheduleOnly(it.text) }
                    if (valid.size < parsed.size) logWarn("task text holds only a date or time")
                    valid.takeIf { last || it.size == parsed.size }
                }.also { completed() }
            }

            val grounded = groundTasks(items, lines, timed.map { it.startMs }, recordedAt, zone)
            AnalysisOutcome.Success(Analysis(summary.title, summary.summary, grounded))
        } catch (e: StepFailed) {
            AnalysisOutcome.Blocked(e.reason)
        }
    }

    private suspend fun readyRunner(): ReadyRunner {
        val runner = runners()
        return when (val availability = runner.availability()) {
            LlmAvailability.Ready -> ReadyRunner.Ready(runner)
            is LlmAvailability.NeedsDownload ->
                ReadyRunner.Blocked(AnalysisBlock.WaitingForModel(availability.bytes))
            is LlmAvailability.Downloading ->
                // No total size on this branch — the live fraction lives in ModelStore.state.
                ReadyRunner.Blocked(AnalysisBlock.WaitingForModel(0))
            is LlmAvailability.Unsupported ->
                ReadyRunner.Blocked(AnalysisBlock.NoBackend(availability.reason))
        }
    }

    /** One prompt, retried once at zero temperature when it throws or its answer does not parse. */
    private suspend fun <T : Any> generate(runner: LlmRunner, prompt: String, parse: (String) -> T?): T =
        generate(runner, prompt) { raw, _ -> parse(raw) }

    private suspend fun <T : Any> generate(runner: LlmRunner, prompt: String, parse: (raw: String, last: Boolean) -> T?): T {
        var failure: AnalysisBlock = AnalysisBlock.EmptyResult
        val temperatures = listOf(LlmRunner.DEFAULT_TEMPERATURE, 0f)
        for ((attempt, temperature) in temperatures.withIndex()) {
            currentCoroutineContext().ensureActive()
            val raw = try {
                runner.generate(prompt, temperature)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logWarn("generate() failed at temperature=$temperature: ${e.message}")
                failure = AnalysisBlock.GenerationFailed(e.message)
                continue
            }
            // A native backend may return normally even though Stop arrived during generation.
            currentCoroutineContext().ensureActive()
            logDebug("raw output (temperature=$temperature): $raw")

            parse(raw, attempt == temperatures.lastIndex)?.let {
                logDebug("parsed: $it")
                return it
            }
            logWarn("could not parse the raw output above")
            failure = AnalysisBlock.EmptyResult
        }
        throw StepFailed(failure)
    }

    private sealed interface ReadyRunner {
        data class Ready(val runner: LlmRunner) : ReadyRunner
        data class Blocked(val reason: AnalysisBlock) : ReadyRunner
    }

    private class StepFailed(val reason: AnalysisBlock) : Exception()

    private companion object {
        const val TAG = "TranscriptAnalyzer"

        /**
         * Words, not tokens: there is no tokenizer on this side of the interface. About 1.4
         * tokens a word puts a chunk near 1700 of the model's 4096, which leaves the task
         * prompt's instructions and examples room, and its answer.
         */
        const val CHUNK_WORDS = 1200
    }
}
