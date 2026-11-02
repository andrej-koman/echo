package dev.andrej.echo.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SqlTranscriptRepositoryTest {

    private fun repository() = SqlTranscriptRepository(testDatabase())

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
    fun `clear removes every transcript`() = runTest {
        val repository = repository()
        val saved = repository.save(text = "one", language = "en-GB", durationMs = 0)
        repository.setTranscription(saved.id, "one", "en", 1_000, listOf(Segment(0, 1_000, "one")))

        repository.clear()

        assertTrue(repository.transcripts.first().isEmpty())
        assertTrue(repository.segments(saved.id).isEmpty())
    }

    @Test
    fun `setTranscription replaces text and segments`() = runTest {
        val repository = repository()
        val saved = repository.save(text = "", language = "", durationMs = 0)
        repository.setTranscription(saved.id, "first", "en", 1_000, listOf(Segment(0, 1_000, "first")))

        val segments = listOf(Segment(0, 1_000, "first"), Segment(1_000, 2_500, "second"))
        repository.setTranscription(saved.id, "first second", "en", 2_500, segments)

        val stored = repository.transcripts.first().single()
        assertEquals("first second", stored.text)
        assertEquals("en", stored.language)
        assertEquals(2_500L, stored.durationMs)
        assertEquals(segments, repository.segments(saved.id))
    }

    @Test
    fun `delete takes the segments with it`() = runTest {
        val repository = repository()
        val saved = repository.save(text = "", language = "", durationMs = 0)
        repository.setTranscription(saved.id, "gone", "en", 1_000, listOf(Segment(0, 1_000, "gone")))

        repository.delete(saved.id)

        assertTrue(repository.segments(saved.id).isEmpty())
    }


    @Test
    fun `search finds spoken words by prefix, titles and summaries`() = runTest {
        val repository = repository()
        repository.save(id = "a", text = "", language = "sl", durationMs = 0)
        repository.setTranscription("a", "", "sl", 0, listOf(Segment(0, 1, "Pokliči Črnigoja jutri")))
        repository.save(id = "b", text = "", language = "en", durationMs = 0)
        repository.attachAnalysis("b", title = "Dentist 50% off", summary = "Book a check-up")

        assertEquals(setOf("a"), repository.search("crni").first())
        assertEquals(setOf("a"), repository.search("pokliči JUTRI").first())
        assertEquals(setOf("b"), repository.search("check-up").first())
        assertEquals(setOf("b"), repository.search("50%").first())
        assertEquals(emptySet<String>(), repository.search("and").first())
    }

    @Test
    fun `edited and deleted segments leave the search index`() = runTest {
        val repository = repository()
        repository.save(id = "a", text = "", language = "en", durationMs = 0)
        repository.setTranscription("a", "", "en", 0, listOf(Segment(0, 1, "buy milk"), Segment(1, 2, "and eggs")))

        repository.editSegments("a", listOf(Segment(0, 1, "buy oat milk"), Segment(1, 2, "and bread")))

        assertEquals("buy oat milk and bread", repository.transcripts.first().single().text)
        assertEquals(emptySet<String>(), repository.search("eggs").first())
        assertEquals(setOf("a"), repository.search("oat").first())

        repository.delete("a")
        assertEquals(emptySet<String>(), repository.search("milk").first())
    }
}
