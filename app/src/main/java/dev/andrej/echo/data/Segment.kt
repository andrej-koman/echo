package dev.andrej.echo.data

/** [speaker] is the speaker's index within its transcript; null before diarization. */
data class Segment(val startMs: Long, val endMs: Long, val text: String, val speaker: Int? = null)

data class Speaker(val index: Int, val name: String?) {
    val label: String get() = name ?: "Speaker ${index + 1}"
}
