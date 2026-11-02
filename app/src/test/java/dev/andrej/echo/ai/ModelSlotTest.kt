package dev.andrej.echo.ai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelSlotTest {

    @Test
    fun `whisper evicts the llm before it runs`() = runTest {
        val slot = ModelSlot()
        val events = mutableListOf<String>()
        slot.onEvictLlm { events += "evict" }

        slot.whisper { events += "whisper" }

        assertEquals(listOf("evict", "whisper"), events)
    }

    @Test
    fun `whisper waits for a running llm call, and the llm never evicts itself`() = runTest {
        val slot = ModelSlot()
        val events = mutableListOf<String>()
        slot.onEvictLlm { events += "evict" }
        val generating = CompletableDeferred<Unit>()

        launch { slot.llm { events += "llm start"; generating.await(); events += "llm end" } }
        runCurrent()
        launch { slot.whisper { events += "whisper" } }
        runCurrent()
        generating.complete(Unit)
        runCurrent()

        assertEquals(listOf("llm start", "llm end", "evict", "whisper"), events)
    }
}
