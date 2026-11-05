package dev.andrej.echo.data

import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface TranscriptRepository {

    val transcripts: Flow<List<Transcript>>

    suspend fun save(
        id: String = UUID.randomUUID().toString(),
        text: String,
        language: String,
        durationMs: Long,
        createdAt: Long = System.currentTimeMillis(),
        title: String? = null,
        titleByUser: Boolean = false,
    ): Transcript

    /** Rewritten after every transcribed chunk, so a long import fills in as it goes. */
    suspend fun setTranscription(
        id: String,
        text: String,
        language: String,
        durationMs: Long,
        segments: List<Segment>,
    )

    suspend fun segments(id: String): List<Segment>

    fun segmentsFlow(id: String): Flow<List<Segment>>

    /** Replaces the segments with edited ones; the transcript's text is rejoined from them. */
    suspend fun editSegments(id: String, segments: List<Segment>)

    /** Diarization's result: [segments] carry speakers numbered from 0 below [count]. */
    suspend fun setSpeakers(id: String, segments: List<Segment>, count: Int)

    fun speakers(id: String): Flow<List<Speaker>>

    /** Every name given to a speaker, most used first. */
    val speakerNames: Flow<List<String>>

    /** Returns the new speaker's index. */
    suspend fun addSpeaker(id: String): Int

    /** Null or blank goes back to the numbered default. */
    suspend fun renameSpeaker(id: String, index: Int, name: String?)

    /** Ids of transcripts whose title, summary or spoken text matches. */
    fun search(text: String): Flow<Set<String>>

    suspend fun attachAnalysis(
        id: String,
        title: String?,
        summary: String?,
        analyzedAt: Long = System.currentTimeMillis(),
    )

    /** A nonblank title chosen by the user; later analysis keeps it. */
    suspend fun rename(id: String, title: String)

    /** Null takes it out of any folder. */
    suspend fun moveToFolder(id: String, folderId: String?)

    suspend fun delete(id: String)

    suspend fun clear()
}
