package dev.andrej.echo.data


data class Transcript(
    val id: String,
    val text: String,
    val language: String,
    val createdAt: Long,
    val durationMs: Long,
    val title: String? = null,
    val summary: String? = null,
    val analyzedAt: Long? = null,
    /** Null until polished; the original stays in [text]. */
    val polishedText: String? = null,
)
