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
    val folderId: String? = null,
    val transcribedUntilMs: Long? = null,
    /** Null follows Settings; empty forces auto-detect; otherwise a Whisper code for this note. */
    val languageOverride: String? = null,
)
