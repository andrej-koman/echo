package dev.andrej.echo.data

import dev.andrej.echo.data.db.EchoDatabase
import dev.andrej.echo.notify.dateReminderTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SqlTaskReminderRepository(private val database: EchoDatabase) {
    private val queries = database.todoQueries

    suspend fun retry(id: String, expectedAt: Long) = withContext(Dispatchers.IO) {
        queries.retryReminder(id, expectedAt)
    }

    suspend fun claim(id: String, expectedAt: Long, now: Long): TodoItem? = withContext(Dispatchers.IO) {
        database.transactionWithResult {
            val task = queries.selectById(id, ::TodoItem).executeAsOneOrNull()
            if (task == null || task.done || task.reminderDelivered || task.reminderAt != expectedAt || expectedAt > now) {
                null
            } else {
                queries.deliverReminder(id)
                task
            }
        }
    }

    suspend fun refreshDateOnly(now: Long, zone: ZoneId) = withContext(Dispatchers.IO) {
        database.transaction {
            queries.selectAll(::TodoItem).executeAsList()
                .filter { !it.done && !it.reminderDelivered && it.reminderAt != null && it.reminderDate != null }
                .forEach { task ->
                    val at = dateReminderTime(task.reminderDate!!, now, zone)
                    if (at != task.reminderAt) queries.updateReminder(at, task.reminderDate, false, task.id)
                }
        }
    }
}
