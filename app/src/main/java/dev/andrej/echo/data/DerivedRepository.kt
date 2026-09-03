package dev.andrej.echo.data

import kotlinx.coroutines.flow.Flow

/** Tasks and reminders pulled out of transcripts. Both live in one file; they change together. */
interface DerivedRepository {

    val tasks: Flow<List<Task>>

    /** Soonest first. Reminders with no resolved time come last. */
    val reminders: Flow<List<Reminder>>

    /** Replaces whatever the given transcript produced before, so re-analysis does not duplicate. */
    suspend fun replaceFor(
        transcriptId: String,
        tasks: List<String>,
        reminders: List<Pair<String, Long?>>,
        createdAt: Long = System.currentTimeMillis(),
    )

    suspend fun setDone(taskId: String, done: Boolean)

    suspend fun deleteFor(transcriptId: String)
}
