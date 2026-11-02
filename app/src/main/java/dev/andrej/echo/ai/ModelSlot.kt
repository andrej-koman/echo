package dev.andrej.echo.ai

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One large model in memory at a time. Whisper Large and the LLM together pushed the S24 past
 * the low-memory killer, which then took the app in the foreground, and again on every restart
 * as WorkManager resumed the job.
 */
class ModelSlot {

    private val mutex = Mutex()
    private var evictLlm: suspend () -> Unit = {}

    /** The LLM registers how to free itself, so Whisper can take the slot. */
    fun onEvictLlm(evict: suspend () -> Unit) {
        evictLlm = evict
    }

    suspend fun <T> llm(block: suspend () -> T): T = mutex.withLock { block() }

    suspend fun <T> whisper(block: suspend () -> T): T = mutex.withLock {
        evictLlm()
        block()
    }
}
