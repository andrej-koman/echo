package dev.andrej.echo.data

import dev.andrej.echo.ai.AnalysisBlock

/**
 * A transcript that still wants analysis. `attempts` only counts real failures
 * ([AnalysisBlock.GenerationFailed], [AnalysisBlock.EmptyResult]) — a device waiting on a model
 * download or with no backend at all is parked, not failing, so it never exhausts.
 */
data class PendingAnalysis(
    val transcriptId: String,
    val enqueuedAt: Long,
    val attempts: Int = 0,
    val lastAttemptAt: Long? = null,
    val lastBlock: AnalysisBlock? = null,
    val updatedAt: Long = enqueuedAt,
)
