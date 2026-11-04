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
}
