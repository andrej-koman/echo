package dev.andrej.echo.ui.tasks

import dev.andrej.echo.data.TodoItem
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val UTC = ZoneId.of("UTC")

// "Now" is 2026-09-04T12:00:00Z.
private val NOW = LocalDate.of(2026, 9, 4).atTime(12, 0).atZone(UTC).toInstant().toEpochMilli()
private val YESTERDAY_9AM = LocalDate.of(2026, 9, 3).atTime(9, 0).atZone(UTC).toInstant().toEpochMilli()
private val TODAY_9AM = LocalDate.of(2026, 9, 4).atTime(9, 0).atZone(UTC).toInstant().toEpochMilli()
private val YESTERDAY_START = LocalDate.of(2026, 9, 3).atStartOfDay(UTC).toInstant().toEpochMilli()
private val TODAY_START = LocalDate.of(2026, 9, 4).atStartOfDay(UTC).toInstant().toEpochMilli()
private val TOMORROW_START = LocalDate.of(2026, 9, 5).atStartOfDay(UTC).toInstant().toEpochMilli()
private val NEXT_WEEK_START = LocalDate.of(2026, 9, 11).atStartOfDay(UTC).toInstant().toEpochMilli()

class TasksViewModelTest {

    private fun item(
        id: String,
        dueAt: Long,
        hasTime: Boolean,
        createdAt: Long = 0,
        done: Boolean = false,
    ) = TodoItem(
        id = id,
        sourceTranscriptId = "t1",
        text = id,
        dueAt = dueAt,
        hasTime = hasTime,
        done = done,
        createdAt = createdAt,
    )

    @Test
    fun `a timed item is overdue once its clock time passes`() {
        assertTrue(isOverdue(item("a", YESTERDAY_9AM, hasTime = true), NOW, UTC))
        assertTrue(isOverdue(item("a", TODAY_9AM, hasTime = true), NOW, UTC))
        assertFalse(isOverdue(item("a", NOW + 1, hasTime = true), NOW, UTC))
    }

    @Test
    fun `a date-only item is not overdue until the next day`() {
        assertFalse(isOverdue(item("a", TODAY_START, hasTime = false), NOW, UTC))
        assertTrue(isOverdue(item("a", YESTERDAY_START, hasTime = false), NOW, UTC))
    }

    @Test
    fun `overdue is grouped ahead of today, tomorrow and later, regardless of done`() {
        val items = listOf(
            item("overdue-timed", YESTERDAY_9AM, hasTime = true),
            item("today-timed-passed", TODAY_9AM, hasTime = true, done = true),
            item("today-untimed", TODAY_START, hasTime = false),
            item("tomorrow", TOMORROW_START, hasTime = true),
            item("next-week", NEXT_WEEK_START, hasTime = false),
        )

        val groups = groupByDue(items, NOW, UTC)

        assertEquals(listOf("Overdue", "Today", "Tomorrow", "11 Sep 2026"), groups.map { it.label })
        assertEquals(
            setOf("overdue-timed", "today-timed-passed"),
            groups.single { it.label == "Overdue" }.items.map { it.id }.toSet(),
        )
        assertEquals(listOf("today-untimed"), groups.single { it.label == "Today" }.items.map { it.id })
        assertEquals(listOf("tomorrow"), groups.single { it.label == "Tomorrow" }.items.map { it.id })
        assertEquals(listOf("next-week"), groups.single { it.label == "11 Sep 2026" }.items.map { it.id })
    }

    @Test
    fun `within a group timed items come first sorted by time, then date-only by creation order`() {
        val items = listOf(
            item("late-created-first", TODAY_START, hasTime = false, createdAt = 200),
            item("early-created-first", TODAY_START, hasTime = false, createdAt = 100),
            item("later-time", TODAY_9AM + 3_600_000, hasTime = true),
            item("sooner-time", TODAY_9AM, hasTime = true),
        )

        // today-9am has passed relative to NOW (noon), so the timed items land in Overdue.
        val overdue = groupByDue(items, NOW, UTC).single { it.label == "Overdue" }.items.map { it.id }

        assertEquals(listOf("sooner-time", "later-time"), overdue)

        val today = groupByDue(items, NOW, UTC).single { it.label == "Today" }.items.map { it.id }
        assertEquals(listOf("early-created-first", "late-created-first"), today)
    }
}
