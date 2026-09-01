package dev.andrej.echo.data

import kotlinx.serialization.Serializable

@Serializable
data class Transcript(
    val id: String,
    val text: String,
    val language: String,
    val createdAt: Long,
    val durationMs: Long,
)
