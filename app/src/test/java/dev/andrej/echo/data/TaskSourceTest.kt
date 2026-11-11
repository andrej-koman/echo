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
        assertEquals("I have to go to the dentist", lines[1].substring(links[1][0].start, links[1][0].end))
    }

    @Test fun `links mark the time and action but not lead-in filler`() {
        val text = "Hi. Okay. So I'm thinking about what I should do tomorrow. I think that tomorrow at 10 a.m. I have to go to the dentist. " +
            "Then at 12 a.m. tomorrow I have to go to the shop. Then after everything at around, I don't know, 6 p.m. I have drinks with my girlfriend. " +
            "Oh, and also remind me to for tomorrow all day to turn in an assignment. I think that I need to call Alex at 5 p.m."
        val tasks = listOf("Go to the dentist.", "Go to the shop.", "Drinks with girlfriend.", "Turn in assignment.", "Call Alex.")
            .mapIndexed { i, action -> task().copy(id = "t$i", text = action) }
        val marked = taskLinks(listOf(text), tasks).single().map { text.substring(it.start, it.end) }
        assertEquals(
            listOf(
                "tomorrow at 10 a.m. I have to go to the dentist",
                "at 12 a.m. tomorrow I have to go to the shop",
                "6 p.m. I have drinks with my girlfriend",
                "tomorrow all day to turn in an assignment",
                "need to call Alex at 5 p.m.",
            ),
            marked,
        )
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

    @Test fun `renaming a task keeps its unchanged source quotation`() {
        val task = task().copy(text = "Discuss the budget.", sourceQuote = "Call Alex tomorrow.", sourceOffsetMs = 1000)
        assertEquals(1, taskLinks(listOf("Call Alex tomorrow."), listOf(task)).single().size)
    }

    private fun task() = TodoItem("task", "note", "Call Alex.", 0, false, createdAt = 0)
}
