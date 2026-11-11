package dev.andrej.echo.widget

import dev.andrej.echo.data.TodoItem
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSnapshotTest {
    private val zone = ZoneId.of("Europe/Ljubljana")
    private fun at(value: String) = LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
    private val now = at("2026-11-11T10:30:00")
    private fun task(id: String, dueAt: Long, hasTime: Boolean = true, hasDate: Boolean = true, done: Boolean = false) =
        TodoItem(id, "note", id, dueAt, hasTime, hasDate, done, createdAt = 0)

    @Test fun `today lists overdue first, then timed, then date-only, and leaves undated tasks out`() {
        val items = listOf(
            task("later", at("2026-11-11T14:30:00")),
            task("dateOnly", at("2026-11-11T00:00:00"), hasTime = false),
            task("overdue", at("2026-11-10T09:00:00")),
            task("undated", 0, hasTime = false, hasDate = false),
            task("tomorrow", at("2026-11-12T09:00:00")),
        )
        val today = todayTasks(items, now, zone)
        assertEquals(listOf("overdue", "later", "dateOnly"), today.map { it.id })
        assertTrue(today.first().overdue)
    }

    @Test fun `a task just ticked stays in place, struck through`() {
        val items = listOf(task("a", at("2026-11-11T11:00:00"), done = true), task("b", at("2026-11-11T12:00:00")))
        assertEquals(listOf("b"), todayTasks(items, now, zone).map { it.id })
        val ticked = todayTasks(items, now, zone, justDone = setOf("a"))
        assertEquals(listOf("a", "b"), ticked.map { it.id })
        assertTrue(ticked.first().done)
    }

    @Test fun `with today clear, the agenda falls through to the next day with tasks`() {
        val tomorrow = listOf(task("dateOnly", at("2026-11-12T00:00:00"), hasTime = false), task("timed", at("2026-11-12T09:00:00")))
        val agenda = nextTasks(tomorrow + task("later", at("2026-11-20T09:00:00")), now, zone)
        assertEquals("Tomorrow", agenda.label)
        assertEquals(listOf("timed", "dateOnly"), agenda.tasks.map { it.id })

        assertEquals("Sunday", nextTasks(listOf(task("sun", at("2026-11-15T09:00:00"))), now, zone).label)
        assertEquals(listOf("later"), nextTasks(listOf(task("later", at("2026-11-20T09:00:00"))), now, zone).tasks.map { it.id })
    }

    @Test fun `today wins over later days, and nothing dated leaves the agenda empty`() {
        val items = listOf(task("today", at("2026-11-11T15:00:00")), task("tomorrow", at("2026-11-12T09:00:00")))
        assertEquals(WidgetAgenda("Today", todayTasks(items, now, zone)), nextTasks(items, now, zone))
        assertEquals(WidgetAgenda(), nextTasks(listOf(task("undated", 0, hasTime = false, hasDate = false)), now, zone))
    }
}
