package dev.andrej.echo.ai

sealed interface AiCardState {
    /** The first availability check reads the model file; the card is briefly empty meanwhile. */
    data object Checking : AiCardState

    data class NeedsDownload(val bytes: Long) : AiCardState

    data class Downloading(val fraction: Float) : AiCardState

    data object Ready : AiCardState

    data class Unsupported(val reason: String) : AiCardState
}

fun LlmAvailability.toCardState(): AiCardState = when (this) {
    is LlmAvailability.Ready -> AiCardState.Ready
    is LlmAvailability.NeedsDownload -> AiCardState.NeedsDownload(bytes)
    is LlmAvailability.Downloading -> AiCardState.Downloading(fraction)
    is LlmAvailability.Unsupported -> AiCardState.Unsupported(reason)
}
