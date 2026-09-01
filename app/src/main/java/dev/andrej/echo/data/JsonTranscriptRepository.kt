package dev.andrej.echo.data

import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Keeps transcripts in a single JSON file inside [directory].
 *
 * The whole list is held in memory and rewritten on every change. That is fine at this scale
 * (a few hundred short text entries) and keeps the code readable; if the list ever grows
 * large enough for that to hurt, this class is the only thing that has to change.
 */
class JsonTranscriptRepository(
    private val directory: File,
    /** Where disk writes happen. Injectable so tests can run them on the test scheduler. */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TranscriptRepository {

    private val file = File(directory, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true }
    private val writeLock = Mutex()

    private val state = MutableStateFlow(readFromDisk())

    override val transcripts: Flow<List<Transcript>> = state.asStateFlow()

    override suspend fun save(
        text: String,
        language: String,
        durationMs: Long,
        createdAt: Long,
    ): Transcript {
        val transcript = Transcript(
            id = UUID.randomUUID().toString(),
            text = text,
            language = language,
            createdAt = createdAt,
            durationMs = durationMs,
        )

        update { current -> current + transcript }
        return transcript
    }

    override suspend fun delete(id: String) {
        update { current -> current.filterNot { it.id == id } }
    }

    private suspend fun update(transform: (List<Transcript>) -> List<Transcript>) {
        writeLock.withLock {
            val updated = transform(state.value).sortedByDescending { it.createdAt }
            withContext(ioDispatcher) { writeToDisk(updated) }
            state.value = updated
        }
    }

    private fun readFromDisk(): List<Transcript> {
        if (!file.exists()) return emptyList()

        return try {
            json.decodeFromString<List<Transcript>>(file.readText())
                .sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            // A corrupt or half-written file must not take the app down. Starting from empty
            // loses history, which is better than never launching again.
            emptyList()
        }
    }

    private fun writeToDisk(transcripts: List<Transcript>) {
        directory.mkdirs()

        // Write to a temporary file first so a crash mid-write cannot corrupt the real one.
        val temp = File(directory, "$FILE_NAME.tmp")
        temp.writeText(json.encodeToString(transcripts))
        temp.renameTo(file)
    }

    private companion object {
        const val FILE_NAME = "transcripts.json"
    }
}
