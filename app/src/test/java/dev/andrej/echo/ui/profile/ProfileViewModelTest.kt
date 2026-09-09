package dev.andrej.echo.ui.profile

import dev.andrej.echo.data.Transcript
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileViewModelTest {

    private val zone: ZoneId = ZoneOffset.UTC

    private fun epochMillisAt(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun transcript(id: String, day: LocalDate, durationMs: Long = 60_000) = Transcript(
        id = id,
        text = "hello",
        language = "en",
        createdAt = epochMillisAt(day),
        durationMs = durationMs,
    )

    @Test
    fun `streak counts consecutive days ending today, stopping at the first gap`() {
        val today = LocalDate.of(2026, 9, 9)
        val transcripts = listOf(
            transcript("a", today),
            transcript("b", today.minusDays(1)),
            transcript("c", today.minusDays(2)),
            // gap at today - 3
            transcript("d", today.minusDays(4)),
        )

        val state = buildState(transcripts, emptyList(), epochMillisAt(today), zone)

        assertEquals(3, state.streak)
    }

    @Test
    fun `a day with no recordings is not part of the streak`() {
        val today = LocalDate.of(2026, 9, 9)

        val state = buildState(emptyList(), emptyList(), epochMillisAt(today), zone)

        assertEquals(0, state.streak)
    }

    @Test
    fun `week bucket for today reflects only today's recordings`() {
        val today = LocalDate.of(2026, 9, 9)
        val transcripts = listOf(transcript("a", today, durationMs = 120_000))

        val state = buildState(transcripts, emptyList(), epochMillisAt(today), zone)

        assertEquals(7, state.week.size)
        assertEquals(true, state.week.last().active)
        assertEquals(1f, state.week.last().fraction, 0.001f)
    }
}
