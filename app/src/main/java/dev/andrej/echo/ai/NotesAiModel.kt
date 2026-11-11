package dev.andrej.echo.ai

enum class NotesAiModel(
    val id: String,
    val title: String,
    val spec: ModelSpec,
    /** Writes good title, summary and tasks in a non-English note's own language. */
    val writesNoteLanguage: Boolean = false,
) {
    Fast("qwen3_0_6b", "Qwen3 0.6B · Fast", QWEN3_0_6B_INT4),
    Default("qwen3_1_7b", "Qwen3 1.7B", QWEN3_1_7B_INT4),
    Multilingual("gemma4_e2b", "Gemma 4 E2B · Multilingual", GEMMA4_E2B, writesNoteLanguage = true),
    ;

    companion object {
        fun fromId(id: String?): NotesAiModel = entries.firstOrNull { it.id == id } ?: Default
    }
}
