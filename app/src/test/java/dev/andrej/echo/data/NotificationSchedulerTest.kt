package dev.andrej.echo.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

private val UTC = ZoneId.of("UTC")

class FireTimeForTest {

    private fun item(dueAt: Long, hasTime: Boolean) = TodoItem(
        id = "a",
        sourceTranscriptId = "t1",
        text = "call the plumber",
        dueAt = dueAt,
        hasTime = hasTime,
        createdAt = 0,
    )

    @Test
    fun `a timed item fires 15 minutes ahead`() {
        val dueAt = LocalDate.of(2026, 9, 4).atTime(10, 0).atZone(UTC).toInstant().toEpochMilli()

        assertEquals(dueAt - 15 * 60 * 1000L, fireTimeFor(item(dueAt, hasTime = true), UTC))
    }

    @Test
    fun `a date-only item fires at 9am that day`() {
        val dueAt = LocalDate.of(2026, 9, 4).atStartOfDay(UTC).toInstant().toEpochMilli()
        val expected = LocalDate.of(2026, 9, 4).atTime(9, 0).atZone(UTC).toInstant().toEpochMilli()

        assertEquals(expected, fireTimeFor(item(dueAt, hasTime = false), UTC))
    }
}
