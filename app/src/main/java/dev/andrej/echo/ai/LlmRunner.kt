package dev.andrej.echo.ai

sealed interface LlmAvailability {
    data object Ready : LlmAvailability

    data class NeedsDownload(val bytes: Long) : LlmAvailability

    data class Downloading(val fraction: Float) : LlmAvailability

    /** This device cannot run this backend at all. Try the next one. */
    data class Unsupported(val reason: String) : LlmAvailability
}

/**
 * A text-in, text-out language model. The abstraction sits below the analysis so that both
 * backends share one prompt and one parser.
 */
interface LlmRunner {

    suspend fun availability(): LlmAvailability

    /** Pulls the model into memory so the first real call is not the slow one. Best effort. */
    suspend fun warmup()

    suspend fun generate(prompt: String, temperature: Float = DEFAULT_TEMPERATURE): String

    companion object {
        const val DEFAULT_TEMPERATURE = 0.2f
    }
}
