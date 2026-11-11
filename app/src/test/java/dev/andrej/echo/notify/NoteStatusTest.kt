package dev.andrej.echo.notify

import dev.andrej.echo.ai.Analysis
import dev.andrej.echo.ai.AnalysisBlock
import dev.andrej.echo.ai.AnalysisOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteStatusTest {
    @Test fun `success reports the stored task count`() {
        assertEquals(NoteStatus.Ready(3), noteStatus(AnalysisOutcome.Success(Analysis("T", "S", emptyList())), 3))
    }

    @Test fun `a parked analysis still announces the transcript`() {
        assertEquals(NoteStatus.TranscriptReady, noteStatus(AnalysisOutcome.Blocked(AnalysisBlock.NoBackend("memory")), 0))
        assertEquals(NoteStatus.TranscriptReady, noteStatus(AnalysisOutcome.Blocked(AnalysisBlock.WaitingForModel(1)), 0))
    }

    @Test fun `failed generation offers a retry and an empty transcript says nothing`() {
        assertTrue(noteStatus(AnalysisOutcome.Blocked(AnalysisBlock.GenerationFailed(null)), 0) is NoteStatus.Failed)
        assertTrue(noteStatus(AnalysisOutcome.Blocked(AnalysisBlock.EmptyResult), 0) is NoteStatus.Failed)
        assertNull(noteStatus(AnalysisOutcome.Blocked(AnalysisBlock.EmptyTranscript), 0))
    }
}
