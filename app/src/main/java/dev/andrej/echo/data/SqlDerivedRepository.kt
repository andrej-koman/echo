package dev.andrej.echo.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.andrej.echo.data.db.EchoDatabase
import dev.andrej.echo.data.db.Todo
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SqlDerivedRepository(
    private val database: EchoDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
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
            val doneBefore = queries.doneFor(transcriptId).executeAsList().associate { it.id to it.done }
            queries.deleteFor(transcriptId)
            items.distinctBy { derivedId(transcriptId, it.text) }.forEach { newTodo ->
                val id = derivedId(transcriptId, newTodo.text)
                queries.insert(
                    Todo(
                        id = id,
                        sourceTranscriptId = transcriptId,
                        text = newTodo.text,
                        dueAt = newTodo.dueAt,
                        hasTime = newTodo.hasTime,
                        hasDate = newTodo.hasDate,
                        done = doneBefore[id] ?: false,
                        createdAt = createdAt,
                        updatedAt = createdAt,
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
        queries.setDone(done, System.currentTimeMillis(), itemId)
        Unit
    }

    override suspend fun update(itemId: String, text: String, dueAt: Long, hasTime: Boolean, hasDate: Boolean) =
        withContext(ioDispatcher) {
            queries.update(text, dueAt, hasTime, hasDate, System.currentTimeMillis(), itemId)
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
}
