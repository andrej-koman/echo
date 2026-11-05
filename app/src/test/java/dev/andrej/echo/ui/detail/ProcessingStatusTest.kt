package dev.andrej.echo.ui.detail

import dev.andrej.echo.ai.AnalysisProgress
import dev.andrej.echo.transcribe.TranscriptionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessingStatusTest {
    @Test
    fun `active analysis replaces stale speaker completion with its own model and progress`() {
        assertEquals(
            TranscriptionStatus.Analyzing(0.33f, "Qwen3 0.6B · Fast"),
            processingStatus(
                "note",
                TranscriptionStatus.FindingSpeakers(1f),
                AnalysisProgress("note", "Qwen3 0.6B · Fast", 0.33f),
            ),
        )
    }

    @Test
    fun `another note's analysis does not replace this note's transcription`() {
        assertEquals(
            TranscriptionStatus.Transcribing(0.5f),
            processingStatus(
                "note",
                TranscriptionStatus.Transcribing(0.5f),
                AnalysisProgress("other", "Qwen3 1.7B", 0.33f),
            ),
        )
    }
}
