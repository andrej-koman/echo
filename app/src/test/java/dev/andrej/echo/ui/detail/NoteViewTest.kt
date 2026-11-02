package dev.andrej.echo.ui.detail

import dev.andrej.echo.data.Segment
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteViewTest {

    @Test
    fun `runs group consecutive lines by speaker`() {
        val segments = listOf(
            Segment(0, 1, "a", 0),
            Segment(1, 2, "b", 0),
            Segment(2, 3, "c", 1),
            Segment(3, 4, "d", null),
            Segment(4, 5, "e", null),
        )

        assertEquals(listOf(0..1, 2..2, 3..4), runs(segments))
    }

    @Test
    fun `no segments, no runs`() {
        assertEquals(emptyList<IntRange>(), runs(emptyList()))
    }

    @Test
    fun `speed cycles up then back to slow`() {
        assertEquals(listOf(1.25f, 1.5f, 2f, 0.75f, 1f), generateSequence(1f, ::nextSpeed).drop(1).take(5).toList())
    }

    @Test
    fun `speed labels drop trailing zeros`() {
        assertEquals("1×", speedLabel(1f))
        assertEquals("1.25×", speedLabel(1.25f))
        assertEquals("0.75×", speedLabel(0.75f))
    }
}
