package dev.andrej.echo.ai

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val UTC = ZoneId.of("UTC")
private val TODAY = LocalDate.of(2026, 9, 4)
private val RECORDED_AT = TODAY.atTime(11, 5)

class AnalysisPromptTest {

    private val clean = """
        {"title":"Call the plumber","summary":"Kitchen tap is dripping.",
         "items":[{"text":"Call the plumber","due":"2026-09-04T10:00"},{"text":"Buy a washer","due":null}]}
    """.trimIndent()

    private fun parse(raw: String) = parseAnalysis(raw, UTC, TODAY, RECORDED_AT)

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
    fun `due_relative_minutes flows through to a real due time and date`() {
        val raw = """{"items":[{"text":"water plants","due":null,"due_relative_minutes":20}]}"""
        val item = parse(raw)!!.items.single()

        assertEquals(RECORDED_AT.plusMinutes(20).atZone(UTC).toInstant().toEpochMilli(), item.dueAt)
        assertTrue(item.hasTime)
        assertTrue(item.hasDate)
    }

    @Test
    fun `an item with nothing stated has no date`() {
        val raw = """{"items":[{"text":"buy milk"}]}"""
        val item = parse(raw)!!.items.single()

        assertFalse(item.hasTime)
        assertFalse(item.hasDate)
    }

    @Test
    fun `a missing brace before the closing array bracket is repaired`() {
        // Observed on-device: the model drops the item object's closing brace before ']'.
        val raw = """{"title":"take out the trash","summary":"...",""" +
            """"items":[{"text":"take out the trash","due":"2026-09-07T10:00:00"]]}"""

        val analysis = parse(raw)!!

        assertEquals("take out the trash", analysis.items.single().text)
        assertTrue(analysis.items.single().hasTime)
    }

    @Test
    fun `a genuinely truncated array is still null, not mis-repaired`() {
        assertNull(parse("""{"title":"A","items":[{"text":"x","due":null}"""))
    }

    @Test
    fun `output with no json at all is null`() {
        assertNull(parse("I'm sorry, I can't help with that."))
        assertNull(parse(""))
    }

    @Test
    fun `prompt carries the date, the current time and the text`() {
        val prompt = buildAnalysisPrompt("call the plumber", LocalDate.of(2026, 9, 3).atTime(11, 5))

        assertTrue(prompt.contains("Today is 2026-09-03"))
        assertTrue(prompt.contains("the current time is 11:05"))
        assertTrue(prompt.contains("call the plumber"))
    }

    @Test
    fun `prompt asks for a reformatted note, not a short blurb`() {
        val prompt = buildAnalysisPrompt("call the plumber", LocalDate.of(2026, 9, 3).atTime(11, 5))

        assertTrue(prompt.contains("clean, readable note"))
        assertTrue(!prompt.contains("max 2 sentences"))
    }

    @Test
    fun `prompt asks the model to compute relative minutes, not resolve them itself`() {
        val prompt = buildAnalysisPrompt("call the plumber", LocalDate.of(2026, 9, 3).atTime(11, 5))

        assertTrue(prompt.contains("due_relative_minutes"))
    }

    @Test
    fun `prompt asks for capitalization, punctuation and list formatting fixes`() {
        val prompt = buildAnalysisPrompt("call the plumber", LocalDate.of(2026, 9, 3).atTime(11, 5))

        assertTrue(prompt.contains("capitalization"))
        assertTrue(prompt.contains("- \" bulleted list"))
    }

    @Test
    fun `long transcripts are truncated`() {
        val long = (1..MAX_PROMPT_WORDS + 500).joinToString(" ") { "word$it" }

        val prompt = buildAnalysisPrompt(long, LocalDate.of(2026, 9, 3).atTime(11, 5))

        assertTrue(prompt.contains("word$MAX_PROMPT_WORDS"))
        assertTrue(!prompt.contains("word${MAX_PROMPT_WORDS + 1} "))
    }
}

class ResolveDueTest {

    private fun due(value: String?, minutes: Int? = null) = resolveDue(value, minutes, UTC, TODAY, RECORDED_AT)

    @Test
    fun `date and time resolves with a time and a date`() {
        val result = due("2026-09-04T10:00")
        assertEquals(LocalDate.of(2026, 9, 4).atTime(10, 0).atZone(UTC).toInstant().toEpochMilli(), result.at)
        assertTrue(result.hasTime)
        assertTrue(result.hasDate)
    }

    @Test
    fun `bare date resolves to midnight with no time but a date`() {
        val result = due("2026-09-04")
        assertEquals(LocalDate.of(2026, 9, 4).atStartOfDay(UTC).toInstant().toEpochMilli(), result.at)
        assertFalse(result.hasTime)
        assertTrue(result.hasDate)
    }

    @Test
    fun `trailing zulu is accepted`() {
        assertEquals(due("2026-09-04T10:00"), due("2026-09-04T10:00Z"))
    }

    @Test
    fun `relative minutes are computed in Kotlin against the recorded time, not parsed from the model`() {
        val result = due(value = null, minutes = 15)
        assertEquals(RECORDED_AT.plusMinutes(15).atZone(UTC).toInstant().toEpochMilli(), result.at)
        assertTrue(result.hasTime)
        assertTrue(result.hasDate)
    }

    @Test
    fun `relative minutes win over a conflicting due string`() {
        val result = due("2026-09-04T10:00", minutes = 15)
        assertEquals(RECORDED_AT.plusMinutes(15).atZone(UTC).toInstant().toEpochMilli(), result.at)
    }

    @Test
    fun `unresolved relative phrases fall back to no date`() {
        for (value in listOf("next Tuesday", "tomorrow at ten", "soon")) {
            val result = due(value)
            assertFalse(result.hasTime)
            assertFalse(result.hasDate)
        }
    }

    @Test
    fun `absent and literal null fall back to no date`() {
        for (value in listOf(null, "", "   ", "null", "NULL")) {
            val result = due(value)
            assertFalse(result.hasTime)
            assertFalse(result.hasDate)
        }
    }

    @Test
    fun `nonsense date falls back to no date`() {
        for (value in listOf("2026-13-45", "not a date")) {
            val result = due(value)
            assertFalse(result.hasTime)
            assertFalse(result.hasDate)
        }
    }
}
