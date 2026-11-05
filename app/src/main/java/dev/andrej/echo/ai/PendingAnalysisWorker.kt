package dev.andrej.echo.ai

import dev.andrej.echo.data.AnalysisQueueRepository
import dev.andrej.echo.data.DerivedRepository
import dev.andrej.echo.data.TranscriptRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * Owns the pending-analysis queue's only writer path. Runs on an application-scoped
 * [CoroutineScope] so a drain outlives the screen that triggered it — the same reasoning as
 * [dev.andrej.echo.AppContainer]'s model download.
 */
class PendingAnalysisWorker(
    private val queue: AnalysisQueueRepository,
    private val transcripts: TranscriptRepository,
    private val derived: DerivedRepository,
    private val analyzer: TranscriptAnalyzer,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val completedAttempts = MutableSharedFlow<String>(extraBufferCapacity = 64)

    private val _progress = MutableStateFlow<Map<String, AnalysisProgress>>(emptyMap())
    val progress: StateFlow<Map<String, AnalysisProgress>> = _progress.asStateFlow()

    private val _activeId = MutableStateFlow<String?>(null)

    /** The transcript currently being analyzed, automatic or manual. Null between attempts. */
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    private val cancelled = MutableStateFlow(emptySet<String>())
    private val attempts = ConcurrentHashMap<String, Job>()

    /** Stops only this note; a drain can continue with other queued notes. */
    fun cancel(transcriptId: String) {
        cancelled.update { it + transcriptId }
        attempts[transcriptId]?.cancel()
        scope.launch { if (transcriptId in cancelled.value) queue.remove(transcriptId) }
    }

    /** An explicit transcription retry or Analyze tap may process a previously stopped note. */
    fun allow(transcriptId: String) = cancelled.update { it - transcriptId }

    private suspend fun attempt(transcriptId: String, block: suspend () -> Unit) = supervisorScope {
        val job = async(start = CoroutineStart.LAZY) {
            if (transcriptId !in cancelled.value) block()
        }
        attempts[transcriptId] = job
        try {
            job.await()
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            if (transcriptId !in cancelled.value) throw e
        } finally {
            attempts.remove(transcriptId, job)
            completedAttempts.tryEmit(transcriptId)
        }
    }

    private var drainJob: Job? = null
    private var rerunRequested = false

    /** Fire-and-forget. A request arriving mid-drain is folded into one rerun, not lost. */
    fun requestDrain() {
        if (drainJob?.isActive == true) {
            rerunRequested = true
            return
        }
        drainJob = scope.launch { drainLoop() }
    }

    /** Used where the caller has to wait for the result: a transcription job, and the manual retry. */
    suspend fun requestDrainAndAwait() {
        requestDrain()
        drainJob?.join()
    }

    /** A transcription job ends when its own note is processed, not the rest of the queue. */
    suspend fun requestDrainAndAwait(transcriptId: String) = coroutineScope {
        val finished = async(start = CoroutineStart.UNDISPATCHED) {
            completedAttempts.first { it == transcriptId }
        }
        requestDrain()
        val drained = async { drainJob?.join() }
        try {
            select<Unit> {
                finished.onAwait {}
                drained.onAwait {}
            }
        } finally {
            finished.cancel()
            drained.cancel()
        }
    }

    /**
     * Fire-and-forget form of [analyzeNow], on the application scope so leaving the screen that
     * triggered it does not cancel the run — the manual Analyze button used to die with the
     * ViewModel on navigation.
     */
    fun analyzeNowAsync(transcriptId: String) {
        scope.launch { analyzeNow(transcriptId) }
    }

    /**
     * Bypasses the attempt cap and the "already analyzed" reconciliation that [drainOnce] applies
     * to the automatic queue — an explicit tap on Analyze always gets a fresh run, even for a
     * transcript analyzed before.
     */
    suspend fun analyzeNow(transcriptId: String) {
        allow(transcriptId)
        val transcript = transcripts.transcripts.first().firstOrNull { it.id == transcriptId } ?: return

        attempt(transcriptId) {
            _activeId.value = transcriptId
            try {
                val outcome = analyzer.analyze(transcript) { progress -> _progress.update { it + (transcript.id to progress) } }
                currentCoroutineContext().ensureActive()
                when (outcome) {
                    is AnalysisOutcome.Success -> applySuccess(transcriptId, outcome)
                    is AnalysisOutcome.Blocked -> {
                        queue.reset(transcriptId)
                        queue.recordBlock(transcriptId, outcome.reason, outcome.reason.consumesAttempt())
                    }
                }
            } finally {
                _progress.update { it - transcript.id }
                _activeId.value = null
            }
        }
    }

    private suspend fun drainLoop() {
        do {
            rerunRequested = false
            drainOnce()
        } while (rerunRequested)
    }

    private suspend fun drainOnce() {
        val currentTranscripts = transcripts.transcripts.first()
        val currentPending = queue.pending.first()

        for (entry in currentPending) {
            if (entry.transcriptId in cancelled.value) {
                queue.remove(entry.transcriptId)
                completedAttempts.tryEmit(entry.transcriptId)
                continue
            }
            val transcript = currentTranscripts.firstOrNull { it.id == entry.transcriptId }
            if (transcript == null || transcript.analyzedAt != null) {
                queue.remove(entry.transcriptId)
                completedAttempts.tryEmit(entry.transcriptId)
                continue
            }
            if (entry.attempts >= MAX_ATTEMPTS) {
                completedAttempts.tryEmit(entry.transcriptId)
                continue
            }

            attempt(entry.transcriptId) {
                _activeId.value = entry.transcriptId
                try {
                    val outcome = analyzer.analyze(transcript) { progress -> _progress.update { it + (transcript.id to progress) } }
                    currentCoroutineContext().ensureActive()
                    when (outcome) {
                        is AnalysisOutcome.Success -> applySuccess(entry.transcriptId, outcome)
                        is AnalysisOutcome.Blocked ->
                            queue.recordBlock(entry.transcriptId, outcome.reason, outcome.reason.consumesAttempt())
                    }
                } finally {
                    _progress.update { it - transcript.id }
                    _activeId.value = null
                }
            }
        }
    }

    private suspend fun applySuccess(transcriptId: String, outcome: AnalysisOutcome.Success) {
        derived.replaceFor(
            transcriptId = transcriptId,
            items = outcome.analysis.items,
            createdAt = now(),
        )
        transcripts.attachAnalysis(
            id = transcriptId,
            title = outcome.analysis.title,
            summary = outcome.analysis.summary,
            analyzedAt = now(),
        )
        queue.remove(transcriptId)
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}

private fun AnalysisBlock.consumesAttempt(): Boolean = when (this) {
    is AnalysisBlock.GenerationFailed, is AnalysisBlock.EmptyResult -> true
    AnalysisBlock.EmptyTranscript, is AnalysisBlock.WaitingForModel, is AnalysisBlock.NoBackend -> false
}
