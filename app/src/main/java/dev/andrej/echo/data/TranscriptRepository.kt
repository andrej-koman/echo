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

    suspend fun delete(id: String)
}
