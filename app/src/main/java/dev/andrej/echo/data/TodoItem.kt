package dev.andrej.echo.data

import kotlinx.serialization.Serializable

@Serializable
data class TodoItem(
    val id: String,
    val sourceTranscriptId: String,
    val text: String,
    val dueAt: Long,
    val hasTime: Boolean,
    val done: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
)

/** What the analyzer produces, before the repository gives it an id. */
data class NewTodo(val text: String, val dueAt: Long, val hasTime: Boolean)
