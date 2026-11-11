package dev.andrej.echo.data

import kotlinx.coroutines.flow.Flow

/** Todo items pulled out of transcripts. */
interface DerivedRepository {

    /** Soonest first; within a day, timed items before date-only ones. */
    val items: Flow<List<TodoItem>>

    /** Replaces whatever the given transcript produced before, so re-analysis does not duplicate. */
    suspend fun replaceFor(
        transcriptId: String,
        items: List<NewTodo>,
        createdAt: Long = System.currentTimeMillis(),
    )

    /** Applies analysis only while its transcript exists, including queue removal in one transaction. */
    suspend fun applyAnalysis(
        transcriptId: String,
        items: List<NewTodo>,
        title: String?,
        summary: String?,
        createdAt: Long,
    )

    suspend fun setDone(itemId: String, done: Boolean)

    /**
     * Reminds again in an hour. A timed task's time moves with it (an hour after its time, or after
     * now once overdue) so lists show the new time; date-only and undated tasks keep their deadline.
     */
    suspend fun snooze(itemId: String)

    suspend fun update(itemId: String, text: String, dueAt: Long, hasTime: Boolean, hasDate: Boolean)

    suspend fun delete(itemId: String)

    suspend fun deleteFor(transcriptId: String)

    suspend fun clear()
}
