package dev.andrej.echo.ai

/** Identifies a selectable analysis backend, independent of whether it happens to be usable right now. */
enum class LlmBackend(val id: String, val displayName: String) {
    NANO("nano", "Built-in AI"),
    LITERT("litert", "Qwen3 1.7B"),
    ;

    companion object {
        fun fromId(id: String?): LlmBackend? = entries.firstOrNull { it.id == id }
    }
}
