package dev.andrej.echo.transcribe

import java.io.File
import java.io.IOException

/** Records that Whisper and speaker assignment finished, even if later work kills the process. */
internal class TranscriptionCheckpoint(private val marker: File) {
    suspend fun run(transcribe: suspend () -> Unit, finalize: suspend () -> Unit) {
        if (!marker.isFile) {
            transcribe()
            if (!marker.createNewFile() && !marker.isFile) {
                throw IOException("could not save transcription checkpoint")
            }
        }
        finalize()
    }

    fun reset() {
        if (marker.exists() && !marker.delete()) {
            throw IOException("could not reset transcription checkpoint")
        }
    }
}
