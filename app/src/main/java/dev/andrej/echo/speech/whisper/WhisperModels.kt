package dev.andrej.echo.speech.whisper

import dev.andrej.echo.ai.ModelSpec

class WhisperModel(val name: String, val spec: ModelSpec)

private const val BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/"

val WHISPER_LARGE_V3_TURBO = WhisperModel(
    name = "Whisper Large V3 Turbo",
    spec = ModelSpec(BASE_URL + "ggml-large-v3-turbo-q5_0.bin", "ggml-large-v3-turbo-q5_0.bin", 574_041_195),
)

val WHISPER_SMALL = WhisperModel(
    name = "Whisper Small",
    spec = ModelSpec(BASE_URL + "ggml-small-q5_1.bin", "ggml-small-q5_1.bin", 190_085_487),
)

/** An 8 GB phone reports about 7.3 GB of total memory; 6 GB phones fall to Small. */
fun whisperModelFor(totalMemoryBytes: Long): WhisperModel =
    if (totalMemoryBytes >= 7_000_000_000L) WHISPER_LARGE_V3_TURBO else WHISPER_SMALL
