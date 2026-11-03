package dev.andrej.echo.speech.whisper

import dev.andrej.echo.data.Segment
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class WhisperResult(val segments: List<Segment>, val language: String)

/** One loaded model. Not thread-safe: one [transcribe] at a time, then [close]. */
class WhisperTranscriber private constructor(private val handle: Long) : Closeable {

    /**
     * Cancelling the caller aborts the native run; the call still waits for it to stop, because
     * freeing the context mid-run would crash.
     */
    suspend fun transcribe(
        samples: FloatArray,
        language: String?,
        prompt: String?,
        onSegment: (Segment) -> Unit = {},
        onProgress: (Int) -> Unit,
    ): WhisperResult = coroutineScope {
        val listener = object : WhisperListener {
            override fun onProgress(percent: Int) = onProgress(percent)
            override fun onSegment(startMs: Long, endMs: Long, text: ByteArray) =
                onSegment(Segment(startMs = startMs, endMs = endMs, text = text.decodeToString().trim()))
        }
        val run = async(Dispatchers.Default) {
            WhisperNative.full(handle, samples, language, prompt, threads(), listener)
        }
        val code = try {
            run.await()
        } catch (e: CancellationException) {
            WhisperNative.abort(handle)
            throw e
        }
        check(code == 0) { "whisper_full failed ($code)" }

        val segments = (0 until WhisperNative.segmentCount(handle)).map { i ->
            Segment(
                startMs = WhisperNative.segmentStartMs(handle, i),
                endMs = WhisperNative.segmentEndMs(handle, i),
                text = WhisperNative.segmentText(handle, i).decodeToString().trim(),
            )
        }
        WhisperResult(segments, WhisperNative.detectedLanguage(handle))
    }

    override fun close() = WhisperNative.free(handle)

    companion object {
        fun load(model: File): WhisperTranscriber {
            val handle = WhisperNative.init(model.absolutePath)
            check(handle != 0L) { "could not load ${model.name}" }
            return WhisperTranscriber(handle)
        }

        // Past the big cores, extra threads wait on the little ones and slow every step down.
        private fun threads() = (Runtime.getRuntime().availableProcessors() - 4).coerceIn(2, 6)
    }
}
