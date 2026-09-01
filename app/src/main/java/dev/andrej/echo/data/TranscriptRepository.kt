package dev.andrej.echo.data

import kotlinx.coroutines.flow.Flow

/**
 * Stores completed transcripts.
 *
 * Deliberately a small interface: the current implementation is a JSON file, but swapping in
 * a database later should not ripple past this file.
 */
interface TranscriptRepository {

    /** All transcripts, newest first. Emits again whenever the set changes. */
    val transcripts: Flow<List<Transcript>>

    suspend fun save(
        text: String,
        language: String,
        durationMs: Long,
        createdAt: Long = System.currentTimeMillis(),
    ): Transcript

    suspend fun delete(id: String)
}
