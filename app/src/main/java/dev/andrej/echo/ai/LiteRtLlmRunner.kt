package dev.andrej.echo.ai

import android.app.ActivityManager
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A model we ship the download for, so it works on every device Gemini Nano skips. Weights come
 * from [ModelStore]; nothing here runs until the user has asked for that download.
 */
class LiteRtLlmRunner(
    private val store: ModelStore,
    private val activityManager: ActivityManager?,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : LlmRunner {

    private val lock = Mutex()
    private var engine: Engine? = null

    override suspend fun availability(): LlmAvailability {
        val memory = totalMemoryBytes()
        if (memory in 1 until MIN_TOTAL_MEMORY_BYTES) {
            return LlmAvailability.Unsupported("needs about 4GB of memory, this device has less")
        }

        return when (val state = store.state.value) {
            is ModelState.Ready -> LlmAvailability.Ready
            is ModelState.Downloading -> LlmAvailability.Downloading(state.fraction)
            is ModelState.Failed -> LlmAvailability.NeedsDownload(store.spec.bytes)
            ModelState.Absent -> LlmAvailability.NeedsDownload(store.spec.bytes)
        }
    }

    override suspend fun warmup() {
        try {
            engineOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "warmup failed, the first call will be slow: ${e.message}")
        }
    }

    override suspend fun generate(prompt: String, temperature: Float): String {
        val engine = engineOrNull() ?: error("the model is not on this device yet")

        return withContext(computeDispatcher) {
            val config = ConversationConfig(
                samplerConfig = SamplerConfig(
                    topK = TOP_K,
                    topP = TOP_P,
                    temperature = temperature.toDouble(),
                ),
            )

            engine.createConversation(config).use { conversation ->
                val reply = conversation.sendMessage(
                    prompt,
                    // Qwen3 reasons out loud unless told not to. The parser survives it, but the
                    // thinking tokens cost seconds on the Processing screen for nothing.
                    thinkingConfig = ThinkingConfig(enableThinking = false),
                )

                reply.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
            }
        }
    }

    private suspend fun engineOrNull(): Engine? {
        engine?.let { return it }

        val file = store.fileOrNull() ?: return null

        return lock.withLock {
            engine ?: withContext(computeDispatcher) {
                // initialize() reads a gigabyte off disk and sets up the accelerator, so it is
                // held open for the life of the process rather than paid per recording.
                Engine(EngineConfig(modelPath = file.absolutePath, backend = Backend.GPU()))
                    .also { it.initialize() }
            }.also { engine = it }
        }
    }

    private fun totalMemoryBytes(): Long {
        val info = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(info) ?: return 0
        return info.totalMem
    }

    private companion object {
        const val TAG = "LiteRtLlmRunner"
        const val MIN_TOTAL_MEMORY_BYTES = 3_500_000_000L
        const val TOP_K = 40
        const val TOP_P = 0.95
    }
}
