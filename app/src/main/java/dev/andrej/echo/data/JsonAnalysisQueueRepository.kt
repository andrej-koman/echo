package dev.andrej.echo.data

import dev.andrej.echo.ai.AnalysisBlock
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Mirrors [JsonTranscriptRepository]'s shape exactly — same disk safety, same corruption story. */
class JsonAnalysisQueueRepository(
    private val directory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AnalysisQueueRepository {

    private val file = File(directory, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true }
    private val writeLock = Mutex()

    private val state = MutableStateFlow(readFromDisk())

    override val pending: Flow<List<PendingAnalysis>> = state

    override suspend fun enqueue(transcriptId: String, now: Long) {
        update { current ->
            if (current.any { it.transcriptId == transcriptId }) {
                current
            } else {
                current + PendingAnalysis(transcriptId = transcriptId, enqueuedAt = now)
            }
        }
    }

    override suspend fun recordBlock(
        transcriptId: String,
        block: AnalysisBlock,
        consumesAttempt: Boolean,
        now: Long,
    ) {
        update { current ->
            current.map { entry ->
                if (entry.transcriptId == transcriptId) {
                    entry.copy(
                        attempts = if (consumesAttempt) entry.attempts + 1 else entry.attempts,
                        lastAttemptAt = now,
                        lastBlock = block,
                        updatedAt = now,
                    )
                } else {
                    entry
                }
            }
        }
    }

    override suspend fun remove(transcriptId: String) {
        update { current -> current.filterNot { it.transcriptId == transcriptId } }
    }

    override suspend fun reset(transcriptId: String, now: Long) {
        update { current ->
            if (current.any { it.transcriptId == transcriptId }) {
                current.map { entry ->
                    if (entry.transcriptId == transcriptId) {
                        entry.copy(attempts = 0, lastAttemptAt = null, lastBlock = null, updatedAt = now)
                    } else {
                        entry
                    }
                }
            } else {
                current + PendingAnalysis(transcriptId = transcriptId, enqueuedAt = now)
            }
        }
    }

    private suspend fun update(transform: (List<PendingAnalysis>) -> List<PendingAnalysis>) {
        writeLock.withLock {
            val updated = transform(state.value).sortedBy { it.enqueuedAt }
            withContext(ioDispatcher) { writeToDisk(updated) }
            state.value = updated
        }
    }

    private fun readFromDisk(): List<PendingAnalysis> {
        if (!file.exists()) return emptyList()

        return try {
            json.decodeFromString<List<PendingAnalysis>>(file.readText())
                .sortedBy { it.enqueuedAt }
        } catch (e: Exception) {
            // Losing the queue beats never launching again — transcripts themselves are untouched.
            emptyList()
        }
    }

    private fun writeToDisk(pending: List<PendingAnalysis>) {
        directory.mkdirs()

        val temp = File(directory, "$FILE_NAME.tmp")
        temp.writeText(json.encodeToString(pending))
        temp.renameTo(file)
    }

    private companion object {
        const val FILE_NAME = "pending_analysis.json"
    }
}
