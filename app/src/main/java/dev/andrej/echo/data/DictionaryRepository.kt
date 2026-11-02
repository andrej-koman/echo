package dev.andrej.echo.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.andrej.echo.data.db.EchoDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class DictionaryTerm(val id: Long, val term: String)

/** Names and jargon Whisper should spell right; they go into its initial prompt. */
class DictionaryRepository(
    database: EchoDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val queries = database.dictionaryTermQueries

    val terms: Flow<List<DictionaryTerm>> =
        queries.selectAll(::DictionaryTerm).asFlow().mapToList(ioDispatcher)

    suspend fun all(): List<DictionaryTerm> = withContext(ioDispatcher) {
        queries.selectAll(::DictionaryTerm).executeAsList()
    }

    suspend fun add(term: String) = withContext(ioDispatcher) { queries.insert(term.trim()); Unit }

    suspend fun rename(id: Long, term: String) = withContext(ioDispatcher) { queries.update(term.trim(), id); Unit }

    suspend fun delete(id: Long) = withContext(ioDispatcher) { queries.delete(id); Unit }
}
