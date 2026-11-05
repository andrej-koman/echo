package dev.andrej.echo.ui

import dev.andrej.echo.ui.notes.dateLabel
import dev.andrej.echo.ui.tasks.dayLabel
import org.junit.Assert.assertEquals
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import org.junit.Assert.assertTrue
import org.junit.Test

class DueLabelsTest {
    @Test fun relativeLabelsFollowCalendarDaysAcrossBothDstTransitions() {
        val previous = TimeZone.getDefault()
        val zone = ZoneId.of("Europe/Ljubljana")
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        try {
            for (today in listOf(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 10, 25))) {
                val now = today.atStartOfDay(zone).toInstant().toEpochMilli()
                val tomorrow = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                assertEquals("Tomorrow", dayLabel(tomorrow, now))
                assertEquals(1L, calendarDaysBetween(now, tomorrow))
                assertTrue(formatDue(tomorrow, now), formatDue(tomorrow, now).startsWith("Tomorrow,"))
                assertTrue(dateLabel(now, tomorrow), dateLabel(now, tomorrow).startsWith("Yesterday,"))
            }
        } finally { TimeZone.setDefault(previous) }
    }
}
