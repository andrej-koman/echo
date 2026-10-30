package dev.andrej.echo.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.andrej.echo.ai.AnalysisBlock
import dev.andrej.echo.data.db.EchoDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class SqlAnalysisQueueRepository(
    private val database: EchoDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AnalysisQueueRepository {

    private val queries = database.analysisQueueQueries

    override val pending: Flow<List<PendingAnalysis>> =
        queries.selectAll { transcriptId, enqueuedAt, attempts, lastAttemptAt, lastBlock, updatedAt ->
            PendingAnalysis(
                transcriptId = transcriptId,
                enqueuedAt = enqueuedAt,
                attempts = attempts.toInt(),
                lastAttemptAt = lastAttemptAt,
                lastBlock = lastBlock?.let(::decodeBlock),
                updatedAt = updatedAt,
            )
        }.asFlow().mapToList(ioDispatcher)

    override suspend fun enqueue(transcriptId: String, now: Long) = withContext(ioDispatcher) {
        queries.enqueue(transcriptId, now)
        Unit
    }

    override suspend fun recordBlock(
        transcriptId: String,
        block: AnalysisBlock,
        consumesAttempt: Boolean,
        now: Long,
    ) = withContext(ioDispatcher) {
        val consumed = if (consumesAttempt) 1L else 0L
        queries.recordBlock(consumed, now, Json.encodeToString(block), transcriptId)
        Unit
    }

    override suspend fun remove(transcriptId: String) = withContext(ioDispatcher) {
        queries.remove(transcriptId)
        Unit
    }

    override suspend fun clear() = withContext(ioDispatcher) {
        queries.clear()
        Unit
    }

    override suspend fun reset(transcriptId: String, now: Long) = withContext(ioDispatcher) {
        database.transaction {
            queries.enqueue(transcriptId, now)
            queries.reset(now, transcriptId)
        }
    }

    // A block from an older build that no longer decodes just shows no reason; the entry still runs.
    private fun decodeBlock(json: String): AnalysisBlock? =
        runCatching { Json.decodeFromString<AnalysisBlock>(json) }.getOrNull()
}
