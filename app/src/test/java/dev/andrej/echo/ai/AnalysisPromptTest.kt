package dev.andrej.echo.ai

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val UTC = ZoneId.of("UTC")
private val TODAY = LocalDate.of(2026, 9, 4)
private val RECORDED_AT = TODAY.atTime(11, 5)

class SummaryParsingTest {

    @Test
    fun `clean json is parsed`() {
        val summary = parseSummary("""{"title":"Call the plumber","summary":"Kitchen tap is dripping."}""")!!

        assertEquals("Call the plumber", summary.title)
        assertEquals("Kitchen tap is dripping.", summary.summary)
    }

    @Test
    fun `json in a fence or prose is parsed`() {
        assertEquals("A", parseSummary("```json\n{\"title\":\"A\"}\n```")!!.title)
        assertEquals("A", parseSummary("Sure! Here it is: {\"title\":\"A\"} Hope that helps.")!!.title)
    }

    @Test
    fun `blank strings become null, and nothing at all is no summary`() {
        assertNull(parseSummary("""{"title":"A","summary":"  "}""")!!.summary)
        assertNull(parseSummary("""{"title":"","summary":""}"""))
    }

    @Test
    fun `output with no json is null`() {
        assertNull(parseSummary("Sure! Please provide the details you'd like me to assist with."))
        assertNull(parseSummary(""))
    }
}

class IntentParsingTest {

    @Test
    fun `yes, no and no answer`() {
        assertEquals(true, parseIntent("""{"plans":true}"""))
        assertEquals(false, parseIntent("Sure. {\"plans\": false}"))
        assertNull(parseIntent("I think so"))
    }
}

class TaskParsingTest {

    private fun parse(raw: String, offsets: (Int) -> Long? = { null }) =
        parseTasks(raw, UTC, TODAY, RECORDED_AT, offsets)

    @Test
    fun `items resolve their due and keep their text`() {
        val items = parse(
            """{"items":[{"text":"Call the plumber","due":"2026-09-04T10:00"},{"text":"Buy a washer","due":null}]}""",
        )!!

        assertTrue(items.single { it.text == "Call the plumber" }.hasTime)
        assertFalse(items.single { it.text == "Buy a washer" }.hasDate)
    }

    @Test
    fun `an answer closed with a parenthesis instead of a brace still parses`() {
        val summary = parseSummary("""{"title":"Test and doctor appointment","summary":"Doctor at 10 am tomorrow.")""")!!
        assertEquals("Test and doctor appointment", summary.title)
        val items = parse("""{"items":[{"text":"Go to the doctor.","due":null}]""")!!
        assertEquals("Go to the doctor.", items.single().text)
    }

    @Test
    fun `quoted line numbers are dropped from the source`() {
        val item = parse("""{"items":[{"text":"Pojdi k zdravniku.","source":"[1] Jutri grem k zdravniku.","due":null}]}""")!!.single()
        assertEquals("Jutri grem k zdravniku.", item.sourceQuote)
    }

    @Test
    fun `an empty array is an answer, not a failure`() {
        assertEquals(emptyList<Any>(), parse("""{"items":[]}"""))
    }

    @Test
    fun `no items array at all is a failure`() {
        assertNull(parse("""{"title":"wrong prompt"}"""))
        assertNull(parse("no json"))
    }

    @Test
    fun `blank items are dropped`() {
        assertEquals(listOf("keep"), parse("""{"items":[{"text":""},{"text":"keep"}]}""")!!.map { it.text })
    }

    @Test
    fun `the line number becomes the moment it was said`() {
        val items = parse(
            """{"items":[{"text":"a","line":2},{"text":"b","line":9},{"text":"c"}]}""",
            offsets = { line -> if (line == 2) 42_000L else null },
        )!!

        assertEquals(listOf(42_000L, null, null), items.map { it.sourceOffsetMs })
    }

    @Test
    fun `due_relative_minutes flows through to a real due time`() {
        val item = parse("""{"items":[{"text":"water plants","due_relative_minutes":20}]}""")!!.single()

        assertEquals(RECORDED_AT.plusMinutes(20).atZone(UTC).toInstant().toEpochMilli(), item.dueAt)
        assertTrue(item.hasTime)
    }

    @Test
    fun `a missing brace before the closing array bracket is repaired`() {
        // Observed on-device: the model drops the item object's closing brace before ']'.
        val raw = """{"items":[{"text":"take out the trash","due":"2026-09-07T10:00:00"]]}"""

        assertEquals("take out the trash", parse(raw)!!.single().text)
    }

    @Test
    fun `a genuinely truncated array is still null, not mis-repaired`() {
        assertNull(parse("""{"items":[{"text":"x","due":null}"""))
    }
}

class PromptTest {

    @Test
    fun `the tasks prompt carries the date, the time and numbered lines`() {
        val prompt = buildTasksPrompt(
            listOf(IndexedValue(7, "call the plumber")),
            LocalDate.of(2026, 9, 3).atTime(11, 5),
        )

        assertTrue(prompt.contains("Today is 2026-09-03"))
        assertTrue(prompt.contains("the current time is 11:05"))
        assertTrue(prompt.contains("[7] call the plumber"))
    }

    @Test
    fun `prompts name the note's language instead of leaving it to the model`() {
        val summary = buildSummaryPrompt("Jutri grem k zdravniku.", "sl")
        assertTrue(summary.contains("in Slovenian, the language of the note"))
        assertTrue(buildSummaryPrompt("x", "en").contains("at most 6 words, in English."))
        // Same-language examples get copied into the answer, so there are none.
        assertTrue(summary.contains("Dripping kitchen tap"))
        assertTrue(!summary.contains("Kapljajoča"))
        val tasks = buildTasksPrompt(listOf(IndexedValue(0, "Jutri grem k zdravniku.")), LocalDate.of(2026, 9, 3).atTime(11, 5), "sl")
        assertTrue(tasks.contains("in Slovenian"))
        assertTrue(tasks.contains("pol desetih"))
        assertTrue(buildSummaryPrompt("Hola", "es").contains("in Spanish"))
        assertTrue(buildSummaryPrompt("Hi", null).contains("in the language the speaker used"))
    }

    @Test
    fun `the merge prompt carries every part`() {
        val prompt = buildMergePrompt(listOf(NoteSummary("One", "first"), NoteSummary("Two", "second")))

        assertTrue(prompt.contains("first"))
        assertTrue(prompt.contains("second"))
    }
}

class ChunkLinesTest {

    @Test
    fun `short text is one chunk with its line numbers`() {
        val chunks = chunkLines(listOf("a b", "c"), maxWords = 10)

        assertEquals(listOf(listOf(IndexedValue(0, "a b"), IndexedValue(1, "c"))), chunks)
    }

    @Test
    fun `lines are not split across chunks`() {
        val chunks = chunkLines(listOf("a b c", "d e f", "g"), maxWords = 4)

        assertEquals(listOf(listOf(0), listOf(1, 2)), chunks.map { chunk -> chunk.map { it.index } })
    }

    @Test
    fun `a line longer than the limit is split, keeping its number`() {
        val chunks = chunkLines(listOf("a b c d e"), maxWords = 2)

        assertEquals(listOf("a b", "c d", "e"), chunks.map { it.single().value })
        assertTrue(chunks.all { it.single().index == 0 })
    }

    @Test
    fun `sentences split at sentence ends`() {
        assertEquals(listOf("Buy milk.", "Call mum!", "Why?"), sentences(" Buy milk. Call mum! Why? "))
    }

    @Test
    fun `task text that is only a date or time is recognised`() {
        listOf("Tomorrow at 10 a.m.", "Tomorrow at 6 p.m.", "On Friday", "In 20 minutes.", "At noon", "18:00")
            .forEach { assertTrue(it, isScheduleOnly(it)) }
        listOf("Go to the doctor tomorrow at 10 a.m.", "Go to the mechanic.", "Call Alex at 9", "Buy milk")
            .forEach { assertFalse(it, isScheduleOnly(it)) }
    }
}
