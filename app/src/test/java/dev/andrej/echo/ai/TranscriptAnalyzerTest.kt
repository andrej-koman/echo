package dev.andrej.echo.ai

import dev.andrej.echo.data.Segment
import dev.andrej.echo.data.Transcript
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val UTC = ZoneId.of("UTC")

class TranscriptAnalyzerTest {

    private val summary = """{"title":"Fix the tap","summary":"It drips."}"""
    private val tasks = """{"items":[{"text":"Buy a washer","due":null,"line":1}]}"""
    private val plans = """{"plans":true}"""

    private fun transcript(text: String = "the kitchen tap drips. buy a washer.") = Transcript(
        id = "t1",
        text = text,
        language = "en",
        createdAt = LocalDate.of(2026, 9, 3).atStartOfDay(UTC).toInstant().toEpochMilli(),
        durationMs = 4_000,
    )

    private fun analyzer(runner: FakeLlmRunner, segments: List<Segment> = emptyList()) =
        TranscriptAnalyzer({ runner }, { segments }, UTC, logDebug = {}, logWarn = {})

    @Test
    fun `analysis reports progress only after successful steps including skipped tasks`() = runTest {
        val progress = mutableListOf<Float>()
        val runner = FakeLlmRunner(listOf("invalid", summary, plans, tasks))
        val outcome = analyzer(runner).analyze(transcript(), onProgress = { progress += it.fraction })
        assertTrue(outcome is AnalysisOutcome.Success)
        assertEquals(listOf(0f, 1f / 3, 2f / 3, 1f), progress)

        progress.clear()
        analyzer(FakeLlmRunner(listOf(summary, """{"plans":false}""")))
            .analyze(transcript(), onProgress = { progress += it.fraction })
        assertEquals(listOf(0f, 1f / 3, 2f / 3, 1f), progress)
    }

    @Test
    fun `summary and tasks come from separate prompts`() = runTest {
        val runner = FakeLlmRunner(listOf(summary, plans, tasks))

        val analysis = (analyzer(runner).analyze(transcript()) as AnalysisOutcome.Success).analysis

        assertEquals("Fix the tap", analysis.title)
        assertEquals("It drips.", analysis.summary)
        assertEquals(listOf("Buy a washer"), analysis.items.map { it.text })
        assertEquals(3, runner.prompts.size)
    }

    @Test
    fun `a task's line maps to its segment's start`() = runTest {
        val runner = FakeLlmRunner(listOf(summary, plans, tasks))
        val segments = listOf(Segment(0, 2_000, "the kitchen tap drips."), Segment(2_000, 4_000, "buy a washer."))

        val analysis = (analyzer(runner, segments).analyze(transcript()) as AnalysisOutcome.Success).analysis

        assertEquals(2_000L, analysis.items.single().sourceOffsetMs)
        assertTrue(runner.prompts[2].contains("[1] buy a washer."))
    }

    @Test
    fun `a long note is summarized in parts, then merged`() = runTest {
        val words = (1..2_000).joinToString(" ") { "w$it" }
        val segments = listOf(Segment(0, 1, words.substringBefore(" w1001")), Segment(1, 2, "w1001" + words.substringAfter(" w1001")))
        val merged = """{"title":"Whole note","summary":"All of it."}"""
        val runner = FakeLlmRunner(listOf(summary, summary, merged, plans, """{"items":[]}""", plans, """{"items":[]}"""))

        val progress = mutableListOf<Float>()
        val analysis = (analyzer(runner, segments).analyze(transcript(words)) { progress += it.fraction } as AnalysisOutcome.Success).analysis
        assertEquals((0..7).map { it / 7f }, progress)

        assertEquals("Whole note", analysis.title)
        assertTrue(runner.prompts[2].contains("Part 2"))
        assertEquals(7, runner.prompts.size)
    }

    @Test
    fun `the transcript date reaches the tasks prompt, not today`() = runTest {
        val runner = FakeLlmRunner(listOf(summary, plans, tasks))

        analyzer(runner).analyze(transcript())

        assertTrue(runner.prompts[2].contains("Today is 2026-09-03"))
    }

    @Test
    fun `unparseable output retries once at zero temperature`() = runTest {
        val runner = FakeLlmRunner(listOf("no json here", summary, plans, tasks))

        val outcome = analyzer(runner).analyze(transcript())

        assertEquals("Fix the tap", (outcome as AnalysisOutcome.Success).analysis.title)
        val retried = listOf(LlmRunner.DEFAULT_TEMPERATURE, 0f)
        assertEquals(retried + LlmRunner.DEFAULT_TEMPERATURE + LlmRunner.DEFAULT_TEMPERATURE, runner.temperatures)
    }

    @Test
    fun `it gives up after the retry`() = runTest {
        val runner = FakeLlmRunner(listOf("nope", "still nope"))

        val progress = mutableListOf<Float>()
        val outcome = analyzer(runner).analyze(transcript()) { progress += it.fraction }

        assertEquals(listOf(0f), progress)
        assertEquals(AnalysisOutcome.Blocked(AnalysisBlock.EmptyResult), outcome)
        assertEquals(2, runner.prompts.size)
    }

    @Test
    fun `no tasks is still a success`() = runTest {
        val runner = FakeLlmRunner(listOf(summary, plans, """{"items":[]}"""))

        val outcome = analyzer(runner).analyze(transcript())

        assertTrue((outcome as AnalysisOutcome.Success).analysis.items.isEmpty())
    }

    @Test
    fun `a speaker with no plans of their own gets no tasks prompt`() = runTest {
        val runner = FakeLlmRunner(listOf(summary, """{"plans":false}""", tasks))

        val analysis = (analyzer(runner).analyze(transcript()) as AnalysisOutcome.Success).analysis

        assertTrue(analysis.items.isEmpty())
        assertEquals(2, runner.prompts.size)
    }

    @Test
    fun `a throwing runner does not propagate`() = runTest {
        val runner = FakeLlmRunner(listOf(summary), throwOnGenerate = true)

        val outcome = analyzer(runner).analyze(transcript())

        assertTrue((outcome as AnalysisOutcome.Blocked).reason is AnalysisBlock.GenerationFailed)
    }

    @Test
    fun `an unavailable runner is not called`() = runTest {
        val runner = FakeLlmRunner(availability = LlmAvailability.Unsupported("too little memory"))

        val outcome = analyzer(runner).analyze(transcript())

        assertEquals(AnalysisOutcome.Blocked(AnalysisBlock.NoBackend("too little memory")), outcome)
        assertTrue(runner.prompts.isEmpty())
    }

    @Test
    fun `a runner awaiting download is not called`() = runTest {
        val runner = FakeLlmRunner(availability = LlmAvailability.NeedsDownload(bytes = 550_000_000))

        val outcome = analyzer(runner).analyze(transcript())

        assertEquals(AnalysisOutcome.Blocked(AnalysisBlock.WaitingForModel(550_000_000)), outcome)
        assertTrue(runner.prompts.isEmpty())
    }

    @Test
    fun `a blank transcript is not sent to the model`() = runTest {
        val runner = FakeLlmRunner(listOf(summary))

        val outcome = analyzer(runner).analyze(transcript(text = "   "))

        assertEquals(AnalysisOutcome.Blocked(AnalysisBlock.EmptyTranscript), outcome)
        assertTrue(runner.prompts.isEmpty())
    }
    @Test
    fun `cancellation propagates without retrying generation`() = runTest {
        var calls = 0
        val runner = object : LlmRunner by FakeLlmRunner() {
            override suspend fun generate(prompt: String, temperature: Float): String {
                calls++
                throw kotlinx.coroutines.CancellationException("Stopped by user")
            }
        }
        var cancelled = false
        try {
            TranscriptAnalyzer({ runner }, zone = UTC, logDebug = {}, logWarn = {}).analyze(transcript())
        } catch (e: kotlinx.coroutines.CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertEquals(1, calls)
    }

}
