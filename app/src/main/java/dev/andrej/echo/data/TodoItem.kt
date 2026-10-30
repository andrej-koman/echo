package dev.andrej.echo.data


data class TodoItem(
    val id: String,
    val sourceTranscriptId: String,
    val text: String,
    val dueAt: Long,
    val hasTime: Boolean,
    /** False means no date/time was ever stated — [dueAt] is a placeholder, never read. */
    val hasDate: Boolean = true,
    val done: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
)

/** What the analyzer produces, before the repository gives it an id. */
data class NewTodo(val text: String, val dueAt: Long, val hasTime: Boolean, val hasDate: Boolean)
