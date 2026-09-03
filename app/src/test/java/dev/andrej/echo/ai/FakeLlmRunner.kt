package dev.andrej.echo.ai

/** Replays canned model output, one entry per call, then repeats the last. */
class FakeLlmRunner(
    private val responses: List<String> = emptyList(),
    private val availability: LlmAvailability = LlmAvailability.Ready,
    private val throwOnGenerate: Boolean = false,
) : LlmRunner {

    val prompts = mutableListOf<String>()
    val temperatures = mutableListOf<Float>()

    override suspend fun availability(): LlmAvailability = availability

    override suspend fun warmup() = Unit

    override suspend fun generate(prompt: String, temperature: Float): String {
        prompts += prompt
        temperatures += temperature
        if (throwOnGenerate) error("model exploded")
        return responses.getOrElse(prompts.size - 1) { responses.lastOrNull().orEmpty() }
    }
}
