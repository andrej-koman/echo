package dev.andrej.echo.data

import kotlinx.serialization.Serializable

@Serializable
data class TodoItem(
    val id: String,
    val sourceTranscriptId: String,
    val text: String,
    val dueAt: Long,
    val hasTime: Boolean,
    val notify: Boolean = true,
    val done: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    /** Tombstone. Set instead of dropping the row, so a delete can outlive one device. */
    val deletedAt: Long? = null,
)

/** What the analyzer produces, before the repository gives it an id. */
data class NewTodo(val text: String, val dueAt: Long, val hasTime: Boolean)
