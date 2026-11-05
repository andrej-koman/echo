package dev.andrej.echo.ai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun `switching models closes the previous engine before the next is used`() = runTest {
        val slot = ModelSlot()
        val events = mutableListOf<String>()
        slot.onEvictLlm("fast") { events += "close fast" }
        slot.onEvictLlm("default") { events += "close default" }

        slot.llm("fast") { events += "use fast" }
        slot.llm("default") { events += "use default" }

        assertEquals(listOf("use fast", "close fast", "use default"), events)
    }

    @Test
    fun `whisper closes whichever model is currently loaded`() = runTest {
        val slot = ModelSlot()
        val events = mutableListOf<String>()
        slot.onEvictLlm("fast") { events += "close fast" }
        slot.onEvictLlm("default") { events += "close default" }

        slot.llm("default") { events += "use default" }
        slot.whisper { events += "whisper" }

        assertEquals(listOf("use default", "close default", "whisper"), events)
    }
}
