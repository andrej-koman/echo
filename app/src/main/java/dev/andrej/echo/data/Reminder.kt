package dev.andrej.echo.data

import kotlinx.serialization.Serializable

/** @param dueAt null when the speaker named no time, or named one we could not resolve. */
@Serializable
data class Reminder(
    val id: String,
    val sourceTranscriptId: String,
    val text: String,
    val dueAt: Long?,
    val createdAt: Long,
)
