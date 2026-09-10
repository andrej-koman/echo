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

    suspend fun setDone(itemId: String, done: Boolean)

    suspend fun update(itemId: String, text: String, dueAt: Long, hasTime: Boolean, hasDate: Boolean, notify: Boolean)

    /** Tombstones rather than removing, so the delete can sync to other devices. */
    suspend fun delete(itemId: String)

    suspend fun deleteFor(transcriptId: String)

    /** All rows including tombstones — for sync only, never for UI. */
    suspend fun allForSync(): List<TodoItem>

    /** Raw write from a remote pull: writes the row's fields as given, no id minting. */
    suspend fun upsertFromSync(item: TodoItem)
}
