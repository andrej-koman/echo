package dev.andrej.echo.speech.speakers

import dev.andrej.echo.data.Segment
import org.junit.Assert.assertEquals
import org.junit.Test

class AssignSpeakersTest {

    private fun segment(start: Long, end: Long) = Segment(start, end, "x")

    @Test
    fun `each segment takes the speaker it overlaps most, numbered by first appearance`() {
        val turns = listOf(
            SpeakerTurn(0, 2_000, speaker = 3),
            SpeakerTurn(2_000, 6_000, speaker = 0),
            SpeakerTurn(6_000, 8_000, speaker = 3),
        )
        val segments = listOf(segment(0, 1_800), segment(1_500, 5_000), segment(6_100, 7_000))

        val (assigned, count) = assignSpeakers(segments, turns)

        assertEquals(listOf(0, 1, 0), assigned.map { it.speaker })
        assertEquals(2, count)
    }

    @Test
    fun `a segment in a gap takes the nearest turn`() {
        val turns = listOf(SpeakerTurn(0, 1_000, 5), SpeakerTurn(9_000, 10_000, 7))

        val (assigned, _) = assignSpeakers(listOf(segment(0, 900), segment(7_000, 8_000)), turns)

        assertEquals(listOf(0, 1), assigned.map { it.speaker })
    }

    @Test
    fun `no turns leaves segments unassigned`() {
        val (assigned, count) = assignSpeakers(listOf(segment(0, 1)), emptyList())

        assertEquals(listOf(null), assigned.map { it.speaker })
        assertEquals(0, count)
    }
}
