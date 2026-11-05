package dev.andrej.echo.notify

import dev.andrej.echo.data.NewTodo
import dev.andrej.echo.data.SqlDerivedRepository
import dev.andrej.echo.data.SqlTaskReminderRepository
import dev.andrej.echo.data.TodoItem
import dev.andrej.echo.data.testDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TaskRemindersTest {
    private var now = 1_000_000L
    private val database = testDatabase()
    private val repository = SqlDerivedRepository(database, now = { now })
    private val delivery = FakeDelivery()
    private fun coordinator() = TaskReminders(repository, SqlTaskReminderRepository(database), delivery, now = { now })
    private suspend fun task() = repository.items.first().single()
    private suspend fun create() = repository.replaceFor("note", listOf(NewTodo("Call mum", 0, false, false)))

    @Test fun `restore pending alarm after process restart and deliver once`() = runTest {
        create()
        val reminders = coordinator()
        reminders.reconcile()
        val task = task()
        assertEquals(task.reminderAt, delivery.alarms[task.id])
        delivery.alarms.clear()
        coordinator().reconcile()
        assertEquals(task.reminderAt, delivery.alarms[task.id])
        now = task.reminderAt!!
        reminders.deliver(task.id, now)
        reminders.deliver(task.id, now)
        coordinator().reconcile()
        assertEquals(1, delivery.posts)
        assertTrue(delivery.alarms.isEmpty())
    }

    @Test fun `completion and deletion cancel alarms and visible notifications`() = runTest {
        create()
        val reminders = coordinator()
        reminders.reconcile()
        val id = task().id
        repository.setDone(id, true)
        reminders.reconcile()
        assertTrue(delivery.alarms.isEmpty())
        repository.setDone(id, false)
        now = task().reminderAt!!
        reminders.reconcile()
        assertTrue(id in delivery.visible)
        repository.delete(id)
        reminders.reconcile()
        assertTrue(delivery.visible.isEmpty())
    }

    @Test fun `permission denial preserves overdue reminder for later grant`() = runTest {
        create()
        val reminders = coordinator()
        delivery.allowed = false
        now = task().reminderAt!!
        reminders.reconcile()
        assertFalse(task().reminderDelivered)
        assertEquals(0, delivery.posts)
        delivery.allowed = true
        reminders.reconcile()
        assertTrue(task().reminderDelivered)
        assertEquals(1, delivery.posts)
    }

    @Test fun `stale broadcast after edit does not deliver new reminder early`() = runTest {
        create()
        val reminders = coordinator()
        reminders.reconcile()
        val original = task()
        now = original.reminderAt!!
        repository.update(original.id, "Call mum", now + 60_000, true, true)
        reminders.deliver(original.id, now)
        assertEquals(0, delivery.posts)
        reminders.reconcile()
        assertEquals(now + 60_000, delivery.alarms[original.id])
    }

    @Test fun `failed notification post keeps reminder available for retry`() = runTest {
        create()
        val reminders = coordinator()
        now = task().reminderAt!!
        delivery.failPost = true
        reminders.reconcile()
        assertFalse(task().reminderDelivered)
        delivery.failPost = false
        reminders.reconcile(force = true)
        assertTrue(task().reminderDelivered)
        assertEquals(1, delivery.posts)
    }

    @Test fun `persistent posting failure does not spin database collector`() = runBlocking {
        create()
        now = task().reminderAt!!
        delivery.failPost = true
        val reminders = coordinator()
        val collector = launch(Dispatchers.Default) { repository.items.collect { reminders.reconcile() } }
        try {
            withTimeout(5_000) { delivery.attempted.await() }
            delay(200)
            assertEquals(1, delivery.attempts.get())
            assertFalse(task().reminderDelivered)
        } finally {
            collector.cancelAndJoin()
        }
    }

    private class FakeDelivery : TaskReminderDelivery {
        val alarms = mutableMapOf<String, Long>()
        val visible = mutableSetOf<String>()
        var posts = 0
        var allowed = true
        var failPost = false
        val attempts = AtomicInteger()
        val attempted = CompletableDeferred<Unit>()
        override fun notificationsAllowed() = allowed
        override fun schedule(id: String, at: Long) { alarms[id] = at }
        override fun cancelAlarm(id: String) { alarms.remove(id) }
        override fun cancelNotification(id: String) { visible.remove(id) }
        override fun postedTaskIds() = visible.toSet()
        override fun post(task: TodoItem, alert: Boolean): Boolean {
            attempts.incrementAndGet()
            attempted.complete(Unit)
            if (failPost) throw SecurityException("Permission revoked")
            if (alert) posts++
            visible.add(task.id)
            return true
        }
    }
}
