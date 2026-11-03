package dev.andrej.echo.speech.speakers

import dev.andrej.echo.speech.whisper.WhisperProgress

/** Mirrors `src/main/cpp/diarize_jni.cpp`, which calls sherpa-onnx's C API. */
object SpeakerNative {
    init {
        System.loadLibrary("sherpa-onnx-c-api")
        System.loadLibrary("echodiarize")
    }

    /**
     * Diarizes a 16 kHz PCM16 file. Returns start and end in seconds and a speaker number per
     * turn, flattened, sorted by start; null when the models or the file couldn't be read.
     * Non-empty [cpus] pins the run, onnxruntime's worker threads included, to those cores.
     */
    external fun diarize(
        segmentationModel: String,
        embeddingModel: String,
        pcmPath: String,
        threads: Int,
        cpus: IntArray,
        progress: WhisperProgress,
    ): FloatArray?
}
