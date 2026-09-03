package dev.andrej.echo.data

import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val id: String,
    val sourceTranscriptId: String,
    val text: String,
    val done: Boolean = false,
    val createdAt: Long,
)
