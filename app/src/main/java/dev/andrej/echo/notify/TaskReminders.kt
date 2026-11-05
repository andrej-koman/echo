package dev.andrej.echo.notify

import dev.andrej.echo.data.DerivedRepository
import dev.andrej.echo.data.SqlTaskReminderRepository
import dev.andrej.echo.data.TodoItem
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface TaskReminderDelivery {
    fun notificationsAllowed(): Boolean
    fun schedule(id: String, at: Long)
    fun cancelAlarm(id: String)
    fun cancelNotification(id: String)
    fun postedTaskIds(): Set<String>
    fun post(task: TodoItem, alert: Boolean = true): Boolean
}

class TaskReminders(
    private val tasks: DerivedRepository,
    private val repository: SqlTaskReminderRepository,
    private val delivery: TaskReminderDelivery,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val scheduled = mutableMapOf<String, Long>()
    private val shownText = mutableMapOf<String, String>()
    private val failed = mutableMapOf<String, Long>()

    suspend fun reconcile(refreshDates: Boolean = false, force: Boolean = false) = mutex.withLock {
        val time = now()
        if (force) failed.clear()
        if (refreshDates) repository.refreshDateOnly(time, ZoneId.systemDefault())
        val current = tasks.items.first()
        val incomplete = current.filterNot { it.done }.associateBy { it.id }
        val posted = delivery.postedTaskIds()
        posted.filterNot { it in incomplete }.forEach {
            delivery.cancelNotification(it)
            shownText.remove(it)
        }
        incomplete.values.filter { it.id in posted && it.reminderDelivered && shownText[it.id] != it.text }.forEach {
            if (runCatching { delivery.post(it, alert = false) }.getOrDefault(false)) shownText[it.id] = it.text
        }
        val pending = incomplete.values.filter { it.reminderAt != null && !it.reminderDelivered }.associateBy { it.id }
        failed.keys.retainAll(pending.keys)
        (scheduled.keys - pending.keys).forEach {
            delivery.cancelAlarm(it)
            scheduled.remove(it)
        }
        pending.values.forEach { task ->
            val at = task.reminderAt!!
            if (at <= time) {
                deliverLocked(task.id, at, time)
            } else if (force || scheduled[task.id] != at) {
                delivery.schedule(task.id, at)
                scheduled[task.id] = at
            }
        }
    }

    suspend fun deliver(id: String, expectedAt: Long) = mutex.withLock {
        deliverLocked(id, expectedAt, now())
    }

    private suspend fun deliverLocked(id: String, expectedAt: Long, time: Long) {
        if (failed[id] == expectedAt) return
        if (!delivery.notificationsAllowed()) return
        val task = repository.claim(id, expectedAt, time) ?: return
        if (runCatching { delivery.post(task) }.getOrDefault(false)) {
            shownText[id] = task.text
            delivery.cancelAlarm(id)
            scheduled.remove(id)
        } else {
            // Resetting the claim emits a database update; wait for an explicit refresh to retry.
            failed[id] = expectedAt
            repository.retry(id, expectedAt)
        }
    }
}
