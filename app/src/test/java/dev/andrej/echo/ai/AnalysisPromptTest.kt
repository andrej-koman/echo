package dev.andrej.echo.ai

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val UTC = ZoneId.of("UTC")
private val TODAY = LocalDate.of(2026, 9, 4)

class AnalysisPromptTest {

    private val clean = """
        {"title":"Call the plumber","summary":"Kitchen tap is dripping.",
         "items":[{"text":"Call the plumber","due":"2026-09-04T10:00"},{"text":"Buy a washer","due":null}]}
    """.trimIndent()

    private fun parse(raw: String) = parseAnalysis(raw, UTC, TODAY)

    @Test
    fun `clean json is parsed`() {
        val analysis = parse(clean)!!

        assertEquals("Call the plumber", analysis.title)
        assertEquals("Kitchen tap is dripping.", analysis.summary)
        assertEquals(2, analysis.items.size)
        val timed = analysis.items.single { it.text == "Call the plumber" }
        assertTrue(timed.hasTime)
        val untimed = analysis.items.single { it.text == "Buy a washer" }
        assertTrue(!untimed.hasTime)
    }

    @Test
    fun `fenced json is parsed`() {
        val analysis = parse("```json\n$clean\n```")!!

        assertEquals("Call the plumber", analysis.title)
        assertEquals(2, analysis.items.size)
    }

    @Test
    fun `json wrapped in prose is parsed`() {
        val raw = "Sure! Here is the JSON you asked for:\n$clean\nHope that helps."

        assertEquals("Call the plumber", parse(raw)!!.title)
    }

    @Test
    fun `unknown fields are ignored`() {
        val raw = """{"title":"A","mood":"chirpy","items":[]}"""

        assertEquals("A", parse(raw)!!.title)
    }

    @Test
    fun `missing fields fall back to empty`() {
        val analysis = parse("""{"title":"Only a title"}""")!!

        assertEquals("Only a title", analysis.title)
        assertNull(analysis.summary)
        assertTrue(analysis.items.isEmpty())
    }

    @Test
    fun `blank strings become null`() {
        val analysis = parse("""{"title":"   ","summary":""}""")!!

        assertNull(analysis.title)
        assertNull(analysis.summary)
    }

    @Test
    fun `blank items are dropped`() {
        val raw = """{"items":[{"text":"","due":null},{"text":"keep"}]}"""
        val analysis = parse(raw)!!

        assertEquals(listOf("keep"), analysis.items.map { it.text })
    }

    @Test
    fun `malformed json is null`() {
        assertNull(parse("""{"title": "unterminated"""))
    }

    @Test
    fun `output with no json at all is null`() {
        assertNull(parse("I'm sorry, I can't help with that."))
        assertNull(parse(""))
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

    private fun due(value: String?) = resolveDue(value, UTC, TODAY)

    @Test
    fun `date and time resolves with a time`() {
        val result = due("2026-09-04T10:00")
        assertEquals(LocalDate.of(2026, 9, 4).atTime(10, 0).atZone(UTC).toInstant().toEpochMilli(), result.at)
        assertTrue(result.hasTime)
    }

    @Test
    fun `bare date resolves to midnight with no time`() {
        val result = due("2026-09-04")
        assertEquals(LocalDate.of(2026, 9, 4).atStartOfDay(UTC).toInstant().toEpochMilli(), result.at)
        assertTrue(!result.hasTime)
    }

    @Test
    fun `trailing zulu is accepted`() {
        assertEquals(due("2026-09-04T10:00"), due("2026-09-04T10:00Z"))
    }

    @Test
    fun `unresolved relative phrases fall back to today, untimed`() {
        val expected = TODAY.atStartOfDay(UTC).toInstant().toEpochMilli()
        for (value in listOf("next Tuesday", "tomorrow at ten", "soon")) {
            val result = due(value)
            assertEquals(expected, result.at)
            assertTrue(!result.hasTime)
        }
    }

    @Test
    fun `absent and literal null fall back to today, untimed`() {
        val expected = TODAY.atStartOfDay(UTC).toInstant().toEpochMilli()
        for (value in listOf(null, "", "   ", "null", "NULL")) {
            val result = due(value)
            assertEquals(expected, result.at)
            assertTrue(!result.hasTime)
        }
    }

    @Test
    fun `nonsense date falls back to today, untimed`() {
        val expected = TODAY.atStartOfDay(UTC).toInstant().toEpochMilli()
        for (value in listOf("2026-13-45", "not a date")) {
            val result = due(value)
            assertEquals(expected, result.at)
            assertTrue(!result.hasTime)
        }
    }
}
