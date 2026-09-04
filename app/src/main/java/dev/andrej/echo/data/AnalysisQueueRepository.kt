package dev.andrej.echo.data

import dev.andrej.echo.ai.AnalysisBlock
import kotlinx.coroutines.flow.Flow

interface AnalysisQueueRepository {

    /** Oldest first. Emits again whenever the set changes. */
    val pending: Flow<List<PendingAnalysis>>

    /** No-op if the transcript is already queued. */
    suspend fun enqueue(transcriptId: String, now: Long = System.currentTimeMillis())

    /** No-op if the transcript is not queued. */
    suspend fun recordBlock(
        transcriptId: String,
        block: AnalysisBlock,
        consumesAttempt: Boolean,
        now: Long = System.currentTimeMillis(),
    )

    suspend fun remove(transcriptId: String)

    /** Queues the transcript if it isn't already, and clears its attempt count either way. */
    suspend fun reset(transcriptId: String, now: Long = System.currentTimeMillis())
}
