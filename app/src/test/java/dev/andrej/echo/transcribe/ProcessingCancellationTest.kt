package dev.andrej.echo.transcribe

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProcessingCancellationTest {
    @Test fun `stop cancels transcription and concurrent speaker finding`() = runTest {
        val stop = MutableStateFlow(false)
        var speechCancelled = false
        var speakersCancelled = false
        var analysisStarted = false
        val work = async {
            val completed = runUntilStopped(stop) {
                launch { try { awaitCancellation() } finally { speechCancelled = true } }
                launch { try { awaitCancellation() } finally { speakersCancelled = true } }
            }
            if (completed) analysisStarted = true
            completed
        }
        runCurrent()
        stop.value = true
        assertFalse(work.await())
        assertTrue(speechCancelled)
        assertTrue(speakersCancelled)
        assertFalse(analysisStarted)
    }

    @Test fun `stop still cancels speaker finding after transcription finishes`() = runTest {
        val stop = MutableStateFlow(false)
        val speechDone = CompletableDeferred<Unit>()
        var speakerCancelled = false
        val work = async {
            runUntilStopped(stop) {
                launch { speechDone.complete(Unit) }
                launch { try { awaitCancellation() } finally { speakerCancelled = true } }
            }
        }
        speechDone.await()
        stop.value = true
        assertFalse(work.await())
        assertTrue(speakerCancelled)
    }

    @Test fun `already stopped work never starts`() = runTest {
        var started = false
        assertFalse(runUntilStopped(MutableStateFlow(true)) { started = true })
        assertFalse(started)
    }

    @Test fun `completed work permits later stages`() = runTest {
        assertTrue(runUntilStopped(MutableStateFlow(false)) {})
    }

    @Test fun `parent cancellation propagates instead of being treated as user stop`() = runTest {
        val work = async { runUntilStopped(MutableStateFlow(false)) { awaitCancellation() } }
        runCurrent()
        work.cancel()
        work.join()
        assertTrue(work.isCancelled)
    }
}
