package dev.andrej.echo.transcribe

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Cancels every child stage on Stop, but lets the caller safely preserve partial results. */
internal suspend fun runUntilStopped(stop: Flow<Boolean>, process: suspend CoroutineScope.() -> Unit): Boolean =
    coroutineScope {
        if (stop.first()) return@coroutineScope false
        val processing = async(block = process)
        val watcher = launch {
            stop.first { it }
            processing.cancel()
        }
        try {
            processing.await()
            true
        } catch (e: CancellationException) {
            // WorkManager/system cancellation must still propagate rather than count as success.
            currentCoroutineContext().ensureActive()
            if (!stop.first()) throw e
            false
        } finally {
            watcher.cancel()
        }
    }
