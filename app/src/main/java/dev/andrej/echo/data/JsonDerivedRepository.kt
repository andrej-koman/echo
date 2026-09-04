package dev.andrej.echo.data

import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class Derived(
    val tasks: List<Task> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
)

class JsonDerivedRepository(
    private val directory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DerivedRepository {

    private val file = File(directory, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true }
    private val writeLock = Mutex()

    private val state = MutableStateFlow(readFromDisk())

    override val tasks: Flow<List<Task>> = state.map { it.tasks }

    override val reminders: Flow<List<Reminder>> = state.map { derived ->
        derived.reminders.sortedWith(compareBy(nullsLast()) { it.dueAt })
    }

    override suspend fun replaceFor(
        transcriptId: String,
        tasks: List<String>,
        reminders: List<Pair<String, Long?>>,
        createdAt: Long,
    ) {
        update { current ->
            val doneBefore = current.tasks
                .filter { it.sourceTranscriptId == transcriptId }
                .associate { it.id to it.done }

            Derived(
                tasks = current.tasks.filterNot { it.sourceTranscriptId == transcriptId } +
                    tasks.map { text ->
                        val id = derivedId(transcriptId, "task", text)
                        Task(
                            id = id,
                            sourceTranscriptId = transcriptId,
                            text = text,
                            done = doneBefore[id] ?: false,
                            createdAt = createdAt,
                        )
                    }.distinctBy { it.id },
                reminders = current.reminders.filterNot { it.sourceTranscriptId == transcriptId } +
                    reminders.map { (text, dueAt) ->
                        Reminder(
                            id = derivedId(transcriptId, "reminder", text),
                            sourceTranscriptId = transcriptId,
                            text = text,
                            dueAt = dueAt,
                            createdAt = createdAt,
                        )
                    }.distinctBy { it.id },
            )
        }
    }

    /** Keyed by what produced the row, not minted, so re-analysis keeps ids and [Task.done]. */
    private fun derivedId(transcriptId: String, kind: String, text: String): String =
        UUID.nameUUIDFromBytes(
            "$transcriptId\u0000$kind\u0000${text.trim().lowercase()}".toByteArray(),
        ).toString()

    override suspend fun setDone(taskId: String, done: Boolean) {
        val now = System.currentTimeMillis()
        update { current ->
            current.copy(
                tasks = current.tasks.map { task ->
                    if (task.id == taskId) task.copy(done = done, updatedAt = now) else task
                },
            )
        }
    }

    override suspend fun deleteFor(transcriptId: String) {
        update { current ->
            Derived(
                tasks = current.tasks.filterNot { it.sourceTranscriptId == transcriptId },
                reminders = current.reminders.filterNot { it.sourceTranscriptId == transcriptId },
            )
        }
    }

    private suspend fun update(transform: (Derived) -> Derived) {
        writeLock.withLock {
            val updated = transform(state.value)
            withContext(ioDispatcher) { writeToDisk(updated) }
            state.value = updated
        }
    }

    private fun readFromDisk(): Derived {
        if (!file.exists()) return Derived()

        return try {
            json.decodeFromString<Derived>(file.readText())
        } catch (e: Exception) {
            // Same trade as transcripts: losing this beats never launching again.
            Derived()
        }
    }

    private fun writeToDisk(derived: Derived) {
        directory.mkdirs()

        val temp = File(directory, "$FILE_NAME.tmp")
        temp.writeText(json.encodeToString(derived))
        temp.renameTo(file)
    }

    private companion object {
        const val FILE_NAME = "derived.json"
    }
}
