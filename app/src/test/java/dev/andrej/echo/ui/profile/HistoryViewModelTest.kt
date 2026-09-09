package dev.andrej.echo.ui.profile

import dev.andrej.echo.data.Transcript
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryViewModelTest {

    private val zone: ZoneId = ZoneOffset.UTC

    private fun epochMillisAt(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun transcript(id: String, day: LocalDate) = Transcript(
        id = id,
        text = "hello",
        language = "en",
        createdAt = epochMillisAt(day),
        durationMs = 60_000,
    )

    @Test
    fun `a day with three or more notes hits the top heat bucket`() {
        val day = LocalDate.of(2026, 9, 9)
        val transcripts = listOf(transcript("a", day), transcript("b", day), transcript("c", day))

        val state = buildHistoryState(transcripts, emptyList(), YearMonth.of(2026, 9), day, day, zone)

        val cell = state.days.first { it.date == day }
        assertEquals(3, cell.heatLevel)
    }

    @Test
    fun `the grid pads with out-of-month days to stay aligned to the week`() {
        val month = YearMonth.of(2026, 9)
        val state = buildHistoryState(emptyList(), emptyList(), month, month.atDay(1), month.atDay(1), zone)

        assertEquals(42, state.days.size)
        assertEquals(true, state.days.any { !it.inMonth })
        assertEquals(30, state.days.count { it.inMonth }) // September 2026 has 30 days
    }

    @Test
    fun `next month is blocked once the displayed month reaches today's month`() {
        val today = LocalDate.of(2026, 9, 9)
        val state = buildHistoryState(emptyList(), emptyList(), YearMonth.from(today), today, today, zone)

        assertEquals(false, state.canGoNext)
    }

    @Test
    fun `selecting a day surfaces that day's transcripts, most recent first`() {
        val day = LocalDate.of(2026, 9, 9)
        val month = YearMonth.from(day)
        val transcripts = listOf(transcript("a", day), transcript("b", day))

        val state = buildHistoryState(transcripts, emptyList(), month, day, day, zone)

        assertEquals(2, state.selectedDetail.size)
    }
}
