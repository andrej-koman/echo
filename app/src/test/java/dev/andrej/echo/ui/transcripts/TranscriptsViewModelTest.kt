package dev.andrej.echo.ui.transcripts

import dev.andrej.echo.data.Transcript
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

class TranscriptsViewModelTest {

    @Test
    fun `title stops at the first sentence`() {
        assertEquals("Oat milk, two cartons", title("Oat milk, two cartons. Sourdough as well."))
    }

    @Test
    fun `title cuts a long opening at six words`() {
        assertEquals(
            "She wants the pitch cut to…",
            title("She wants the pitch cut to eight slides and the pricing page moved"),
        )
    }

    @Test
    fun `title falls back for empty text`() {
        assertEquals("Untitled", title("   "))
    }

    @Test
    fun `clock pads seconds`() {
        assertEquals("1:05", clock(65_000))
        assertEquals("0:09", clock(9_400))
    }

    @Test
    fun `spoken total switches to hours past sixty minutes`() {
        assertEquals("14m", spokenTotal(TimeUnit.MINUTES.toMillis(14)))
        assertEquals("2h 14m", spokenTotal(TimeUnit.MINUTES.toMillis(134)))
    }

    @Test
    fun `groups are labelled relative to today and ordered newest first`() {
        val now = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 12) }.timeInMillis
        val day = TimeUnit.DAYS.toMillis(1)

        val groups = group(
            listOf(
                transcript("older", now - 3 * day),
                transcript("today", now),
                transcript("yesterday", now - day),
            ),
            now,
        )

        assertEquals(listOf("Today", "Yesterday"), groups.take(2).map { it.label })
        assertEquals(3, groups.size)
        assertEquals("today", groups[0].items.single().id)
    }

    private fun transcript(id: String, createdAt: Long) = Transcript(
        id = id,
        text = "Something said out loud.",
        language = "en-GB",
        createdAt = createdAt,
        durationMs = 1_000,
    )
}
