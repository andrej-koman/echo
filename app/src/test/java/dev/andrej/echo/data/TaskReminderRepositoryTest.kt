package dev.andrej.echo.data

import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TaskReminderRepositoryTest {
    private var zone = ZoneId.of("Europe/Ljubljana")
    private fun at(value: String, zone: ZoneId = this.zone) =
        LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
    private var now = at("2026-11-05T12:00:00")
    private val database = testDatabase()
    private val repository = SqlDerivedRepository(database, now = { now }, zone = { zone })
    private val reminders = SqlTaskReminderRepository(database)
    private fun undated() = NewTodo("Call mum", 0, false, false)
    private suspend fun task() = repository.items.first().single()

    @Test fun `undated reminder starts an hour after save not recording`() = runTest {
        repository.replaceFor("note", listOf(undated()), createdAt = 1)
        assertEquals(now + 3_600_000, task().reminderAt)
        assertFalse(task().hasDate)
    }

    @Test fun `date-only reminder is nine in the local timezone`() = runTest {
        repository.replaceFor("note", listOf(NewTodo("Dentist", at("2026-11-06T00:00:00"), false, true)))
        assertEquals(at("2026-11-06T09:00:00"), task().reminderAt)
        assertEquals("2026-11-06", task().reminderDate)
    }

    @Test fun `explicit time and overdue times`() = runTest {
        repository.replaceFor("note", listOf(NewTodo("Dentist", now + 60_000, true, true)))
        assertEquals(now + 60_000, task().reminderAt)
        repository.update(task().id, "Dentist", now - 60_000, true, true)
        assertEquals(now, task().reminderAt)
    }

    @Test fun `reanalysis and text edits preserve countdown and first creation`() = runTest {
        repository.replaceFor("note", listOf(undated()), createdAt = 1)
        val original = task()
        now += 1_800_000
        repository.replaceFor("note", listOf(undated()), createdAt = 2)
        assertEquals(original.reminderAt, task().reminderAt)
        assertEquals(original.createdAt, task().createdAt)
        repository.update(task().id, "Phone mum", 0, false, false)
        assertEquals(original.reminderAt, task().reminderAt)
    }

    @Test fun `delivery claim is once and reanalysis does not rearm`() = runTest {
        repository.replaceFor("note", listOf(undated()))
        val original = task()
        assertNull(reminders.claim(original.id, original.reminderAt!!, now))
        now = original.reminderAt
        assertNotNull(reminders.claim(original.id, now, now))
        assertNull(reminders.claim(original.id, now, now))
        repository.replaceFor("note", listOf(undated()))
        assertTrue(task().reminderDelivered)
    }

    @Test fun `completion deletion and stale alarm cannot claim`() = runTest {
        repository.replaceFor("note", listOf(undated()))
        val original = task()
        now = original.reminderAt!!
        repository.setDone(original.id, true)
        assertNull(reminders.claim(original.id, now, now))
        repository.setDone(original.id, false)
        assertEquals(now, task().reminderAt)
        assertNotNull(reminders.claim(original.id, now, now))
        repository.update(original.id, "Call mum", now + 100_000, true, true)
        assertNull(reminders.claim(original.id, now, now))
        assertFalse(task().reminderDelivered)
        repository.delete(original.id)
        assertNull(reminders.claim(original.id, now + 100_000, now + 100_000))
    }

    @Test fun `timezone recalculation keeps stated date through daylight saving`() = runTest {
        now = at("2026-03-28T12:00:00")
        repository.replaceFor("note", listOf(NewTodo("Dentist", at("2026-03-29T00:00:00"), false, true)))
        assertEquals(at("2026-03-29T09:00:00"), task().reminderAt)
        val newZone = ZoneId.of("America/New_York")
        reminders.refreshDateOnly(now, newZone)
        assertEquals(at("2026-03-29T09:00:00", newZone), task().reminderAt)
        assertEquals("2026-03-29", task().reminderDate)
    }

    @Test fun `legacy task stays without reminder until scheduling edit or reopening`() = runTest {
        repository.replaceFor("note", listOf(undated()))
        database.todoQueries.updateReminder(null, null, false, task().id)
        repository.replaceFor("note", listOf(undated()))
        assertNull(task().reminderAt)
        repository.update(task().id, "New text", 0, false, false)
        assertNull(task().reminderAt)
        repository.update(task().id, "New text", now + 60_000, true, true)
        assertEquals(now + 60_000, task().reminderAt)
    }

    @Test fun `reopening date-only task uses current timezone`() = runTest {
        repository.replaceFor("note", listOf(NewTodo("Dentist", at("2026-11-06T00:00:00"), false, true)))
        repository.setDone(task().id, true)
        zone = ZoneId.of("America/New_York")
        repository.setDone(task().id, false)
        assertEquals(at("2026-11-06T09:00:00"), task().reminderAt)
    }

    @Test fun `same stated date reanalysis in new timezone does not repeat delivery`() = runTest {
        repository.replaceFor("note", listOf(NewTodo("Dentist", at("2026-11-06T00:00:00"), false, true)))
        now = task().reminderAt!!
        reminders.claim(task().id, now, now)
        zone = ZoneId.of("America/New_York")
        repository.replaceFor("note", listOf(NewTodo("Dentist", at("2026-11-06T00:00:00"), false, true)))
        assertTrue(task().reminderDelivered)
    }

    @Test fun `text-only edit after travel keeps original local date and delivered state`() = runTest {
        repository.replaceFor("note", listOf(NewTodo("Dentist", at("2026-11-06T00:00:00"), false, true)))
        now = task().reminderAt!!
        reminders.claim(task().id, now, now)
        zone = ZoneId.of("America/New_York")
        val old = task()
        repository.update(old.id, "New dentist", old.dueAt, old.hasTime, old.hasDate)
        assertTrue(task().reminderDelivered)
        assertEquals("2026-11-06", task().reminderDate)
    }
}
