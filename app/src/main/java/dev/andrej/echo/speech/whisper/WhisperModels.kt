package dev.andrej.echo.speech.whisper

import dev.andrej.echo.ai.ModelSpec

class WhisperModel(val id: String, val name: String, val hint: String, val spec: ModelSpec)

private val BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/"

val WHISPER_LARGE_V3_TURBO = WhisperModel(
    id = "large-v3-turbo",
    name = "Whisper Large V3 Turbo",
    hint = "Most accurate, slowest · 574 MB",
    spec = ModelSpec(BASE_URL + "ggml-large-v3-turbo-q5_0.bin", "ggml-large-v3-turbo-q5_0.bin", 574_041_195),
)

val WHISPER_MEDIUM = WhisperModel(
    id = "medium",
    name = "Whisper Medium",
    hint = "Accurate, a bit faster · 539 MB",
    spec = ModelSpec(BASE_URL + "ggml-medium-q5_0.bin", "ggml-medium-q5_0.bin", 539_212_467),
)

val WHISPER_SMALL = WhisperModel(
    id = "small",
    name = "Whisper Small",
    hint = "Several times faster, misses more · 190 MB",
    spec = ModelSpec(BASE_URL + "ggml-small-q5_1.bin", "ggml-small-q5_1.bin", 190_085_487),
)

val WHISPER_BASE = WhisperModel(
    id = "base",
    name = "Whisper Base",
    hint = "Fastest, rough text · 60 MB",
    spec = ModelSpec(BASE_URL + "ggml-base-q5_1.bin", "ggml-base-q5_1.bin", 59_707_625),
)

/** Silero VAD for whisper.cpp, commit-pinned. Lets Whisper skip silence. */
val WHISPER_VAD = ModelSpec(
    url = "https://huggingface.co/ggml-org/whisper-vad/resolve/9ffd54a1e1ee413ddf265af9913beaf518d1639b/" +
        "ggml-silero-v6.2.0.bin",
    fileName = "ggml-silero-v6.2.0.bin",
    bytes = 885_098,
)

/** An 8 GB phone reports about 7.3 GB total memory; 6 GB phones fall back to Small. */
private fun bigModelsFit(totalMemoryBytes: Long) = totalMemoryBytes >= 7_000_000_000L

fun whisperModelFor(totalMemoryBytes: Long): WhisperModel =
    if (bigModelsFit(totalMemoryBytes)) WHISPER_LARGE_V3_TURBO else WHISPER_SMALL

/** What the user may pick: Large and Medium only where they fit in memory beside the app. */
fun whisperChoicesFor(totalMemoryBytes: Long): List<WhisperModel> =
    if (bigModelsFit(totalMemoryBytes)) {
        listOf(WHISPER_LARGE_V3_TURBO, WHISPER_MEDIUM, WHISPER_SMALL, WHISPER_BASE)
    } else {
        listOf(WHISPER_SMALL, WHISPER_BASE)
    }
