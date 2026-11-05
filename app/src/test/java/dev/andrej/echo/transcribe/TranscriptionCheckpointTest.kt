package dev.andrej.echo.transcribe

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import kotlinx.coroutines.test.runTest

class TranscriptionCheckpointTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `restart after transcription finishes only finalization`() = runTest {
        val marker = folder.root.resolve("speech-complete")
        var transcriptions = 0
        var finalizations = 0

        try {
            TranscriptionCheckpoint(marker).run(
                transcribe = { transcriptions++ },
                finalize = { finalizations++; throw IOException("process stopped after transcription") },
            )
        } catch (_: IOException) {
        }
        TranscriptionCheckpoint(marker).run(
            transcribe = { transcriptions++ },
            finalize = { finalizations++ },
        )

        assertEquals(1, transcriptions)
        assertEquals(2, finalizations)
    }

    @Test fun `explicit retry transcribes again`() = runTest {
        val checkpoint = TranscriptionCheckpoint(folder.root.resolve("speech-complete"))
        var transcriptions = 0
        checkpoint.run(transcribe = { transcriptions++ }, finalize = {})

        checkpoint.reset()
        checkpoint.run(transcribe = { transcriptions++ }, finalize = {})

        assertEquals(2, transcriptions)
    }
    @Test fun `chunk snapshot survives restart and explicit retry clears it`() {
        val marker = folder.root.resolve("speech-complete")
        val segments = listOf(dev.andrej.echo.data.Segment(0, 1200, "hello"))
        TranscriptionCheckpoint(marker).saveChunk(48000, "sl", segments)
        val restarted = TranscriptionCheckpoint(marker)
        assertEquals(48000L, restarted.chunk()!!.nextSample)
        assertEquals("sl", restarted.chunk()!!.language)
        assertEquals(segments, restarted.chunk()!!.segments)
        restarted.reset()
        assertEquals(null, restarted.chunk())
    }

    @Test fun `unfinished import is failed and complete import is queued`() {
        val dir = folder.newFolder()
        dir.resolve("source.part").writeText("truncated")
        assertEquals(RecoveryAction.FAILED, recoveryAction(dir, true, true))
        dir.resolve("source.part").renameTo(dir.resolve("source"))
        assertEquals(RecoveryAction.ENQUEUE, recoveryAction(dir, true, true))
        assertEquals(RecoveryAction.NONE, recoveryAction(dir, true, false))
        dir.resolve("audio.pcm").writeBytes(byteArrayOf(1, 2))
        assertEquals(RecoveryAction.ENQUEUE, recoveryAction(dir, true, true))
    }
    @Test fun `partial next checkpoint leaves last completed chunk intact`() {
        val marker = folder.root.resolve("speech-complete")
        val checkpoint = TranscriptionCheckpoint(marker)
        checkpoint.saveChunk(16000, "en", listOf(dev.andrej.echo.data.Segment(0, 500, "first")))
        folder.root.resolve("speech-chunk.part").writeText("interrupted write")
        assertEquals(16000L, TranscriptionCheckpoint(marker).chunk()!!.nextSample)
        assertEquals("first", TranscriptionCheckpoint(marker).chunk()!!.segments.single().text)
    }

    @Test fun `known recording in save before enqueue window is recovered`() {
        val dir = folder.newFolder()
        dir.resolve("audio.pcm").writeBytes(byteArrayOf(1, 2))
        assertEquals(RecoveryAction.ENQUEUE, recoveryAction(dir, true, true))
        assertEquals(RecoveryAction.ENQUEUE, recoveryAction(dir, false, false))
        assertEquals(RecoveryAction.NONE, recoveryAction(dir, true, false))
    }
}
