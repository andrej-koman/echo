package dev.andrej.echo.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JsonTranscriptRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun repository() = JsonTranscriptRepository(tempFolder.newFolder())

    @Test
    fun `saved transcript is returned`() = runTest {
        val repository = repository()

        repository.save(text = "hello world", language = "en-US", durationMs = 1_500)

        val all = repository.transcripts.first()
        assertEquals(1, all.size)
        assertEquals("hello world", all.single().text)
        assertEquals("en-US", all.single().language)
        assertEquals(1_500, all.single().durationMs)
    }

    @Test
    fun `transcripts are listed newest first`() = runTest {
        val repository = repository()

        repository.save(text = "first", language = "en-US", durationMs = 0, createdAt = 1_000)
        repository.save(text = "second", language = "en-US", durationMs = 0, createdAt = 2_000)

        assertEquals(listOf("second", "first"), repository.transcripts.first().map { it.text })
    }

    @Test
    fun `delete removes only the given transcript`() = runTest {
        val repository = repository()
        repository.save(text = "keep", language = "en-US", durationMs = 0)
        val doomed = repository.save(text = "remove", language = "en-US", durationMs = 0)

        repository.delete(doomed.id)

        assertEquals(listOf("keep"), repository.transcripts.first().map { it.text })
    }

    @Test
    fun `transcripts survive a new repository instance`() = runTest {
        val directory = tempFolder.newFolder()
        JsonTranscriptRepository(directory).save(text = "persisted", language = "en-US", durationMs = 0)

        val reopened = JsonTranscriptRepository(directory)

        assertEquals(listOf("persisted"), reopened.transcripts.first().map { it.text })
    }

    @Test
    fun `corrupt file is recovered as an empty list`() = runTest {
        val directory = tempFolder.newFolder()
        directory.resolve("transcripts.json").writeText("{not json at all")

        val repository = JsonTranscriptRepository(directory)

        assertTrue(repository.transcripts.first().isEmpty())
    }

    @Test
    fun `saving into a corrupt store still works`() = runTest {
        val directory = tempFolder.newFolder()
        directory.resolve("transcripts.json").writeText("garbage")
        val repository = JsonTranscriptRepository(directory)

        repository.save(text = "after recovery", language = "en-US", durationMs = 0)

        assertEquals(listOf("after recovery"), repository.transcripts.first().map { it.text })
    }
}
