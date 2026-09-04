package dev.andrej.echo.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `attachAnalysis writes the title, summary and stamp`() = runTest {
        val repository = repository()
        val saved = repository.save(text = "the tap drips", language = "en-GB", durationMs = 0)

        repository.attachAnalysis(saved.id, "Fix the tap", "It drips.", analyzedAt = 9_000)

        val stored = repository.transcripts.first().single()
        assertEquals("Fix the tap", stored.title)
        assertEquals("It drips.", stored.summary)
        assertEquals(9_000L, stored.analyzedAt)
    }

    @Test
    fun `attachAnalysis on an unknown id changes nothing`() = runTest {
        val repository = repository()
        repository.save(text = "untouched", language = "en-GB", durationMs = 0)

        repository.attachAnalysis("nope", "Title", "Summary")

        assertNull(repository.transcripts.first().single().title)
    }

    @Test
    fun `transcripts written before analysis existed still load`() = runTest {
        val directory = tempFolder.newFolder()
        directory.resolve("transcripts.json").writeText(
            """[{"id":"old","text":"legacy","language":"en-GB","createdAt":1,"durationMs":2}]""",
        )

        val stored = JsonTranscriptRepository(directory).transcripts.first().single()

        assertEquals("legacy", stored.text)
        assertNull(stored.title)
        assertNull(stored.summary)
        assertNull(stored.analyzedAt)
        assertEquals(stored.createdAt, stored.updatedAt)
        assertNull(stored.deletedAt)
    }

    @Test
    fun `a deleted transcript stays deleted across instances`() = runTest {
        val directory = tempFolder.newFolder()
        val repository = JsonTranscriptRepository(directory)
        repository.save(text = "keep", language = "en-GB", durationMs = 0)
        val doomed = repository.save(text = "remove", language = "en-GB", durationMs = 0)

        repository.delete(doomed.id)

        assertEquals(
            listOf("keep"),
            JsonTranscriptRepository(directory).transcripts.first().map { it.text },
        )
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
