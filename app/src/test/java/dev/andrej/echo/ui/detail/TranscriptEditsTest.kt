package dev.andrej.echo.ui.detail

import dev.andrej.echo.data.Segment
import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptEditsTest {

    private val segments = listOf(
        Segment(0, 2_000, "Call the bank."),
        Segment(2_000, 5_000, "Then call Ana about the BANK holiday."),
        Segment(6_000, 8_000, "Bank."),
    )

    @Test
    fun `segmentAt finds the segment that has started`() {
        assertEquals(-1, segmentAt(segments, -1))
        assertEquals(0, segmentAt(segments, 0))
        assertEquals(1, segmentAt(segments, 4_999))
        assertEquals(1, segmentAt(segments, 5_500))
        assertEquals(2, segmentAt(segments, 60_000))
        assertEquals(-1, segmentAt(emptyList(), 100))
    }

    @Test
    fun `matches ignore case and do not overlap`() {
        assertEquals(listOf(0..1, 2..3), matchRanges("aAaa", "aa"))
        assertEquals(3, countMatches(segments, "bank"))
        assertEquals(0, countMatches(segments, ""))
    }

    @Test
    fun `replaceAll is literal and drops emptied segments`() {
        val replaced = replaceAll(segments, "bank.", "")
        assertEquals(listOf("Call the", "Then call Ana about the BANK holiday."), replaced.map { it.text })
        assertEquals(2_000L, replaced[0].endMs)
    }

    @Test
    fun `applyEdits rewrites by index and drops blanked segments`() {
        val edited = applyEdits(segments, mapOf(0 to " Call the bank today. ", 2 to " "))
        assertEquals(listOf("Call the bank today.", segments[1].text), edited.map { it.text })
    }

    @Test
    fun `assignSpeaker changes only the chosen segments`() {
        val assigned = assignSpeaker(segments, setOf(0, 2), speaker = 1)
        assertEquals(listOf(1, null, 1), assigned.map { it.speaker })
    }

    @Test
    fun `a selection splits its segment with interpolated times`() {
        val two = listOf(Segment(0, 1_000, "Hello there friend", speaker = 0), Segment(1_000, 2_000, "Bye", 0))

        val split = assignSelection(two, 0, 6 until 11, speaker = 1)

        assertEquals(listOf("Hello", "there", "friend", "Bye"), split.map { it.text })
        assertEquals(listOf(0, 1, 0, 0), split.map { it.speaker })
        assertEquals(listOf(0L, 333L, 611L, 1_000L), split.map { it.startMs })
        assertEquals(listOf(333L, 611L, 1_000L, 2_000L), split.map { it.endMs })
    }

    @Test
    fun `a selection covering the whole segment only reassigns it`() {
        val one = listOf(Segment(0, 1_000, "Hello", speaker = 0))
        assertEquals(listOf(Segment(0, 1_000, "Hello", 1)), assignSelection(one, 0, 0 until 5, speaker = 1))
    }
}
