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
    private val speakerQueries = database.speakerQueries

    override val transcripts: Flow<List<Transcript>> =
        queries.selectAll(::Transcript).asFlow().mapToList(ioDispatcher)

    override suspend fun save(
        id: String,
        text: String,
        language: String,
        durationMs: Long,
        createdAt: Long,
        title: String?,
        titleByUser: Boolean,
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
            queries.insert(transcript.id, text, language, createdAt, durationMs, title, if (titleByUser) 1L else 0L)
        }
        return transcript
    }

    override suspend fun setTranscription(
        id: String,
        text: String,
        language: String,
        durationMs: Long,
        segments: List<Segment>,
        preserveSpeakers: Boolean,
    ) = withContext(ioDispatcher) {
        database.transaction {
            queries.setTranscription(text, language, durationMs, id)
            if (!preserveSpeakers) speakerQueries.deleteFor(id)
            replaceSegments(id, segments)
        }
    }

    override suspend fun completeTranscription(id: String, untilMs: Long?, analyze: Boolean, now: Long) = withContext(ioDispatcher) {
        database.transaction {
            val completingCap = untilMs == null && queries.selectCap(id).executeAsOneOrNull() != null
            queries.setTranscribedUntil(untilMs, id)
            if (completingCap) {
                queries.invalidateAnalysis(id)
                if (analyze) {
                    database.analysisQueueQueries.enqueue(id, now)
                    database.analysisQueueQueries.reset(now, id)
                } else database.analysisQueueQueries.remove(id)
            }
        }
    }

    override suspend fun setTranscribedUntil(id: String, untilMs: Long?) = withContext(ioDispatcher) {
        queries.setTranscribedUntil(untilMs, id)
        Unit
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
            segmentQueries.insert(id, i.toLong(), segment.startMs, segment.endMs, segment.text, segment.speaker?.toLong())
            segmentQueries.insertFts(id, segment.text)
        }
    }

    override fun segmentsFlow(id: String): Flow<List<Segment>> =
        segmentQueries.selectFor(id, ::segment).asFlow().mapToList(ioDispatcher)

    override suspend fun setSpeakers(id: String, segments: List<Segment>, count: Int, preserveExisting: Boolean) = withContext(ioDispatcher) {
        database.transaction {
            if (!preserveExisting) speakerQueries.deleteFor(id)
            repeat(count) { speakerQueries.ensure(id, it.toLong()) }
            replaceSegments(id, segments)
        }
    }

    override fun speakers(id: String): Flow<List<Speaker>> =
        speakerQueries.selectFor(id) { idx, name -> Speaker(idx.toInt(), name) }.asFlow().mapToList(ioDispatcher)

    override val speakerNames: Flow<List<String>> =
        speakerQueries.names().asFlow().mapToList(ioDispatcher)

    override suspend fun addSpeaker(id: String): Int = withContext(ioDispatcher) {
        database.transactionWithResult {
            val next = speakerQueries.nextIdx(id).executeAsOne()
            speakerQueries.insert(id, next)
            next.toInt()
        }
    }

    override suspend fun renameSpeaker(id: String, index: Int, name: String?) = withContext(ioDispatcher) {
        speakerQueries.rename(name?.trim()?.ifEmpty { null }, id, index.toLong())
        Unit
    }

    override fun search(text: String): Flow<Set<String>> {
        val like = text.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return queries.search(like, ftsMatch(text)).asFlow().mapToList(ioDispatcher).map { it.toSet() }
    }

    override suspend fun segments(id: String): List<Segment> = withContext(ioDispatcher) {
        segmentQueries.selectFor(id, ::segment).executeAsList()
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

    override suspend fun rename(id: String, title: String) = withContext(ioDispatcher) {
        val trimmed = title.trim()
        if (trimmed.isNotEmpty()) queries.rename(trimmed, id)
        Unit
    }

    override suspend fun moveToFolder(id: String, folderId: String?) = withContext(ioDispatcher) {
        queries.setFolder(folderId, id)
        Unit
    }

    override suspend fun delete(id: String) = withContext(ioDispatcher) {
        database.transaction {
            segmentQueries.deleteFor(id)
            segmentQueries.deleteFtsFor(id)
            speakerQueries.deleteFor(id)
            queries.delete(id)
        }
    }

    override suspend fun clear() = withContext(ioDispatcher) {
        database.transaction {
            segmentQueries.clear()
            segmentQueries.clearFts()
            speakerQueries.clear()
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

private fun segment(startMs: Long, endMs: Long, text: String, speaker: Long?) =
    Segment(startMs, endMs, text, speaker?.toInt())
