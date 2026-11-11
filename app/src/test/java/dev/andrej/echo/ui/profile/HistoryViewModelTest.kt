package dev.andrej.echo.ui.profile

import dev.andrej.echo.data.TodoItem
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
    fun `a day with more than three notes has three dots`() {
        val day = LocalDate.of(2026, 9, 9)
        val transcripts = listOf(transcript("a", day), transcript("b", day), transcript("c", day), transcript("d", day))

        val state = buildHistoryState(transcripts, emptyList(), YearMonth.of(2026, 9), day, day, zone)

        val cell = state.days.first { it.date == day }
        assertEquals(3, cell.heatLevel)
    }

    @Test
    fun `the grid pads with out-of-month days to stay aligned to the week`() {
        val month = YearMonth.of(2026, 9)
        val state = buildHistoryState(emptyList(), emptyList(), month, month.atDay(1), month.atDay(1), zone)

        assertEquals(35, state.days.size)
        assertEquals(java.time.DayOfWeek.MONDAY, state.days.first().date.dayOfWeek)
        assertEquals(true, state.days.any { !it.inMonth })
        assertEquals(30, state.days.count { it.inMonth }) // September 2026 has 30 days
    }

    @Test
    fun `future months remain navigable`() {
        val today = LocalDate.of(2026, 9, 9)
        val state = buildHistoryState(emptyList(), emptyList(), YearMonth.from(today), today, today, zone)

        assertEquals(true, state.canGoNext)
    }

    @Test
    fun `selecting a day surfaces that day's transcripts, most recent first`() {
        val day = LocalDate.of(2026, 9, 9)
        val month = YearMonth.from(day)
        val transcripts = listOf(transcript("a", day), transcript("b", day))

        val state = buildHistoryState(transcripts, emptyList(), month, day, day, zone)

        assertEquals(2, state.selectedDetail.size)
    }
    @Test
    fun `today heading is identified`() {
        val day = LocalDate.of(2026, 11, 2)
        val state = buildHistoryState(emptyList(), emptyList(), YearMonth.from(day), day, day, zone)
        assertEquals("Today, 2 November", state.selectedDayLabel)
    }

    @Test
    fun `collapsed week crosses the month boundary from Monday to Sunday`() {
        val day = LocalDate.of(2026, 11, 1)
        val state = buildHistoryState(emptyList(), emptyList(), YearMonth.from(day), day, day, zone)
        assertEquals(LocalDate.of(2026, 10, 26), state.weekDays.first().date)
        assertEquals(day, state.weekDays.last().date)
        assertEquals(7, state.weekDays.size)
    }

    @Test
    fun `empty day links to the closest earlier populated day`() {
        val day = LocalDate.of(2026, 11, 4)
        val notes = listOf(transcript("a", day.minusDays(5)), transcript("b", day.minusDays(2)), transcript("c", day.plusDays(1)))
        val state = buildHistoryState(notes, emptyList(), YearMonth.from(day), day, day, zone)
        assertEquals(day.minusDays(2), state.previousNoteDay)
        assertEquals(null, buildHistoryState(emptyList(), emptyList(), YearMonth.from(day), day, day, zone).previousNoteDay)
    }

    @Test
    fun `arrows move by week or clamp to a valid day in the next month`() {
        val day = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 7), calendarStep(day, false, 1))
        assertEquals(LocalDate.of(2026, 1, 24), calendarStep(day, false, -1))
        assertEquals(LocalDate.of(2026, 2, 28), calendarStep(day, true, 1))
        assertEquals(LocalDate.of(2025, 12, 31), calendarStep(day, true, -1))
    }

    @Test
    fun `dots represent note counts capped at three in both views`() {
        val day = LocalDate.of(2026, 11, 2)
        val notes = (0..4).flatMap { count -> (0 until count).map { transcript("$count-$it", day.plusDays(count.toLong())) } }
        val state = buildHistoryState(notes, emptyList(), YearMonth.from(day), day, day, zone)
        assertEquals(listOf(0, 1, 2, 3, 3), state.weekDays.take(5).map { it.heatLevel })
        assertEquals(state.weekDays.map { it.heatLevel }, state.days.filter { it.date in day..day.plusDays(6) }.map { it.heatLevel })
    }


    @Test
    fun `dated tasks sit on their due day, undated ones nowhere`() {
        val noteDay = LocalDate.of(2026, 9, 9)
        val dueDay = noteDay.plusDays(1)
        fun task(id: String, hasDate: Boolean = true, done: Boolean = false) = TodoItem(
            id = id, sourceTranscriptId = "a", text = id, dueAt = epochMillisAt(dueDay) + 3_600_000,
            hasTime = true, hasDate = hasDate, done = done, createdAt = epochMillisAt(noteDay),
        )
        val items = listOf(task("done", done = true), task("open"), task("undated", hasDate = false))

        val state = buildHistoryState(listOf(transcript("a", noteDay)), items, YearMonth.from(noteDay), dueDay, noteDay, zone)

        assertEquals(0, state.days.first { it.date == noteDay }.taskLevel)
        assertEquals(2, state.days.first { it.date == dueDay }.taskLevel)
        assertEquals(listOf("open", "done"), state.selectedTasks.map { it.id })
        assertEquals("hello", state.noteTitles["a"])
    }
}
