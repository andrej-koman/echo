package dev.andrej.echo.ui.home

import dev.andrej.echo.data.TodoItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelTest {

    private fun item(id: String, dueAt: Long, hasTime: Boolean, done: Boolean = false) = TodoItem(
        id = id,
        sourceTranscriptId = "t1",
        text = id,
        dueAt = dueAt,
        hasTime = hasTime,
        done = done,
        createdAt = 0,
    )

    @Test
    fun `timed items come before untimed items due the same time`() {
        val items = listOf(
            item("untimed", dueAt = 1_000L, hasTime = false),
            item("timed", dueAt = 1_000L, hasTime = true),
        )

        assertEquals(listOf("timed", "untimed"), buildUpNext(items, emptyList(), now = 0).map { it.title })
    }

    @Test
    fun `done items are excluded`() {
        val items = listOf(
            item("done", dueAt = 1_000L, hasTime = true, done = true),
            item("not done", dueAt = 2_000L, hasTime = true),
        )

        assertEquals(listOf("not done"), buildUpNext(items, emptyList(), now = 0).map { it.title })
    }

    @Test
    fun `results are capped at three`() {
        val items = (1..5).map { item("item$it", dueAt = it.toLong(), hasTime = true) }

        assertEquals(3, buildUpNext(items, emptyList(), now = 0).size)
    }
}
