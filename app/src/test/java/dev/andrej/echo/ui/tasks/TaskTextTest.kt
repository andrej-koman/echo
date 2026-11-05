package dev.andrej.echo.ui.tasks

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskTextTest {
    @Test
    fun `saved lowercase tasks read as sentences`() {
        assertEquals("Go to the barber shop.", formatTaskText("  go to the barber shop  "))
        assertEquals("Go to the store.", formatTaskText("go to the store"))
    }

    @Test
    fun `proper names acronyms and existing punctuation are preserved`() {
        assertEquals("Call iPhone support!", formatTaskText("call iPhone support!"))
        assertEquals("Review NASA's report.", formatTaskText("review NASA's report."))
        assertEquals("Ask Alex why?", formatTaskText("ask Alex why?"))
        assertEquals("Wait…", formatTaskText("wait…"))
    }

    @Test
    fun `leading numbers and quoted endings are handled`() {
        assertEquals("10 minutes of Yoga.", formatTaskText("10 minutes of Yoga"))
        assertEquals("Read ‘Hello!’", formatTaskText("read ‘Hello!’"))
        assertEquals("", formatTaskText("  "))
    }
}
