package dev.andrej.echo.ai

import android.util.Log
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Stands in when no backend on the list can run here, so callers never hold a null runner. */
class NoLlmRunner(private val reason: String) : LlmRunner {

    override suspend fun availability(): LlmAvailability = LlmAvailability.Unsupported(reason)

    override suspend fun warmup() = Unit

    override suspend fun generate(prompt: String, temperature: Float): String =
        error("no language model on this device: $reason")
}

/**
 * Picks the first backend the device can actually run, in the order given, and remembers the
 * answer: probing AICore costs an IPC. A runner that only needs a download still wins over one
 * further down the list — the download is the user's decision to make, not ours.
 */
class LlmRunnerProvider(
    private val candidates: List<LlmRunner>,
    private val log: (String) -> Unit = { Log.i(TAG, it) },
) {

    private val lock = Mutex()
    private var resolved: LlmRunner? = null
    private var resolvedGeneration = -1
    private val generation = AtomicInteger(0)

    /** Forces the next [runner] call to re-probe instead of returning the cached choice. */
    fun invalidate() {
        generation.incrementAndGet()
    }

    suspend fun runner(): LlmRunner {
        val current = generation.get()
        resolved?.let { if (resolvedGeneration == current) return it }

        return lock.withLock {
            val target = generation.get()
            resolved?.let { if (resolvedGeneration == target) return@withLock it }
            resolve().also {
                resolved = it
                resolvedGeneration = target
            }
        }
    }

    private suspend fun resolve(): LlmRunner {
        val reasons = mutableListOf<String>()

        for (candidate in candidates) {
            val availability = candidate.availability()
            if (availability !is LlmAvailability.Unsupported) {
                log("using ${candidate.javaClass.simpleName} ($availability)")
                return candidate
            }
            reasons += "${candidate.javaClass.simpleName}: ${availability.reason}"
        }

        log("no language model available — ${reasons.joinToString("; ")}")
        return NoLlmRunner(reasons.joinToString("; ").ifEmpty { "no backends configured" })
    }

    private companion object {
        const val TAG = "LlmRunnerProvider"
    }
}
