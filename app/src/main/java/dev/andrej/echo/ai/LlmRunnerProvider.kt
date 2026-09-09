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
 *
 * [preferredBackend], when non-null, forces that specific backend instead: no fallback to the
 * next candidate if it turns out unsupported, since a manual pick is the user overriding the
 * automatic choice on purpose.
 */
class LlmRunnerProvider(
    private val candidates: Map<LlmBackend, LlmRunner>,
    private val preferredBackend: () -> LlmBackend? = { null },
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
        val preferred = preferredBackend()
        if (preferred != null) {
            val candidate = candidates[preferred]
                ?: return NoLlmRunner("no backend registered for $preferred")
            val availability = candidate.availability()
            return if (availability !is LlmAvailability.Unsupported) {
                log("using $preferred (forced, $availability)")
                candidate
            } else {
                log("$preferred forced but unsupported: ${availability.reason}")
                NoLlmRunner(availability.reason)
            }
        }

        val reasons = mutableListOf<String>()

        for ((backend, candidate) in candidates) {
            val availability = candidate.availability()
            if (availability !is LlmAvailability.Unsupported) {
                log("using $backend ($availability)")
                return candidate
            }
            reasons += "$backend: ${availability.reason}"
        }

        log("no language model available — ${reasons.joinToString("; ")}")
        return NoLlmRunner(reasons.joinToString("; ").ifEmpty { "no backends configured" })
    }

    private companion object {
        const val TAG = "LlmRunnerProvider"
    }
}
