package dev.andrej.echo.ai

import kotlinx.serialization.Serializable

/**
 * Why an analysis did not produce anything. Persisted as-is against a queued transcript, so the
 * cases carry only what stays true while it waits — a download fraction would be stale before it
 * reached disk, and is read live off `ModelStore` instead.
 */
@Serializable
sealed interface AnalysisBlock {

    /** Nothing was said. Never worth retrying. */
    @Serializable
    data object EmptyTranscript : AnalysisBlock

    /** A backend exists but its weights are not on disk yet. */
    @Serializable
    data class WaitingForModel(val bytes: Long) : AnalysisBlock

    /** No backend can run on this device. A different one, later, may still be able to. */
    @Serializable
    data class NoBackend(val reason: String) : AnalysisBlock

    @Serializable
    data class GenerationFailed(val message: String?) : AnalysisBlock

    /** The model ran and parsed, but found nothing to extract. */
    @Serializable
    data object EmptyResult : AnalysisBlock
}

sealed interface AnalysisOutcome {
    data class Success(val analysis: Analysis) : AnalysisOutcome

    data class Blocked(val reason: AnalysisBlock) : AnalysisOutcome
}
