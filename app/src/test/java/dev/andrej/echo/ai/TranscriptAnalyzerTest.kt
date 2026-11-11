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

    @Test
    fun `dentist itinerary keeps tomorrow times and all day assignment despite incorrect model output`() = runTest {
        val lines = listOf(
            Segment(1190, 9060, "Hi. Okay. So I'm thinking about what I should do tomorrow. I think that tomorrow at 10 a.m."),
            Segment(9060, 18600, "I have to go to the dentist. Then at 12 a.m. tomorrow I have to go to the shop. Then after"),
            Segment(18600, 30640, "everything at around, I don't know, 6 p.m. I have drinks with my girlfriend. Oh, and also"),
            Segment(30640, 38140, "remind me to for tomorrow all day to turn in an assignment."),
        )
        val raw = """{"items":[
            {"text":"Go to the dentist.","due":"2026-11-09T10:00:00","line":0},
            {"text":"Go to the shop.","due":"2026-11-09T00:00:00","line":1},
            {"text":"Make drinks with my girlfriend.","due":null,"line":2},
            {"text":"Turn in an assignment.","due":"2026-11-09T00:00:00","line":3}
        ]}"""
        val recorded = LocalDate.of(2026, 11, 9).atTime(8, 58)
        val note = transcript(lines.joinToString(" ") { it.text }).copy(
            createdAt = recorded.atZone(UTC).toInstant().toEpochMilli(),
        )
        val result = (analyzer(FakeLlmRunner(listOf(summary, plans, raw)), lines)
            .analyze(note) as AnalysisOutcome.Success).analysis.items
        val tomorrow = recorded.toLocalDate().plusDays(1)
        assertEquals(listOf(10, 0, 18, 0).map { tomorrow.atTime(it, 0).atZone(UTC).toInstant().toEpochMilli() }, result.map { it.dueAt })
        assertEquals(listOf(true, true, true, false), result.map { it.hasTime })
        assertTrue(result.all { it.hasDate })
        assertEquals(listOf(9060L, 9060L, 18600L, 30640L), result.map { it.sourceOffsetMs })
    }

    @Test
    fun `a task named only by its time is retried, then dropped`() = runTest {
        val timeOnly = """{"items":[{"text":"Tomorrow at 10 a.m.","due":null,"line":0},{"text":"Buy a washer","due":null,"line":1}]}"""

        val fixed = FakeLlmRunner(listOf(summary, plans, timeOnly, tasks))
        val retried = (analyzer(fixed).analyze(transcript()) as AnalysisOutcome.Success).analysis.items
        assertEquals(listOf("Buy a washer"), retried.map { it.text })
        assertEquals(listOf(LlmRunner.DEFAULT_TEMPERATURE, 0f), fixed.temperatures.takeLast(2))

        val stubborn = FakeLlmRunner(listOf(summary, plans, timeOnly, timeOnly))
        val dropped = (analyzer(stubborn).analyze(transcript()) as AnalysisOutcome.Success).analysis.items
        assertEquals(listOf("Buy a washer"), dropped.map { it.text })
    }

    @Test
    fun `a Slovenian note is answered in English and grounded on its quotes`() = runTest {
        val segments = listOf(Segment(0, 7_000, "Tako da jutri ob desetih zjutraj želim iti k zdravniku."),
            Segment(7_000, 14_000, "Na to pa danes pa bi rad ob šestih zvečer šal na trening."))
        // The model's real answer on device, wrong day and untranslated when_en included.
        val english = """{"items":[
            {"text":"Go to the doctor","source":"Tako da jutri ob desetih zjutraj želim iti k zdravniku.","due":"2026-11-12T10:00:00","line":0,"when_en":"jutri ob desetih"},
            {"text":"Go to the gym","source":"Na to pa danes pa bi rad ob šestih zvečer šal na trening.","due":"2026-11-12T18:00:00","line":1,"when_en":"danes ob šestih"}
        ]}"""
        val runner = FakeLlmRunner(listOf("""{"title":"Doctor and gym","summary":"Doctor tomorrow at 10, gym today at 6 pm."}""", plans, english))
        val recorded = LocalDate.of(2026, 11, 11).atTime(16, 5)
        val note = transcript(segments.joinToString(" ") { it.text }).copy(language = "sl", createdAt = recorded.atZone(UTC).toInstant().toEpochMilli())

        val analysis = (analyzer(runner, segments).analyze(note) as AnalysisOutcome.Success).analysis

        assertTrue(runner.prompts[0].contains("in English"))
        assertEquals(3, runner.prompts.size)
        assertEquals("Doctor and gym", analysis.title)
        assertEquals(listOf("Go to the doctor", "Go to the gym"), analysis.items.map { it.text })
        assertEquals(listOf(recorded.plusDays(1).toLocalDate().atTime(10, 0), recorded.toLocalDate().atTime(18, 0))
            .map { it.atZone(UTC).toInstant().toEpochMilli() }, analysis.items.map { it.dueAt })
        assertEquals(listOf(0L, 7_000L), analysis.items.map { it.sourceOffsetMs })
        assertEquals(segments.map { it.text }, analysis.items.map { it.sourceQuote })
    }

    @Test
    fun `a model that writes the note's language answers in it`() = runTest {
        val runner = FakeLlmRunner(listOf(summary, plans, tasks), writesNoteLanguage = true)
        analyzer(runner).analyze(transcript().copy(language = "sl"))
        assertTrue(runner.prompts[0].contains("in Slovenian, the language of the note"))
        assertTrue(runner.prompts[2].contains("when_en"))
    }

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
