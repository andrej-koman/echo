package dev.andrej.echo.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.andrej.echo.data.db.EchoDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
        id: String,
        text: String,
        language: String,
        durationMs: Long,
        createdAt: Long,
        title: String?,
    ): Transcript {
        val transcript = Transcript(
            id = id,
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
            replaceSegments(id, segments)
        }
    }

    override suspend fun editSegments(id: String, segments: List<Segment>) = withContext(ioDispatcher) {
        database.transaction {
            queries.setText(segments.joinToString(" ") { it.text }, id)
            replaceSegments(id, segments)
        }
    }

    private fun replaceSegments(id: String, segments: List<Segment>) {
        segmentQueries.deleteFor(id)
        segmentQueries.deleteFtsFor(id)
        segments.forEachIndexed { i, segment ->
            segmentQueries.insert(id, i.toLong(), segment.startMs, segment.endMs, segment.text)
            segmentQueries.insertFts(id, segment.text)
        }
    }

    override fun segmentsFlow(id: String): Flow<List<Segment>> =
        segmentQueries.selectFor(id, ::Segment).asFlow().mapToList(ioDispatcher)

    override fun search(text: String): Flow<Set<String>> {
        val like = text.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return queries.search(like, ftsMatch(text)).asFlow().mapToList(ioDispatcher).map { it.toSet() }
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

    override suspend fun setPolished(id: String, text: String?) = withContext(ioDispatcher) {
        queries.setPolished(text, id)
        Unit
    }

    override suspend fun delete(id: String) = withContext(ioDispatcher) {
        database.transaction {
            segmentQueries.deleteFor(id)
            segmentQueries.deleteFtsFor(id)
            queries.delete(id)
        }
    }

    override suspend fun clear() = withContext(ioDispatcher) {
        database.transaction {
            segmentQueries.clear()
            segmentQueries.clearFts()
            queries.clear()
        }
    }
}

/**
 * Each word becomes a prefix term, so a half-typed word still matches. Lowercased because FTS
 * reads a bare AND/OR/NOT as an operator; anything but letters and digits is dropped.
 */
internal fun ftsMatch(text: String): String =
    text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.joinToString(" ") { "$it*" }
