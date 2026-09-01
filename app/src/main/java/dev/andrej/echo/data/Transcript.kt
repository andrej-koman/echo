package dev.andrej.echo.data

import kotlinx.serialization.Serializable

/**
 * One completed transcription session.
 *
 * @param createdAt epoch milliseconds, used for both display and ordering.
 */
@Serializable
data class Transcript(
    val id: String,
    val text: String,
    val language: String,
    val createdAt: Long,
    val durationMs: Long,
)
