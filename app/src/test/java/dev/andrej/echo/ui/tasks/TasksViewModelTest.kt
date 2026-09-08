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
    fun `three buckets only, overdue folded into Today ahead of the rest, done items excluded`() {
        val items = listOf(
            item("overdue-timed", YESTERDAY_9AM, hasTime = true),
            item("today-timed-passed", TODAY_9AM, hasTime = true, done = true),
            item("today-untimed", TODAY_START, hasTime = false),
            item("tomorrow", TOMORROW_START, hasTime = true),
            item("next-week", NEXT_WEEK_START, hasTime = false),
        )

        val groups = groupByDue(items, NOW, UTC)

        assertEquals(listOf("Today", "Tomorrow", "Upcoming"), groups.map { it.label })
        assertEquals(
            listOf("overdue-timed", "today-untimed"),
            groups.single { it.label == "Today" }.items.map { it.id },
        )
        assertEquals(listOf("tomorrow"), groups.single { it.label == "Tomorrow" }.items.map { it.id })
        assertEquals(listOf("next-week"), groups.single { it.label == "Upcoming" }.items.map { it.id })
        assertFalse(groups.single { it.label == "Today" }.showDate)
        assertFalse(groups.single { it.label == "Tomorrow" }.showDate)
        assertTrue(groups.single { it.label == "Upcoming" }.showDate)
    }

    @Test
    fun `done items are excluded from groupByDue entirely, even if it empties a group`() {
        val items = listOf(item("done-only", TODAY_START, hasTime = false, done = true))

        assertEquals(emptyList<DueGroup>(), groupByDue(items, NOW, UTC))
    }

    @Test
    fun `completedByRecency returns only done items, most recently updated first`() {
        val items = listOf(
            item("undone", TODAY_START, hasTime = false),
            item("done-older", TODAY_START, hasTime = false, done = true).copy(updatedAt = 100),
            item("done-newer", TODAY_START, hasTime = false, done = true).copy(updatedAt = 200),
        )

        assertEquals(listOf("done-newer", "done-older"), completedByRecency(items).map { it.id })
    }

    @Test
    fun `within a group timed items come first sorted by time, then date-only by creation order`() {
        val items = listOf(
            item("late-created-first", TODAY_START, hasTime = false, createdAt = 200),
            item("early-created-first", TODAY_START, hasTime = false, createdAt = 100),
            item("later-time", TODAY_9AM + 3_600_000, hasTime = true),
            item("sooner-time", TODAY_9AM, hasTime = true),
        )

        // Both timed items have passed relative to NOW (noon), so they sort ahead as overdue,
        // then the date-only items follow by creation order.
        val today = groupByDue(items, NOW, UTC).single { it.label == "Today" }.items.map { it.id }

        assertEquals(
            listOf("sooner-time", "later-time", "early-created-first", "late-created-first"),
            today,
        )
    }

    @Test
    fun `filterByQuery matches task text case-insensitively`() {
        val items = listOf(
            item("Buy milk", TODAY_START, hasTime = false),
            item("Call mum", TODAY_START, hasTime = false),
        )

        assertEquals(listOf("Buy milk"), filterByQuery(items, "milk").map { it.text })
        assertEquals(listOf("Buy milk"), filterByQuery(items, "MILK").map { it.text })
        assertEquals(items.map { it.text }, filterByQuery(items, "").map { it.text })
    }
}
