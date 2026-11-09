package dev.andrej.echo.ai

import dev.andrej.echo.data.NewTodo
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class TaskScheduleTest {
    private val zone = ZoneId.of("Europe/Ljubljana")
    private val recorded = LocalDateTime.of(2026, 11, 9, 23, 50)
    private fun at(value: String) = LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
    private fun ground(text: String, action: String, due: String = "2026-11-09T00:00:00", hasTime: Boolean = true) =
        groundTasks(listOf(NewTodo(action, at(due), hasTime, true)), listOf(text), listOf(0), recorded, zone).single()

    @Test fun `shared date carries to errands with distinct times`() {
        val text = "Tomorrow I need to call Alex at 10 a.m. Then buy milk at 6 p.m."
        assertEquals(at("2026-11-10T18:00:00"), ground(text, "Buy milk.").dueAt)
    }

    @Test fun `a changed date replaces shared tomorrow`() {
        val text = "Tomorrow call Alex. On Friday buy milk at noon."
        assertEquals(at("2026-11-13T12:00:00"), ground(text, "Buy milk.").dueAt)
    }

    @Test fun `noon midnight and all day are different`() {
        assertEquals(at("2026-11-10T12:00:00"), ground("Buy milk tomorrow at 12 p.m.", "Buy milk.").dueAt)
        assertEquals(at("2026-11-10T00:00:00"), ground("Buy milk tomorrow at 12 a.m.", "Buy milk.").dueAt)
        val allDay = ground("Turn in an assignment all day tomorrow.", "Turn in an assignment.")
        assertEquals(at("2026-11-10T00:00:00"), allDay.dueAt)
        assertFalse(allDay.hasTime)
    }

    @Test fun `unsupported languages keep model schedule`() {
        assertEquals(at("2026-11-12T18:00:00"), ground("Jutri kupi mleko.", "Kupi mleko.", "2026-11-12T18:00:00").dueAt)
    }

    @Test fun `time without date uses recording date`() {
        assertEquals(at("2026-11-09T18:00:00"), ground("Buy milk around 6 p.m.", "Buy milk.", "2026-11-12T18:00:00").dueAt)
    }

    @Test fun `source not found does not retain a guessed playback offset`() {
        val item = groundTasks(listOf(NewTodo("Call Alex.", 0, false, false, 0)), listOf("Unrelated note."), listOf(0), recorded, zone).single()
        assertNull(item.sourceOffsetMs)
        assertNull(item.sourceQuote)
    }

    @Test fun `date without clock does not become a midnight appointment`() {
        assertFalse(ground("Buy milk tomorrow.", "Buy milk.").hasTime)
    }

    @Test fun `duration from now overrides a shared plan date`() {
        val item = ground("Tomorrow call Alex. Also buy milk in 20 minutes.", "Buy milk.")
        assertEquals(recorded.plusMinutes(20).atZone(zone).toInstant().toEpochMilli(), item.dueAt)
    }

    @Test fun `invalid model quotation falls back to verified action words during analysis`() {
        val item = groundTasks(listOf(NewTodo("Buy milk.", 0, false, false, sourceQuote = "Go shopping.")),
            listOf("Buy milk tomorrow at noon."), listOf(0), recorded, zone).single()
        assertEquals(at("2026-11-10T12:00:00"), item.dueAt)
    }

    @Test fun `background dates do not date unrelated tasks`() {
        val result = groundTasks(listOf(NewTodo("Buy milk.", 0, false, false)),
            listOf("I met Alex on Friday. Buy milk."), listOf(0), recorded, zone).single()
        assertFalse(result.hasDate)
    }

    @Test fun `unsupported explicit dates keep model interpretation instead of inheriting tomorrow`() {
        assertEquals(at("2026-11-15T18:00:00"), ground(
            "Tomorrow call Alex. Buy milk on November 15 at 6 p.m.", "Buy milk.", "2026-11-15T18:00:00",
        ).dueAt)
    }

    @Test fun `written clock times keep model interpretation`() {
        val result = ground("Buy milk tomorrow at six p.m.", "Buy milk.", "2026-11-10T18:00:00")
        assertEquals(at("2026-11-10T18:00:00"), result.dueAt)
        assertTrue(result.hasTime)
    }

    @Test fun `two actions in one sentence keep separate clocks and source phrases`() {
        val text = "Tomorrow call Alex at 10 a.m. and buy milk at 6 p.m."
        val items = listOf(NewTodo("Call Alex.", 0, false, false, sourceQuote = text),
            NewTodo("Buy milk.", 0, false, false, sourceQuote = text))
        val result = groundTasks(items, listOf(text), listOf(0), recorded, zone)
        assertEquals(at("2026-11-10T10:00:00"), result[0].dueAt)
        assertEquals(at("2026-11-10T18:00:00"), result[1].dueAt)
        assertFalse(result[0].sourceQuote == result[1].sourceQuote)
    }

    @Test fun `short source quotation retains surrounding clock context`() {
        val item = groundTasks(listOf(NewTodo("Call Alex.", 0, false, false, sourceQuote = "Call Alex.")),
            listOf("Tomorrow at 10 a.m. Call Alex."), listOf(0), recorded, zone).single()
        assertEquals(at("2026-11-10T10:00:00"), item.dueAt)
    }

    @Test fun `weekday at start introduces a shared plan`() {
        assertEquals(at("2026-11-13T18:00:00"), ground("On Friday call Alex at 10 a.m. Then buy milk at 6 p.m.", "Buy milk.").dueAt)
    }
}
