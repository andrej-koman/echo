package dev.andrej.echo.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.andrej.echo.data.db.EchoDatabase
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SqlTranscriptRepository(
    private val database: EchoDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TranscriptRepository {

    private val queries = database.transcriptQueries
    private val segmentQueries = database.segmentQueries

    override val transcripts: Flow<List<Transcript>> =
        queries.selectAll(::Transcript).asFlow().mapToList(ioDispatcher)

    override suspend fun save(
        text: String,
        language: String,
        durationMs: Long,
        createdAt: Long,
        title: String?,
    ): Transcript {
        val transcript = Transcript(
            id = UUID.randomUUID().toString(),
            text = text,
            language = language,
            createdAt = createdAt,
            durationMs = durationMs,
            title = title,
        )
        withContext(ioDispatcher) {
            queries.insert(transcript.id, text, language, createdAt, durationMs, title)
        }
        return transcript
    }

    override suspend fun setTranscription(
        id: String,
        text: String,
        language: String,
        durationMs: Long,
        segments: List<Segment>,
    ) = withContext(ioDispatcher) {
        database.transaction {
            queries.setTranscription(text, language, durationMs, id)
            segmentQueries.deleteFor(id)
            segments.forEachIndexed { i, segment ->
                segmentQueries.insert(id, i.toLong(), segment.startMs, segment.endMs, segment.text)
            }
        }
    }

    override suspend fun segments(id: String): List<Segment> = withContext(ioDispatcher) {
        segmentQueries.selectFor(id, ::Segment).executeAsList()
    }

    override suspend fun attachAnalysis(
        id: String,
        title: String?,
        summary: String?,
        analyzedAt: Long,
    ) = withContext(ioDispatcher) {
        queries.attachAnalysis(title, summary, analyzedAt, id)
        Unit
    }

    override suspend fun delete(id: String) = withContext(ioDispatcher) {
        database.transaction {
            segmentQueries.deleteFor(id)
            queries.delete(id)
        }
    }

    override suspend fun clear() = withContext(ioDispatcher) {
        database.transaction {
            segmentQueries.clear()
            queries.clear()
        }
    }
}
