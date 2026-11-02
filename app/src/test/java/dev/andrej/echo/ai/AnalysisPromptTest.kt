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
}

class PolishParsingTest {

    private val original = "so um I think we should uh go to the shop tomorrow and buy some milk"

    @Test
    fun `a cleanup a little shorter than the original is kept`() {
        val raw = "So I think we should go to the shop tomorrow and buy some milk."

        assertEquals(raw, parsePolish(raw, original))
    }

    @Test
    fun `a code fence is stripped`() {
        assertEquals("So I think we should go to the shop tomorrow and buy some milk.",
            parsePolish("```\nSo I think we should go to the shop tomorrow and buy some milk.\n```", original))
    }

    @Test
    fun `a summary is rejected`() {
        assertNull(parsePolish("Buy milk tomorrow.", original))
    }

    @Test
    fun `an answer far longer than the transcript is rejected`() {
        assertNull(parsePolish((original + " ").repeat(3), original))
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
