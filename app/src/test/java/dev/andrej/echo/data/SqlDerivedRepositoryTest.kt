package dev.andrej.echo.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SqlDerivedRepositoryTest {

    private fun repository() = SqlDerivedRepository(testDatabase())

    private fun item(text: String, dueAt: Long = 1_000L, hasTime: Boolean = false, hasDate: Boolean = true) =
        NewTodo(text, dueAt, hasTime, hasDate)

    @Test
    fun `existing source quotes survive reading updating and re-analysis`() = runTest {
        val database = testDatabase()
        val repository = SqlDerivedRepository(database)
        repository.replaceFor("t1", listOf(item("Buy milk")))
        val existing = database.todoQueries.selectFor("t1").executeAsOne()
        database.todoQueries.delete(existing.id)
        database.todoQueries.insert(existing.copy(sourceQuote = "I need to buy milk"))

        assertEquals("I need to buy milk", repository.items.first().single().sourceQuote)
        repository.setDone(existing.id, true)
        repository.replaceFor("t1", listOf(item("Buy milk")))

        val stored = repository.items.first().single()
        assertEquals("I need to buy milk", stored.sourceQuote)
        assertTrue(stored.done)
    }

    @Test
    fun `re-analysis keeps tasks the user touched and replaces the rest`() = runTest {
        val repository = repository()
        repository.replaceFor("t1", listOf(item("Kupi mleko"), item("Pokliči mamo"), item("Plačaj račun"), item("Untouched")))
        val byText = repository.items.first().associateBy { it.text }
        repository.setDone(byText.getValue("Kupi mleko").id, true)
        repository.update(byText.getValue("Pokliči mamo").id, "Pokliči mamo zvečer", 1_000L, hasTime = false, hasDate = true)
        repository.snooze(byText.getValue("Plačaj račun").id)

        repository.replaceFor("t1", listOf(item("Buy milk"), item("Kupi mleko")))

        val texts = repository.items.first().map { it.text }.toSet()
        assertEquals(setOf("Kupi mleko", "Pokliči mamo zvečer", "Plačaj račun", "Buy milk"), texts)
        assertTrue(repository.items.first().single { it.text == "Kupi mleko" }.done)
    }

    @Test
    fun `analysis for a missing transcript creates no tasks or queue writes`() = runTest {
        val database = testDatabase()
        val repository = SqlDerivedRepository(database)
        database.analysisQueueQueries.enqueue("missing", 1L)

        repository.applyAnalysis("missing", listOf(item("Orphan task")), "Title", "Summary", 2L)

        assertTrue(repository.items.first().isEmpty())
        assertEquals("missing", database.analysisQueueQueries.selectAll().executeAsList().single().transcriptId)
    }

    @Test
    fun `analysis finishing after transcript deletion creates no tasks`() = runTest {
        val database = testDatabase()
        val transcripts = SqlTranscriptRepository(database)
        val repository = SqlDerivedRepository(database)
        transcripts.save(id = "deleted", text = "Buy milk", language = "en", durationMs = 0)
        database.analysisQueueQueries.enqueue("deleted", 1L)
        transcripts.delete("deleted")
        database.analysisQueueQueries.remove("deleted")

        repository.applyAnalysis("deleted", listOf(item("Buy milk")), "Shopping", "Milk", 2L)

        assertTrue(repository.items.first().isEmpty())
        assertTrue(database.analysisQueueQueries.selectAll().executeAsList().isEmpty())
    }

    @Test
    fun `analysis persists tasks metadata and removes its queue entry`() = runTest {
        val database = testDatabase()
        val transcripts = SqlTranscriptRepository(database)
        val repository = SqlDerivedRepository(database)
        transcripts.save(id = "live", text = "Buy milk", language = "en", durationMs = 0)
        database.analysisQueueQueries.enqueue("live", 1L)
        repository.replaceFor("live", listOf(item("Old task")))

        repository.applyAnalysis("live", listOf(item("Buy milk")), "Shopping", "Milk", 2L)

        val task = repository.items.first().single()
        assertEquals("Buy milk", task.text)
        assertEquals("live", task.sourceTranscriptId)
        assertEquals(2L, task.createdAt)
        val stored = transcripts.transcripts.first().single()
        assertEquals("Shopping", stored.title)
        assertEquals("Milk", stored.summary)
        assertEquals(2L, stored.analyzedAt)
        assertTrue(database.analysisQueueQueries.selectAll().executeAsList().isEmpty())
    }

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
    fun `update changes text, dueAt and hasTime and bumps updatedAt`() = runTest {
        val repository = repository()
        repository.replaceFor("t1", listOf(item("old text", dueAt = 1_000L, hasTime = false)), createdAt = 0L)
        val id = repository.items.first().single().id

        repository.update(id, text = "new text", dueAt = 5_000L, hasTime = true, hasDate = true)

        val updated = repository.items.first().single()
        assertEquals("new text", updated.text)
        assertEquals(5_000L, updated.dueAt)
        assertTrue(updated.hasTime)
        assertTrue(updated.updatedAt > updated.createdAt)
    }
}
