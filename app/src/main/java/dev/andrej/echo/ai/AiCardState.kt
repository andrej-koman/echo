package dev.andrej.echo.ai

/**
 * What the AccountScreen AI card shows. Collapses the two backends into one set of states —
 * the screen does not need to know which runner is behind [NanoReady] vs [LiteRtReady].
 */
sealed interface AiCardState {
    /** Probing Nano costs an IPC call; the card is briefly empty while it resolves. */
    data object Checking : AiCardState

    data object NanoReady : AiCardState

    data class NeedsDownload(val bytes: Long) : AiCardState

    data class Downloading(val fraction: Float) : AiCardState

    data object LiteRtReady : AiCardState

    data class Unsupported(val reason: String) : AiCardState
}

fun LlmAvailability.toCardState(): AiCardState = when (this) {
    is LlmAvailability.Ready -> AiCardState.LiteRtReady
    is LlmAvailability.NeedsDownload -> AiCardState.NeedsDownload(bytes)
    is LlmAvailability.Downloading -> AiCardState.Downloading(fraction)
    is LlmAvailability.Unsupported -> AiCardState.Unsupported(reason)
}
