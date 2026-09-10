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

    private val notifications = RecordingNotificationScheduler()

    private fun repository() = JsonDerivedRepository(tempFolder.newFolder(), notifications)

    private fun item(text: String, dueAt: Long = 1_000L, hasTime: Boolean = false, hasDate: Boolean = true) =
        NewTodo(text, dueAt, hasTime, hasDate)

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
        JsonDerivedRepository(directory, notifications).replaceFor("t1", listOf(item("persisted")))

        assertEquals(
            listOf("persisted"),
            JsonDerivedRepository(directory, notifications).items.first().map { it.text },
        )
    }

    @Test
    fun `corrupt file is recovered as empty and still writable`() = runTest {
        val directory = tempFolder.newFolder()
        directory.resolve("derived.json").writeText("{not json at all")
        val repository = JsonDerivedRepository(directory, notifications)

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
        val repository = JsonDerivedRepository(directory, notifications)

        assertTrue(repository.items.first().isEmpty())
    }

    @Test
    fun `replaceFor schedules the written rows`() = runTest {
        val repository = repository()

        repository.replaceFor("t1", listOf(item("call the plumber")))

        val id = repository.items.first().single().id
        assertEquals(listOf(id), notifications.scheduled)
    }

    @Test
    fun `a re-analysis that drops a row cancels its notification`() = runTest {
        val repository = repository()
        repository.replaceFor("t1", listOf(item("keep"), item("drop")))
        val dropped = repository.items.first().first { it.text == "drop" }.id

        repository.replaceFor("t1", listOf(item("keep")))

        assertTrue(dropped in notifications.cancelled)
    }

    @Test
    fun `setDone true cancels, setDone false reschedules`() = runTest {
        val repository = repository()
        repository.replaceFor("t1", listOf(item("call the plumber")))
        val id = repository.items.first().single().id
        notifications.scheduled.clear()

        repository.setDone(id, done = true)
        assertTrue(id in notifications.cancelled)

        notifications.cancelled.clear()
        repository.setDone(id, done = false)
        assertTrue(id in notifications.scheduled)
    }

    @Test
    fun `re-analysis does not resurrect a ticked row's alarm`() = runTest {
        val repository = repository()
        repository.replaceFor("t1", listOf(item("buy milk")))
        val id = repository.items.first().single().id
        repository.setDone(id, done = true)
        notifications.scheduled.clear()

        repository.replaceFor("t1", listOf(item("buy milk")))

        assertTrue(id !in notifications.scheduled)
    }

    @Test
    fun `update changes text, dueAt and hasTime and bumps updatedAt`() = runTest {
        val repository = repository()
        repository.replaceFor("t1", listOf(item("old text", dueAt = 1_000L, hasTime = false)))
        val id = repository.items.first().single().id

        repository.update(id, text = "new text", dueAt = 5_000L, hasTime = true, hasDate = true, notify = true)

        val updated = repository.items.first().single()
        assertEquals("new text", updated.text)
        assertEquals(5_000L, updated.dueAt)
        assertTrue(updated.hasTime)
        assertTrue(updated.updatedAt > updated.createdAt)
    }

    @Test
    fun `update reschedules the notification for the edited item`() = runTest {
        val repository = repository()
        repository.replaceFor("t1", listOf(item("a", dueAt = 1_000L, hasTime = false)))
        val id = repository.items.first().single().id
        notifications.scheduled.clear()

        repository.update(id, text = "a", dueAt = 5_000L, hasTime = true, hasDate = true, notify = true)

        assertTrue(id in notifications.scheduled)
    }

    @Test
    fun `a dateless item never schedules a notification`() = runTest {
        val repository = repository()

        repository.replaceFor("t1", listOf(item("buy milk", hasDate = false)))

        val id = repository.items.first().single().id
        assertTrue(id !in notifications.scheduled)
    }

    @Test
    fun `update to hasDate false cancels any scheduled notification`() = runTest {
        val repository = repository()
        repository.replaceFor("t1", listOf(item("a", dueAt = 1_000L, hasTime = true)))
        val id = repository.items.first().single().id

        repository.update(id, text = "a", dueAt = 1_000L, hasTime = false, hasDate = false, notify = true)

        assertTrue(id in notifications.cancelled)
    }

    @Test
    fun `update on a done item leaves it done and does not resurrect its notification`() = runTest {
        val repository = repository()
        repository.replaceFor("t1", listOf(item("a", dueAt = 1_000L, hasTime = false)))
        val id = repository.items.first().single().id
        repository.setDone(id, done = true)
        notifications.scheduled.clear()

        repository.update(id, text = "b", dueAt = 5_000L, hasTime = true, hasDate = true, notify = true)

        assertTrue(repository.items.first().single().done)
        assertTrue(id !in notifications.scheduled)
        assertTrue(id in notifications.cancelled)
    }

    @Test
    fun `delete removes the item and cancels its notification`() = runTest {
        val repository = repository()
        repository.replaceFor("t1", listOf(item("keep"), item("gone")))
        val gone = repository.items.first().first { it.text == "gone" }.id

        repository.delete(gone)

        assertEquals(listOf("keep"), repository.items.first().map { it.text })
        assertTrue(gone in notifications.cancelled)
    }

    @Test
    fun `deleteFor cancels all of that transcript's ids`() = runTest {
        val repository = repository()
        repository.replaceFor("t1", listOf(item("a"), item("b")))
        val ids = repository.items.first().map { it.id }

        repository.deleteFor("t1")

        assertEquals(ids.toSet(), notifications.cancelled.toSet())
    }

    @Test
    fun `delete tombstones rather than dropping the row, so allForSync still sees it`() = runTest {
        val repository = repository()
        repository.replaceFor("t1", listOf(item("gone")))
        val id = repository.items.first().single().id

        repository.delete(id)

        val tombstoned = repository.allForSync().single { it.id == id }
        assertTrue(tombstoned.deletedAt != null)
        assertTrue(repository.items.first().none { it.id == id })
    }

    @Test
    fun `upsertFromSync writes the row as given and applies it to items when not tombstoned`() = runTest {
        val repository = repository()
        val remote = TodoItem(
            id = "remote-1",
            sourceTranscriptId = "t1",
            text = "From another device",
            dueAt = 5_000L,
            hasTime = true,
            createdAt = 1_000L,
            updatedAt = 2_000L,
        )

        repository.upsertFromSync(remote)

        assertEquals(remote, repository.items.first().single())
        assertEquals(remote, repository.allForSync().single())
    }

    @Test
    fun `upsertFromSync with a tombstoned row cancels the notification and hides it from items`() = runTest {
        val repository = repository()
        val remote = TodoItem(
            id = "remote-1",
            sourceTranscriptId = "t1",
            text = "Deleted elsewhere",
            dueAt = 5_000L,
            hasTime = true,
            createdAt = 1_000L,
            updatedAt = 2_000L,
            deletedAt = 2_000L,
        )

        repository.upsertFromSync(remote)

        assertTrue(repository.items.first().isEmpty())
        assertTrue("remote-1" in notifications.cancelled)
    }
}
