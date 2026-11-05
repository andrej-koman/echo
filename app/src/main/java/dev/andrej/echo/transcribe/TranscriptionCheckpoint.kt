package dev.andrej.echo.transcribe

import java.io.File
import java.io.IOException
import java.io.DataInputStream
import java.io.DataOutputStream
import dev.andrej.echo.data.Segment

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

    private val chunks = File(marker.parentFile, "speech-chunk")

    data class Chunk(val nextSample: Long, val language: String, val segments: List<Segment>)

    fun chunk(): Chunk? {
        if (!chunks.isFile) return null
        return DataInputStream(chunks.inputStream().buffered()).use { input ->
            val next = input.readLong()
            val language = input.readUTF()
            val segments = List(input.readInt()) {
                Segment(input.readLong(), input.readLong(), input.readUTF())
            }
            Chunk(next, language, segments)
        }
    }

    fun saveChunk(nextSample: Long, language: String, segments: List<Segment>) {
        val partial = File(chunks.parentFile, "${chunks.name}.part")
        java.io.FileOutputStream(partial).use { file ->
            val output = DataOutputStream(file)
            output.writeLong(nextSample)
            output.writeUTF(language)
            output.writeInt(segments.size)
            segments.forEach {
                output.writeLong(it.startMs)
                output.writeLong(it.endMs)
                output.writeUTF(it.text)
            }
            output.flush()
            file.fd.sync()
        }
        if (!partial.renameTo(chunks)) throw IOException("could not save chunk checkpoint")
    }

    fun reset() {
        if (chunks.exists() && !chunks.delete()) throw IOException("could not reset chunk checkpoint")
        if (marker.exists() && !marker.delete()) {
            throw IOException("could not reset transcription checkpoint")
        }
    }
}

internal enum class RecoveryAction { NONE, ENQUEUE, FAILED }

internal fun recoveryAction(dir: File, known: Boolean, unfinished: Boolean): RecoveryAction {
    if (!known) return if (File(dir, "audio.pcm").length() > 0) RecoveryAction.ENQUEUE else RecoveryAction.NONE
    if (!unfinished) return RecoveryAction.NONE
    return if (File(dir, "source").isFile || File(dir, "audio.pcm").length() > 0) {
        RecoveryAction.ENQUEUE
    } else RecoveryAction.FAILED
}
