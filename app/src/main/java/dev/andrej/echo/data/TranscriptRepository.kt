package dev.andrej.echo.data

import kotlinx.coroutines.flow.Flow

interface TranscriptRepository {

    /** Newest first. Emits again whenever the set changes. */
    val transcripts: Flow<List<Transcript>>

    suspend fun save(
        text: String,
        language: String,
        durationMs: Long,
        createdAt: Long = System.currentTimeMillis(),
    ): Transcript

    /** No-op when the id is unknown. */
    suspend fun attachAnalysis(
        id: String,
        title: String?,
        summary: String?,
        analyzedAt: Long = System.currentTimeMillis(),
    )

    suspend fun delete(id: String)
}
