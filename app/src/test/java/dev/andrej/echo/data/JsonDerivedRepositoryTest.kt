package dev.andrej.echo.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JsonDerivedRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun repository() = JsonDerivedRepository(tempFolder.newFolder())

    @Test
    fun `tasks and reminders are stored against their transcript`() = runTest {
        val repository = repository()

        repository.replaceFor(
            transcriptId = "t1",
            tasks = listOf("Buy a washer"),
            reminders = listOf("Call the plumber" to 2_000L),
            createdAt = 1_000,
        )

        val task = repository.tasks.first().single()
        assertEquals("Buy a washer", task.text)
        assertEquals("t1", task.sourceTranscriptId)
        assertTrue(!task.done)

        val reminder = repository.reminders.first().single()
        assertEquals(2_000L, reminder.dueAt)
    }

    @Test
    fun `reminders come out soonest first with no time last`() = runTest {
        val repository = repository()

        repository.replaceFor(
            transcriptId = "t1",
            tasks = emptyList(),
            reminders = listOf("later" to 3_000L, "no time" to null, "sooner" to 1_000L),
        )

        assertEquals(
            listOf("sooner", "later", "no time"),
            repository.reminders.first().map { it.text },
        )
    }

    @Test
    fun `re-analysis replaces rather than duplicates`() = runTest {
        val repository = repository()
        repository.replaceFor("t1", listOf("first pass"), emptyList())

        repository.replaceFor("t1", listOf("second pass"), emptyList())

        assertEquals(listOf("second pass"), repository.tasks.first().map { it.text })
    }

    @Test
    fun `replacing one transcript leaves the others alone`() = runTest {
        val repository = repository()
        repository.replaceFor("t1", listOf("keep me"), emptyList())
        repository.replaceFor("t2", listOf("replace me"), emptyList())

        repository.replaceFor("t2", listOf("replaced"), emptyList())

        assertEquals(setOf("keep me", "replaced"), repository.tasks.first().map { it.text }.toSet())
    }

    @Test
    fun `setDone flips one task`() = runTest {
        val repository = repository()
        repository.replaceFor("t1", listOf("a", "b"), emptyList())
        val target = repository.tasks.first().first { it.text == "a" }

        repository.setDone(target.id, done = true)

        val tasks = repository.tasks.first().associate { it.text to it.done }
        assertEquals(mapOf("a" to true, "b" to false), tasks)
    }

    @Test
    fun `deleteFor removes both lists for one transcript`() = runTest {
        val repository = repository()
        repository.replaceFor("t1", listOf("gone"), listOf("also gone" to null))
        repository.replaceFor("t2", listOf("stays"), listOf("stays too" to null))

        repository.deleteFor("t1")

        assertEquals(listOf("stays"), repository.tasks.first().map { it.text })
        assertEquals(listOf("stays too"), repository.reminders.first().map { it.text })
    }

    @Test
    fun `rows survive a new repository instance`() = runTest {
        val directory = tempFolder.newFolder()
        JsonDerivedRepository(directory).replaceFor("t1", listOf("persisted"), emptyList())

        assertEquals(
            listOf("persisted"),
            JsonDerivedRepository(directory).tasks.first().map { it.text },
        )
    }

    @Test
    fun `corrupt file is recovered as empty and still writable`() = runTest {
        val directory = tempFolder.newFolder()
        directory.resolve("derived.json").writeText("{not json at all")
        val repository = JsonDerivedRepository(directory)

        assertTrue(repository.tasks.first().isEmpty())

        repository.replaceFor("t1", listOf("after recovery"), emptyList())
        assertEquals(listOf("after recovery"), repository.tasks.first().map { it.text })
    }
}
