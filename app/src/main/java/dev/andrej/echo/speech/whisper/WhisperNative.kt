package dev.andrej.echo.speech.whisper

fun interface WhisperProgress {
    fun onProgress(percent: Int)
}

interface WhisperListener : WhisperProgress {
    /** A segment as Whisper finishes it, times relative to the samples passed in. */
    fun onSegment(startMs: Long, endMs: Long, text: ByteArray)
}

/** Mirrors `src/main/cpp/whisper_jni.cpp`; a handle is a `whisper_context` plus its abort flag. */
object WhisperNative {
    init {
        System.loadLibrary("echowhisper")
    }

    external fun init(modelPath: String): Long
    external fun free(handle: Long)

    /**
     * 16 kHz mono float samples. Null [language] auto-detects; [prompt] primes the spelling of
     * names. Returns 0 on success, -1 when aborted.
     */
    external fun full(
        handle: Long,
        samples: FloatArray,
        language: String?,
        prompt: String?,
        threads: Int,
        listener: WhisperListener,
    ): Int
    external fun abort(handle: Long)

    external fun segmentCount(handle: Long): Int
    external fun segmentStartMs(handle: Long, index: Int): Long
    external fun segmentEndMs(handle: Long, index: Int): Long
    external fun segmentText(handle: Long, index: Int): ByteArray
    external fun detectedLanguage(handle: Long): String

    external fun languages(): Array<String>
}
