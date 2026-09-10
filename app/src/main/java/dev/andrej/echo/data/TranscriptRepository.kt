package dev.andrej.echo.data

import kotlinx.coroutines.flow.Flow

interface TranscriptRepository {

    val transcripts: Flow<List<Transcript>>

    suspend fun save(
        text: String,
        language: String,
        durationMs: Long,
        createdAt: Long = System.currentTimeMillis(),
    ): Transcript

    suspend fun attachAnalysis(
        id: String,
        title: String?,
        summary: String?,
        analyzedAt: Long = System.currentTimeMillis(),
    )

    suspend fun delete(id: String)

    suspend fun allForSync(): List<Transcript>

    suspend fun upsertFromSync(transcript: Transcript)
}
