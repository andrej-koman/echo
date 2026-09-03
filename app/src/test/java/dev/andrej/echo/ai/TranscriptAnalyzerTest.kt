package dev.andrej.echo.ai

import dev.andrej.echo.data.Transcript
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val UTC = ZoneId.of("UTC")

class TranscriptAnalyzerTest {

    private val good = """{"title":"Fix the tap","summary":"It drips.","tasks":["Buy a washer"],"reminders":[]}"""

    private fun transcript(text: String = "the kitchen tap drips, buy a washer") = Transcript(
        id = "t1",
        text = text,
        language = "en-GB",
        createdAt = LocalDate.of(2026, 9, 3).atStartOfDay(UTC).toInstant().toEpochMilli(),
        durationMs = 4_000,
    )

    @Test
    fun `usable output is returned`() = runTest {
        val analysis = TranscriptAnalyzer(FakeLlmRunner(listOf(good)), UTC).analyze(transcript())!!

        assertEquals("Fix the tap", analysis.title)
        assertEquals(listOf("Buy a washer"), analysis.tasks)
    }

    @Test
    fun `the transcript date reaches the prompt, not today`() = runTest {
        val runner = FakeLlmRunner(listOf(good))

        TranscriptAnalyzer(runner, UTC).analyze(transcript())

        assertTrue(runner.prompts.single().contains("Today is 2026-09-03"))
    }

    @Test
    fun `unparseable output retries once at zero temperature`() = runTest {
        val runner = FakeLlmRunner(listOf("no json here", good))

        val analysis = TranscriptAnalyzer(runner, UTC).analyze(transcript())

        assertEquals("Fix the tap", analysis?.title)
        assertEquals(listOf(LlmRunner.DEFAULT_TEMPERATURE, 0f), runner.temperatures)
    }

    @Test
    fun `it gives up after the retry`() = runTest {
        val runner = FakeLlmRunner(listOf("nope", "still nope"))

        assertNull(TranscriptAnalyzer(runner, UTC).analyze(transcript()))
        assertEquals(2, runner.prompts.size)
    }

    @Test
    fun `an empty result counts as a failure and is retried`() = runTest {
        val empty = """{"title":"","summary":"","tasks":[],"reminders":[]}"""
        val runner = FakeLlmRunner(listOf(empty, good))

        assertEquals("Fix the tap", TranscriptAnalyzer(runner, UTC).analyze(transcript())?.title)
    }

    @Test
    fun `a throwing runner does not propagate`() = runTest {
        val runner = FakeLlmRunner(listOf(good), throwOnGenerate = true)

        assertNull(TranscriptAnalyzer(runner, UTC).analyze(transcript()))
    }

    @Test
    fun `an unavailable runner is not called`() = runTest {
        val runner = FakeLlmRunner(
            responses = listOf(good),
            availability = LlmAvailability.Unsupported("no AICore"),
        )

        assertNull(TranscriptAnalyzer(runner, UTC).analyze(transcript()))
        assertTrue(runner.prompts.isEmpty())
    }

    @Test
    fun `a runner awaiting download is not called`() = runTest {
        val runner = FakeLlmRunner(
            responses = listOf(good),
            availability = LlmAvailability.NeedsDownload(bytes = 550_000_000),
        )

        assertNull(TranscriptAnalyzer(runner, UTC).analyze(transcript()))
        assertTrue(runner.prompts.isEmpty())
    }

    @Test
    fun `a blank transcript is not sent to the model`() = runTest {
        val runner = FakeLlmRunner(listOf(good))

        assertNull(TranscriptAnalyzer(runner, UTC).analyze(transcript(text = "   ")))
        assertTrue(runner.prompts.isEmpty())
    }
}
