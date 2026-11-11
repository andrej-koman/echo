package dev.andrej.echo.ai

import dev.andrej.echo.data.NewTodo
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlovenianScheduleTest {
    private val zone = ZoneId.of("Europe/Ljubljana")
    private val recorded = LocalDateTime.of(2026, 11, 11, 16, 5)
    private fun at(value: String) = LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
    private fun ground(text: String, action: String, quote: String? = null, due: Long = 0, hasTime: Boolean = false, hasDate: Boolean = false) =
        groundTasks(listOf(NewTodo(action, due, hasTime, hasDate, sourceQuote = quote)), listOf(text), listOf(0), recorded, zone, "sl").single()

    // The note that prompted this: the model left every due null.
    private val note = "Zdaj testiram, če mi bo delal rekord in potem pa da dam lepo vse noter. " +
        "Tako da jutri ob desetih zjutraj želim iti k zdravniku. Na to pa danes pa bi rad ob šestih zvečer šal na trening."

    @Test fun `tomorrow at ten in the morning`() {
        val item = ground(note, "Pojdi k zdravniku.", "Tako da jutri ob desetih zjutraj želim iti k zdravniku.")
        assertEquals(at("2026-11-12T10:00:00"), item.dueAt)
        assertTrue(item.hasTime)
    }

    @Test fun `today at six in the evening`() {
        val item = ground(note, "Pojdi na trening.", "Na to pa danes pa bi rad ob šestih zvečer šal na trening.")
        assertEquals(at("2026-11-11T18:00:00"), item.dueAt)
    }

    @Test fun `half counts toward the next hour`() {
        assertEquals(at("2026-11-12T17:30:00"), ground("Jutri moram ob pol šestih popoldne iti po avto.", "Pojdi po avto.").dueAt)
        assertEquals(at("2026-11-12T09:15:00"), ground("Jutri ob četrt na deset pokliči mamo.", "Pokliči mamo.").dueAt)
    }

    @Test fun `digits weekdays and noon`() {
        assertEquals(at("2026-11-13T14:30:00"), ground("V petek ob 14:30 moram k frizerju.", "Pojdi k frizerju.").dueAt)
        assertEquals(at("2026-11-11T12:00:00"), ground("Danes opoldne kupi kruh.", "Kupi kruh.").dueAt)
    }

    @Test fun `a shared tomorrow carries to the next plan`() {
        val item = ground("Jutri moram poklicati Ano. Potem kupi mleko ob osmih zvečer.", "Kupi mleko.")
        assertEquals(at("2026-11-12T20:00:00"), item.dueAt)
    }

    @Test fun `durations count from the recording`() {
        assertEquals(at("2026-11-11T16:25:00"), ground("Čez 20 minut zalij rože.", "Zalij rože.").dueAt)
        assertEquals(at("2026-11-11T16:35:00"), ground("Čez pol ure pokliči Marka.", "Pokliči Marka.").dueAt)
    }

    @Test fun `a date alone has no time`() {
        val item = ground("Pojutrišnjem moram oddati poročilo.", "Oddaj poročilo.")
        assertEquals(at("2026-11-13T00:00:00"), item.dueAt)
        assertFalse(item.hasTime)
    }

    @Test fun `a bare hour keeps the model's afternoon reading`() {
        val item = ground("Danes ob šestih pojdi na trening.", "Pojdi na trening.", due = at("2026-11-11T18:00:00"), hasTime = true, hasDate = true)
        assertEquals(at("2026-11-11T18:00:00"), item.dueAt)
    }

    @Test fun `schedule-only text is caught in Slovenian`() {
        assertTrue(isScheduleOnly("Jutri ob desetih zjutraj.", "sl"))
        assertFalse(isScheduleOnly("Pojdi k zdravniku.", "sl"))
    }
}
