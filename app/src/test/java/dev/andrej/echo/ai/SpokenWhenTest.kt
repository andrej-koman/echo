package dev.andrej.echo.ai

import dev.andrej.echo.data.NewTodo
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenWhenTest {
    private val zone = ZoneId.of("Europe/Ljubljana")
    private val recorded = LocalDateTime.of(2026, 11, 11, 16, 5)
    private fun at(value: String) = LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
    private fun ground(text: String, item: NewTodo, language: String) =
        groundTasks(listOf(item), listOf(text), listOf(0), recorded, zone, language).single()

    @Test fun `a German note is grounded through its English time`() {
        val item = ground(
            "Morgen um halb zehn muss ich zum Zahnarzt.",
            NewTodo("Geh zum Zahnarzt.", 0, false, false, sourceQuote = "Morgen um halb zehn muss ich zum Zahnarzt.", whenEn = "tomorrow at 9:30 a.m."),
            "de",
        )
        assertEquals(at("2026-11-12T09:30:00"), item.dueAt)
        assertTrue(item.hasTime)
    }

    @Test fun `a Japanese duration counts from the recording`() {
        val item = ground("20分後に植物に水をやる。", NewTodo("植物に水をやる。", 0, false, false, whenEn = "in 20 minutes"), "ja")
        assertEquals(at("2026-11-11T16:25:00"), item.dueAt)
    }

    @Test fun `a task whose sentence is not found still gets its stated time`() {
        val item = ground("Etwas ganz anderes.", NewTodo("Ruf Anna an.", 0, false, false, whenEn = "on Friday"), "de")
        assertEquals(at("2026-11-13T00:00:00"), item.dueAt)
        assertFalse(item.hasTime)
    }

    @Test fun `no stated time keeps the model's values`() {
        val item = ground("Ich muss Milch kaufen.", NewTodo("Kauf Milch.", 7, false, false, whenEn = "null"), "de")
        assertEquals(7L, item.dueAt)
        assertFalse(item.hasDate)
    }

    @Test fun `Slovenian falls back to the English time when its own rules find none`() {
        val item = ground("Moram v trgovino kmalu po službi.", NewTodo("Pojdi v trgovino.", 0, false, false, whenEn = "today at 5 p.m."), "sl")
        assertEquals(at("2026-11-11T17:00:00"), item.dueAt)
    }

    @Test fun `only non-English notes are asked for when_en`() {
        val now = LocalDate.of(2026, 9, 3).atTime(11, 5)
        val lines = listOf(IndexedValue(0, "x"))
        assertFalse(buildTasksPrompt(lines, now, "en").contains("when_en"))
        assertFalse(buildTasksPrompt(lines, now, null).contains("when_en"))
        val german = buildTasksPrompt(lines, now, "de")
        assertTrue(german.contains("\"when_en\":\"tomorrow at 5 p.m.\""))
        assertTrue(german.contains("when_en: the date and time stated"))
        val slovenian = buildTasksPrompt(lines, now, "sl")
        assertTrue(slovenian.contains("\"when_en\":\"tomorrow at 5 p.m.\""))
        assertTrue(slovenian.contains("pol desetih"))
        assertFalse(slovenian.contains("zobozdravnik"))
    }

    @Test fun `when_en is parsed from the reply`() {
        val item = parseTasks("""{"items":[{"text":"Kauf Milch.","due":null,"when_en":" tomorrow "}]}""", zone)!!.single()
        assertEquals("tomorrow", item.whenEn)
    }
}
