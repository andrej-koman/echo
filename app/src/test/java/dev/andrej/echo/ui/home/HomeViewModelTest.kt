package dev.andrej.echo.ui.home

import dev.andrej.echo.data.TodoItem
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelTest {

    private val zone = ZoneId.systemDefault()
    private val now = 1_700_000_000_000L
    private val dayMs = TimeUnit.DAYS.toMillis(1)

    private fun item(id: String, dueAt: Long, hasTime: Boolean, done: Boolean = false, hasDate: Boolean = true) = TodoItem(
        id = id,
        sourceTranscriptId = "t1",
        text = id,
        dueAt = dueAt,
        hasTime = hasTime,
        hasDate = hasDate,
        done = done,
        createdAt = 0,
    )

    @Test
    fun `timed item due today wins the hero slot over an untimed one`() {
        val items = listOf(
            item("untimed", dueAt = now, hasTime = false),
            item("timed", dueAt = now, hasTime = true),
        )

        val state = buildState(emptyList(), items, emptyList(), now, zone)

        assertEquals("timed", (state.hero as HeroState.NextUp).item.id)
    }

    @Test
    fun `done items never reach the hero slot`() {
        val items = listOf(
            item("done", dueAt = now, hasTime = true, done = true),
            item("not done", dueAt = now, hasTime = true),
        )

        val state = buildState(emptyList(), items, emptyList(), now, zone)

        assertEquals("not done", (state.hero as HeroState.NextUp).item.id)
    }

    @Test
    fun `a dated item wins the hero slot over a dateless one`() {
        val items = listOf(
            item("dateless", dueAt = now, hasTime = false, hasDate = false),
            item("dated", dueAt = now, hasTime = true),
        )

        val state = buildState(emptyList(), items, emptyList(), now, zone)

        assertEquals("dated", (state.hero as HeroState.NextUp).item.id)
    }

    @Test
    fun `a dateless item becomes hero when it is the only one left`() {
        val items = listOf(item("dateless", dueAt = now, hasTime = false, hasDate = false))

        val state = buildState(emptyList(), items, emptyList(), now, zone)

        assertEquals("dateless", (state.hero as HeroState.NextUp).item.id)
    }

    @Test
    fun `nothing due today clears the hero and switches to coming up`() {
        val items = listOf(item("later", dueAt = now + 2 * dayMs, hasTime = false))

        val state = buildState(emptyList(), items, emptyList(), now, zone)

        assertEquals(HeroState.Clear, state.hero)
        assertEquals("Coming up", state.thenLabel)
        assertEquals(listOf("later"), state.thenRows.map { it.item.id })
    }

    @Test
    fun `the then strip is capped at two rows and reconciles the untimed remainder`() {
        val items = listOf(
            item("hero", dueAt = now, hasTime = true),
            item("timed1", dueAt = now + 1_000, hasTime = true),
            item("timed2", dueAt = now + 2_000, hasTime = true),
            item("untimed1", dueAt = now, hasTime = false),
            item("untimed2", dueAt = now, hasTime = false),
        )

        val state = buildState(emptyList(), items, emptyList(), now, zone)

        assertEquals(2, state.thenRows.size)
        assertTrue(state.thenRows.all { it.item.hasTime })
        assertEquals(2, state.thenMoreUntimed)
        assertEquals(4, state.thenTotalCount)
    }
}
