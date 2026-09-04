package dev.andrej.echo.ai

import dev.andrej.echo.data.AnalysisQueueRepository
import dev.andrej.echo.data.DerivedRepository
import dev.andrej.echo.data.TranscriptRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

    private val _activeId = MutableStateFlow<String?>(null)

    /** The transcript currently being analyzed, automatic or manual. Null between attempts. */
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

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

    /** Used where the caller has to wait for the result — recording, and the manual retry. */
    suspend fun requestDrainAndAwait() {
        requestDrain()
        drainJob?.join()
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
        val transcript = transcripts.transcripts.first().firstOrNull { it.id == transcriptId } ?: return

        _activeId.value = transcriptId
        try {
            when (val outcome = analyzer.analyze(transcript)) {
                is AnalysisOutcome.Success -> applySuccess(transcriptId, outcome)
                is AnalysisOutcome.Blocked -> {
                    queue.reset(transcriptId)
                    queue.recordBlock(transcriptId, outcome.reason, outcome.reason.consumesAttempt())
                }
            }
        } finally {
            _activeId.value = null
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
            val transcript = currentTranscripts.firstOrNull { it.id == entry.transcriptId }
            if (transcript == null || transcript.deletedAt != null || transcript.analyzedAt != null) {
                queue.remove(entry.transcriptId)
                continue
            }
            if (entry.attempts >= MAX_ATTEMPTS) continue

            _activeId.value = entry.transcriptId
            try {
                when (val outcome = analyzer.analyze(transcript)) {
                    is AnalysisOutcome.Success -> applySuccess(entry.transcriptId, outcome)
                    is AnalysisOutcome.Blocked ->
                        queue.recordBlock(entry.transcriptId, outcome.reason, outcome.reason.consumesAttempt())
                }
            } finally {
                _activeId.value = null
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
