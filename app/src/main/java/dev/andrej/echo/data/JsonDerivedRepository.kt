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
            Derived(
                tasks = current.tasks.filterNot { it.sourceTranscriptId == transcriptId } +
                    tasks.map { text ->
                        Task(
                            id = UUID.randomUUID().toString(),
                            sourceTranscriptId = transcriptId,
                            text = text,
                            createdAt = createdAt,
                        )
                    },
                reminders = current.reminders.filterNot { it.sourceTranscriptId == transcriptId } +
                    reminders.map { (text, dueAt) ->
                        Reminder(
                            id = UUID.randomUUID().toString(),
                            sourceTranscriptId = transcriptId,
                            text = text,
                            dueAt = dueAt,
                            createdAt = createdAt,
                        )
                    },
            )
        }
    }

    override suspend fun setDone(taskId: String, done: Boolean) {
        update { current ->
            current.copy(
                tasks = current.tasks.map { if (it.id == taskId) it.copy(done = done) else it },
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
