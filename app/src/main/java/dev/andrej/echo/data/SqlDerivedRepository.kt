package dev.andrej.echo.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.andrej.echo.data.db.EchoDatabase
import dev.andrej.echo.data.db.Todo
import java.util.UUID
import java.time.ZoneId
import java.time.Instant
import dev.andrej.echo.notify.TaskReminderTime
import dev.andrej.echo.notify.dateReminderTime
import dev.andrej.echo.notify.taskReminderTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SqlDerivedRepository(
    private val database: EchoDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : DerivedRepository {

    private val queries = database.todoQueries

    override val items: Flow<List<TodoItem>> =
        queries.selectAll(::TodoItem).asFlow().mapToList(ioDispatcher)

    override suspend fun replaceFor(
        transcriptId: String,
        items: List<NewTodo>,
        createdAt: Long,
    ) = withContext(ioDispatcher) {
        database.transaction {
            val before = queries.selectFor(transcriptId, ::TodoItem).executeAsList().associateBy { it.id }
            val savedAt = now()
            queries.deleteFor(transcriptId)
            items.distinctBy { derivedId(transcriptId, it.text) }.forEach { newTodo ->
                val id = derivedId(transcriptId, newTodo.text)
                val old = before[id]
                val changed = old == null || scheduleChanged(old, newTodo.dueAt, newTodo.hasTime, newTodo.hasDate)
                val reminder = if (changed) taskReminderTime(newTodo.dueAt, newTodo.hasTime, newTodo.hasDate, savedAt, zone()) else null
                queries.insert(
                    Todo(
                        id = id,
                        sourceTranscriptId = transcriptId,
                        text = newTodo.text,
                        dueAt = newTodo.dueAt,
                        hasTime = newTodo.hasTime,
                        hasDate = newTodo.hasDate,
                        done = old?.done ?: false,
                        createdAt = old?.createdAt ?: createdAt,
                        updatedAt = savedAt,
                        sourceOffsetMs = newTodo.sourceOffsetMs,
                        reminderAt = if (changed) reminder?.at else old.reminderAt,
                        reminderDate = if (changed) reminder?.date else old.reminderDate,
                        reminderDelivered = if (changed) false else old.reminderDelivered,
                    ),
                )
            }
        }
    }

    /** Keyed by what produced the row, not minted, so re-analysis keeps ids and [TodoItem.done]. */
    private fun derivedId(transcriptId: String, text: String): String =
        UUID.nameUUIDFromBytes(
            "$transcriptId\u0000${text.trim().lowercase()}".toByteArray(),
        ).toString()

    override suspend fun setDone(itemId: String, done: Boolean) = withContext(ioDispatcher) {
        database.transaction {
            val old = queries.selectById(itemId, ::TodoItem).executeAsOneOrNull()
            val savedAt = now()
            queries.setDone(done, savedAt, itemId)
            if (old?.done == true && !done) {
                val reminder = if (old.reminderDate != null) {
                    TaskReminderTime(dateReminderTime(old.reminderDate, savedAt, zone()), old.reminderDate)
                } else if (old.reminderAt != null) {
                    TaskReminderTime(maxOf(savedAt, old.reminderAt))
                } else taskReminderTime(old.dueAt, old.hasTime, old.hasDate, savedAt, zone())
                queries.updateReminder(reminder.at, reminder.date, false, itemId)
            }
        }
        Unit
    }

    override suspend fun update(itemId: String, text: String, dueAt: Long, hasTime: Boolean, hasDate: Boolean) =
        withContext(ioDispatcher) {
            database.transaction {
                val old = queries.selectById(itemId, ::TodoItem).executeAsOneOrNull()
                val savedAt = now()
                queries.update(text, dueAt, hasTime, hasDate, savedAt, itemId)
                if (old != null && scheduleChanged(old, dueAt, hasTime, hasDate)) {
                    val reminder = taskReminderTime(dueAt, hasTime, hasDate, savedAt, zone())
                    queries.updateReminder(reminder.at, reminder.date, false, itemId)
                }
            }
            Unit
        }

    override suspend fun delete(itemId: String) = withContext(ioDispatcher) {
        queries.delete(itemId)
        Unit
    }

    override suspend fun deleteFor(transcriptId: String) = withContext(ioDispatcher) {
        queries.deleteFor(transcriptId)
        Unit
    }

    override suspend fun clear() = withContext(ioDispatcher) {
        queries.clear()
        Unit
    }

    private fun scheduleChanged(old: TodoItem, dueAt: Long, hasTime: Boolean, hasDate: Boolean): Boolean {
        if (old.hasDate != hasDate) return true
        if (!hasDate) return false
        if (old.hasTime != hasTime) return true
        if (old.dueAt == dueAt) return false
        if (hasTime) return old.dueAt != dueAt
        val oldDate = old.reminderDate ?: Instant.ofEpochMilli(old.dueAt).atZone(zone()).toLocalDate().toString()
        return oldDate != Instant.ofEpochMilli(dueAt).atZone(zone()).toLocalDate().toString()
    }
}
