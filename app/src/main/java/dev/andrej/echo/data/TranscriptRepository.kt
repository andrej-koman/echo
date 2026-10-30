package dev.andrej.echo.data

import kotlinx.coroutines.flow.Flow

interface TranscriptRepository {

    val transcripts: Flow<List<Transcript>>

    suspend fun save(
        text: String,
        language: String,
        durationMs: Long,
        createdAt: Long = System.currentTimeMillis(),
        title: String? = null,
    ): Transcript

    /** Rewritten after every transcribed chunk, so a long import fills in as it goes. */
    suspend fun setTranscription(
        id: String,
        text: String,
        language: String,
        durationMs: Long,
        segments: List<Segment>,
    )

    suspend fun segments(id: String): List<Segment>

    suspend fun attachAnalysis(
        id: String,
        title: String?,
        summary: String?,
        analyzedAt: Long = System.currentTimeMillis(),
    )

    suspend fun delete(id: String)

    suspend fun clear()
}
