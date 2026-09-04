package dev.andrej.echo.data

import dev.andrej.echo.ai.AnalysisBlock
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JsonAnalysisQueueRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun repository(directory: File = tempFolder.newFolder()) = JsonAnalysisQueueRepository(directory)

    @Test
    fun `enqueue adds a fresh entry`() = runTest {
        val repository = repository()

        repository.enqueue("t1", now = 100)

        val entry = repository.pending.first().single()
        assertEquals("t1", entry.transcriptId)
        assertEquals(100L, entry.enqueuedAt)
        assertEquals(0, entry.attempts)
    }

    @Test
    fun `enqueue is a no-op for an already queued transcript`() = runTest {
        val repository = repository()

        repository.enqueue("t1", now = 100)
        repository.recordBlock("t1", AnalysisBlock.EmptyResult, consumesAttempt = true, now = 200)
        repository.enqueue("t1", now = 300)

        val entry = repository.pending.first().single()
        assertEquals(100L, entry.enqueuedAt)
        assertEquals(1, entry.attempts)
    }

    @Test
    fun `recordBlock with consumesAttempt increments attempts`() = runTest {
        val repository = repository()
        repository.enqueue("t1", now = 100)

        repository.recordBlock("t1", AnalysisBlock.GenerationFailed("boom"), consumesAttempt = true, now = 200)

        val entry = repository.pending.first().single()
        assertEquals(1, entry.attempts)
        assertEquals(AnalysisBlock.GenerationFailed("boom"), entry.lastBlock)
        assertEquals(200L, entry.lastAttemptAt)
    }

    @Test
    fun `recordBlock without consumesAttempt parks without incrementing`() = runTest {
        val repository = repository()
        repository.enqueue("t1", now = 100)

        repository.recordBlock("t1", AnalysisBlock.WaitingForModel(977_000_000), consumesAttempt = false, now = 200)
        repository.recordBlock("t1", AnalysisBlock.NoBackend("no AICore"), consumesAttempt = false, now = 300)

        val entry = repository.pending.first().single()
        assertEquals(0, entry.attempts)
        assertEquals(AnalysisBlock.NoBackend("no AICore"), entry.lastBlock)
    }

    @Test
    fun `remove drops the entry`() = runTest {
        val repository = repository()
        repository.enqueue("t1", now = 100)

        repository.remove("t1")

        assertTrue(repository.pending.first().isEmpty())
    }

    @Test
    fun `reset clears attempts on an existing entry`() = runTest {
        val repository = repository()
        repository.enqueue("t1", now = 100)
        repository.recordBlock("t1", AnalysisBlock.EmptyResult, consumesAttempt = true, now = 200)

        repository.reset("t1", now = 300)

        val entry = repository.pending.first().single()
        assertEquals(0, entry.attempts)
        assertNull(entry.lastBlock)
    }

    @Test
    fun `reset enqueues a transcript that was never queued`() = runTest {
        val repository = repository()

        repository.reset("t1", now = 300)

        val entry = repository.pending.first().single()
        assertEquals("t1", entry.transcriptId)
        assertEquals(0, entry.attempts)
    }

    @Test
    fun `the queue survives a new repository instance`() = runTest {
        val directory = tempFolder.newFolder()
        repository(directory).enqueue("t1", now = 100)

        val reopened = repository(directory)

        assertEquals("t1", reopened.pending.first().single().transcriptId)
    }

    @Test
    fun `a corrupted file is treated as an empty queue`() = runTest {
        val directory = tempFolder.newFolder()
        File(directory, "pending_analysis.json").writeText("not json")

        val repository = repository(directory)

        assertTrue(repository.pending.first().isEmpty())
    }
}
