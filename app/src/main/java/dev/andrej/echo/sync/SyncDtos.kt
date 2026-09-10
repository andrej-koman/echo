package dev.andrej.echo.sync

import dev.andrej.echo.data.TodoItem
import dev.andrej.echo.data.Transcript
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TranscriptDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val text: String,
    val language: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("duration_ms") val durationMs: Long,
    val title: String? = null,
    val summary: String? = null,
    @SerialName("analyzed_at") val analyzedAt: Long? = null,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long? = null,
) {
    fun toDomain() = Transcript(
        id = id,
        text = text,
        language = language,
        createdAt = createdAt,
        durationMs = durationMs,
        title = title,
        summary = summary,
        analyzedAt = analyzedAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    companion object {
        fun from(transcript: Transcript, userId: String) = TranscriptDto(
            id = transcript.id,
            userId = userId,
            text = transcript.text,
            language = transcript.language,
            createdAt = transcript.createdAt,
            durationMs = transcript.durationMs,
            title = transcript.title,
            summary = transcript.summary,
            analyzedAt = transcript.analyzedAt,
            updatedAt = transcript.updatedAt,
            deletedAt = transcript.deletedAt,
        )
    }
}

@Serializable
data class TodoItemDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("source_transcript_id") val sourceTranscriptId: String,
    val text: String,
    @SerialName("due_at") val dueAt: Long,
    @SerialName("has_time") val hasTime: Boolean,
    val notify: Boolean,
    val done: Boolean,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long? = null,
) {
    fun toDomain() = TodoItem(
        id = id,
        sourceTranscriptId = sourceTranscriptId,
        text = text,
        dueAt = dueAt,
        hasTime = hasTime,
        notify = notify,
        done = done,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    companion object {
        fun from(item: TodoItem, userId: String) = TodoItemDto(
            id = item.id,
            userId = userId,
            sourceTranscriptId = item.sourceTranscriptId,
            text = item.text,
            dueAt = item.dueAt,
            hasTime = item.hasTime,
            notify = item.notify,
            done = item.done,
            createdAt = item.createdAt,
            updatedAt = item.updatedAt,
            deletedAt = item.deletedAt,
        )
    }
}
