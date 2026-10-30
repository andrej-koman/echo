package dev.andrej.echo.transcribe

import dev.andrej.echo.speech.SAMPLE_RATE
import dev.andrej.echo.speech.whisper.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkingTest {

    @Test
    fun `cut lands in the silent gap near the end`() {
        val samples = FloatArray(10 * SAMPLE_RATE) { if (it % 2 == 0) 0.5f else -0.5f }
        val gap = 8 * SAMPLE_RATE until 8 * SAMPLE_RATE + SAMPLE_RATE / 2
        gap.forEach { samples[it] = 0f }

        val cut = quietestCut(samples)

        assertTrue("cut $cut outside the gap", cut in gap)
    }

    @Test
    fun `short input is never cut`() {
        val samples = FloatArray(SAMPLE_RATE)
        assertEquals(samples.size, quietestCut(samples))
    }

    @Test
    fun `transcript text drops bracketed captions`() {
        val segments = listOf(
            Segment(0, 1, " Hello there."),
            Segment(1, 2, "[BLANK_AUDIO]"),
            Segment(2, 3, " (music) "),
            Segment(3, 4, "Buy milk."),
        )
        assertEquals("Hello there. Buy milk.", transcriptText(segments))
    }
}
