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
    private val evictLlm = mutableMapOf<String, suspend () -> Unit>()
    private var activeLlm: String? = null

    /** Each model registers its own close operation. */
    fun onEvictLlm(modelId: String, evict: suspend () -> Unit) {
        evictLlm[modelId] = evict
    }

    /** Compatibility for the single-model callers and tests. */
    fun onEvictLlm(evict: suspend () -> Unit) {
        onEvictLlm(DEFAULT_MODEL_ID, evict)
        activeLlm = DEFAULT_MODEL_ID
    }

    suspend fun <T> llm(modelId: String, block: suspend () -> T): T = mutex.withLock {
        if (activeLlm != modelId) {
            activeLlm?.let { evictLlm[it]?.invoke() }
            activeLlm = modelId
        }
        block()
    }

    suspend fun <T> llm(block: suspend () -> T): T = llm(DEFAULT_MODEL_ID, block)

    /** Closes a model before its store removes the weights. */
    suspend fun evictLlm(modelId: String) = mutex.withLock {
        if (activeLlm == modelId) {
            evictLlm[modelId]?.invoke()
            activeLlm = null
        }
    }

    suspend fun <T> whisper(block: suspend () -> T): T = mutex.withLock {
        activeLlm?.let { evictLlm[it]?.invoke() }
        activeLlm = null
        block()
    }

    private companion object {
        const val DEFAULT_MODEL_ID = "default"
    }
}
