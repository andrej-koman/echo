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

    private fun item(text: String, dueAt: Long = 1_000L, hasTime: Boolean = false) =
        NewTodo(text, dueAt, hasTime)

    @Test
    fun `items are stored against their transcript`() = runTest {
        val repository = repository()

        repository.replaceFor(
            transcriptId = "t1",
            items = listOf(item("Buy a washer", dueAt = 2_000L, hasTime = true)),
            createdAt = 1_000,
        )

        val stored = repository.items.first().single()
        assertEquals("Buy a washer", stored.text)
        assertEquals("t1", stored.sourceTranscriptId)
        assertEquals(2_000L, stored.dueAt)
        assertTrue(stored.hasTime)
        assertTrue(!stored.done)
    }

    @Test
    fun `items come out soonest first, timed before date-only on the same day`() = runTest {
        val repository = repository()

        repository.replaceFor(
            transcriptId = "t1",
            items = listOf(
                item("later", dueAt = 3_000L, hasTime = true),
                item("date-only same time", dueAt = 1_000L, hasTime = false),
                item("sooner", dueAt = 1_000L, hasTime = true),
            ),
        )

        assertEquals(
            listOf("sooner", "date-only same time", "later"),
            repository.items.first().map { it.text },
        )
    }

    @Test
    fun `re-analysis replaces rather than duplicates`() = runTest {
        val repository = repository()
        repository.replaceFor("t1", listOf(item("first pass")))

        repository.replaceFor("t1", listOf(item("second pass")))

        assertEquals(listOf("second pass"), repository.items.first().map { it.text })
    }

    @Test
    fun `re-analysis keeps an item ticked when its text survives`() = runTest {
        val repository = repository()
        repository.replaceFor("t1", listOf(item("buy milk"), item("call mum")))
        val ticked = repository.items.first().first { it.text == "buy milk" }
        repository.setDone(ticked.id, done = true)

        repository.replaceFor("t1", listOf(item("buy milk"), item("call mum"), item("book flights")))

        assertEquals(
            mapOf("buy milk" to true, "call mum" to false, "book flights" to false),
            repository.items.first().associate { it.text to it.done },
        )
    }

    @Test
    fun `the same item from the same transcript keeps one stable id`() = runTest {
        val repository = repository()
        repository.replaceFor("t1", listOf(item("buy milk")))
        val before = repository.items.first().single().id

        repository.replaceFor("t1", listOf(item("Buy Milk ")))

        assertEquals(before, repository.items.first().single().id)
    }

    @Test
    fun `the same text under two transcripts gets two ids`() = runTest {
        val repository = repository()
        repository.replaceFor("t1", listOf(item("buy milk")))
        repository.replaceFor("t2", listOf(item("buy milk")))

        assertEquals(2, repository.items.first().map { it.id }.toSet().size)
    }

    @Test
    fun `an item repeated in one analysis collapses to a single row`() = runTest {
        val repository = repository()

        repository.replaceFor("t1", listOf(item("buy milk"), item("buy milk")))

        assertEquals(listOf("buy milk"), repository.items.first().map { it.text })
    }

    @Test
    fun `replacing one transcript leaves the others alone`() = runTest {
        val repository = repository()
        repository.replaceFor("t1", listOf(item("keep me")))
        repository.replaceFor("t2", listOf(item("replace me")))

        repository.replaceFor("t2", listOf(item("replaced")))

        assertEquals(setOf("keep me", "replaced"), repository.items.first().map { it.text }.toSet())
    }

    @Test
    fun `setDone flips one item`() = runTest {
        val repository = repository()
        repository.replaceFor("t1", listOf(item("a"), item("b")))
        val target = repository.items.first().first { it.text == "a" }

        repository.setDone(target.id, done = true)

        val items = repository.items.first().associate { it.text to it.done }
        assertEquals(mapOf("a" to true, "b" to false), items)
    }

    @Test
    fun `deleteFor removes rows for one transcript`() = runTest {
        val repository = repository()
        repository.replaceFor("t1", listOf(item("gone")))
        repository.replaceFor("t2", listOf(item("stays")))

        repository.deleteFor("t1")

        assertEquals(listOf("stays"), repository.items.first().map { it.text })
    }

    @Test
    fun `rows survive a new repository instance`() = runTest {
        val directory = tempFolder.newFolder()
        JsonDerivedRepository(directory).replaceFor("t1", listOf(item("persisted")))

        assertEquals(
            listOf("persisted"),
            JsonDerivedRepository(directory).items.first().map { it.text },
        )
    }

    @Test
    fun `corrupt file is recovered as empty and still writable`() = runTest {
        val directory = tempFolder.newFolder()
        directory.resolve("derived.json").writeText("{not json at all")
        val repository = JsonDerivedRepository(directory)

        assertTrue(repository.items.first().isEmpty())

        repository.replaceFor("t1", listOf(item("after recovery")))
        assertEquals(listOf("after recovery"), repository.items.first().map { it.text })
    }

    @Test
    fun `an old-shape derived json decodes to zero rows without throwing`() = runTest {
        val directory = tempFolder.newFolder()
        directory.resolve("derived.json").writeText(
            """{"tasks":[{"id":"a","sourceTranscriptId":"t1","text":"old","createdAt":1}],"reminders":[]}""",
        )
        val repository = JsonDerivedRepository(directory)

        assertTrue(repository.items.first().isEmpty())
    }
}
