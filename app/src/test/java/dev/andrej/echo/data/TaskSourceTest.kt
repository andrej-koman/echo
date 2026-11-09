package dev.andrej.echo.data

import org.junit.Assert.*
import org.junit.Test

class TaskSourceTest {
    @Test fun `a task sentence spans segments without splitting at am`() {
        val lines = listOf("Tomorrow at 10 a.m.", "I have to go to the dentist. Then buy milk.")
        val text = lines.joinToString(" ")
        val source = taskSource(text, "Go to the dentist.")!!
        assertEquals("Tomorrow at 10 a.m. I have to go to the dentist.", text.substring(source.start, source.end))
        assertEquals(text.indexOf("dentist"), source.actionStart)
        val links = taskLinks(lines, listOf(task().copy(text = "Go to the dentist.")))
        assertEquals(1, links[0].size)
        assertEquals(1, links[1].size)
        assertEquals("I have to go to the dentist.", lines[1].substring(links[1][0].start, links[1][0].end))
    }

    @Test fun `stale or unrelated quotations produce no link`() {
        assertNull(taskSource("Buy milk.", "Call Alex.", "Call Alex."))
        assertNull(taskSource("Buy milk.", "Call Alex.", "Buy milk.", validateAction = true))
        assertTrue(taskLinks(listOf("Buy milk."), listOf(task().copy(sourceQuote = "Call Alex."))).single().isEmpty())
    }

    @Test fun `ambiguous repeated actions do not invent a reference`() {
        assertNull(taskSource("Call Alex tomorrow. Call Alex on Friday.", "Call Alex."))
        assertNotNull(taskSource("Call Alex tomorrow. Call Alex on Friday.", "Call Alex.", near = 30))
    }

    @Test fun `quotes tolerate whitespace and casing changes`() {
        val source = taskSource("  call   Alex tomorrow.", "Call Alex.", "Call Alex tomorrow.")!!
        assertEquals(2, source.start)
    }

    @Test fun `renaming a task keeps its unchanged source quotation and audio reference`() {
        val task = task().copy(text = "Discuss the budget.", sourceQuote = "Call Alex tomorrow.", sourceOffsetMs = 1000)
        assertEquals(1, taskLinks(listOf("Call Alex tomorrow."), listOf(task)).single().size)
        assertEquals(1000L, taskPlaybackOffset(task, listOf(Segment(1000, 2000, "Call Alex tomorrow."))))
        assertNull(taskPlaybackOffset(task, listOf(Segment(1000, 2000, "Different words."))))
    }

    private fun task() = TodoItem("task", "note", "Call Alex.", 0, false, createdAt = 0)
}
