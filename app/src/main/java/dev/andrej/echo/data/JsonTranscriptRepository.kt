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

class JsonTranscriptRepository(
    private val directory: File,
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

    override suspend fun attachAnalysis(
        id: String,
        title: String?,
        summary: String?,
        analyzedAt: Long,
    ) {
        update { current ->
            current.map { transcript ->
                if (transcript.id == id) {
                    transcript.copy(title = title, summary = summary, analyzedAt = analyzedAt)
                } else {
                    transcript
                }
            }
        }
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
            // Losing history beats never launching again.
            emptyList()
        }
    }

    private fun writeToDisk(transcripts: List<Transcript>) {
        directory.mkdirs()

        val temp = File(directory, "$FILE_NAME.tmp")
        temp.writeText(json.encodeToString(transcripts))
        temp.renameTo(file)
    }

    private companion object {
        const val FILE_NAME = "transcripts.json"
    }
}
