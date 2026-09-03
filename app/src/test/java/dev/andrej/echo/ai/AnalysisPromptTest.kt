package dev.andrej.echo.ai

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val UTC = ZoneId.of("UTC")

class AnalysisPromptTest {

    private val clean = """
        {"title":"Call the plumber","summary":"Kitchen tap is dripping.",
         "tasks":["Call the plumber","Buy a washer"],
         "reminders":[{"text":"Call the plumber","due":"2026-09-04T10:00"}]}
    """.trimIndent()

    @Test
    fun `clean json is parsed`() {
        val analysis = parseAnalysis(clean, UTC)!!

        assertEquals("Call the plumber", analysis.title)
        assertEquals("Kitchen tap is dripping.", analysis.summary)
        assertEquals(listOf("Call the plumber", "Buy a washer"), analysis.tasks)
        assertEquals(1, analysis.reminders.size)
        assertEquals("Call the plumber", analysis.reminders.single().first)
    }

    @Test
    fun `fenced json is parsed`() {
        val analysis = parseAnalysis("```json\n$clean\n```", UTC)!!

        assertEquals("Call the plumber", analysis.title)
        assertEquals(2, analysis.tasks.size)
    }

    @Test
    fun `json wrapped in prose is parsed`() {
        val raw = "Sure! Here is the JSON you asked for:\n$clean\nHope that helps."

        assertEquals("Call the plumber", parseAnalysis(raw, UTC)!!.title)
    }

    @Test
    fun `unknown fields are ignored`() {
        val raw = """{"title":"A","mood":"chirpy","tasks":[],"reminders":[]}"""

        assertEquals("A", parseAnalysis(raw, UTC)!!.title)
    }

    @Test
    fun `missing fields fall back to empty`() {
        val analysis = parseAnalysis("""{"title":"Only a title"}""", UTC)!!

        assertEquals("Only a title", analysis.title)
        assertNull(analysis.summary)
        assertTrue(analysis.tasks.isEmpty())
        assertTrue(analysis.reminders.isEmpty())
    }

    @Test
    fun `blank strings become null`() {
        val analysis = parseAnalysis("""{"title":"   ","summary":""}""", UTC)!!

        assertNull(analysis.title)
        assertNull(analysis.summary)
    }

    @Test
    fun `blank tasks and reminders are dropped`() {
        val raw = """{"tasks":["  ","real"],"reminders":[{"text":"","due":null},{"text":"keep"}]}"""
        val analysis = parseAnalysis(raw, UTC)!!

        assertEquals(listOf("real"), analysis.tasks)
        assertEquals(listOf("keep"), analysis.reminders.map { it.first })
    }

    @Test
    fun `malformed json is null`() {
        assertNull(parseAnalysis("""{"title": "unterminated""", UTC))
    }

    @Test
    fun `output with no json at all is null`() {
        assertNull(parseAnalysis("I'm sorry, I can't help with that.", UTC))
        assertNull(parseAnalysis("", UTC))
    }

    @Test
    fun `prompt carries the date and the text`() {
        val prompt = buildAnalysisPrompt("call the plumber", LocalDate.of(2026, 9, 3))

        assertTrue(prompt.contains("Today is 2026-09-03"))
        assertTrue(prompt.contains("call the plumber"))
    }

    @Test
    fun `long transcripts are truncated`() {
        val long = (1..MAX_PROMPT_WORDS + 500).joinToString(" ") { "word$it" }

        val prompt = buildAnalysisPrompt(long, LocalDate.of(2026, 9, 3))

        assertTrue(prompt.contains("word$MAX_PROMPT_WORDS"))
        assertTrue(!prompt.contains("word${MAX_PROMPT_WORDS + 1} "))
    }
}

class ResolveDueTest {

    private fun due(value: String?) = resolveDue(value, UTC)

    @Test
    fun `date and time resolves`() {
        assertEquals(
            LocalDate.of(2026, 9, 4).atTime(10, 0).atZone(UTC).toInstant().toEpochMilli(),
            due("2026-09-04T10:00"),
        )
    }

    @Test
    fun `bare date resolves to midnight`() {
        assertEquals(
            LocalDate.of(2026, 9, 4).atStartOfDay(UTC).toInstant().toEpochMilli(),
            due("2026-09-04"),
        )
    }

    @Test
    fun `trailing zulu is accepted`() {
        assertEquals(due("2026-09-04T10:00"), due("2026-09-04T10:00Z"))
    }

    @Test
    fun `unresolved relative phrases are null, not wrong`() {
        assertNull(due("next Tuesday"))
        assertNull(due("tomorrow at ten"))
        assertNull(due("soon"))
    }

    @Test
    fun `absent and literal null are null`() {
        assertNull(due(null))
        assertNull(due(""))
        assertNull(due("   "))
        assertNull(due("null"))
        assertNull(due("NULL"))
    }

    @Test
    fun `nonsense date is null`() {
        assertNull(due("2026-13-45"))
        assertNull(due("not a date"))
    }
}
