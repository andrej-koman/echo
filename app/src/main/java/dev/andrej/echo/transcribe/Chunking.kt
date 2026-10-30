package dev.andrej.echo.transcribe

import dev.andrej.echo.speech.SAMPLE_RATE
import dev.andrej.echo.speech.whisper.Segment

/** Long files go to Whisper in pieces, so progress is saved as it goes and memory stays flat. */
const val CHUNK_SAMPLES = 5 * 60 * SAMPLE_RATE

private const val CUT_SEARCH_SAMPLES = 3 * SAMPLE_RATE
private const val CUT_WINDOW_SAMPLES = SAMPLE_RATE / 10

/**
 * Where to end a chunk: the start of the quietest 100 ms window in its last three seconds, so
 * the cut lands between words rather than through one.
 */
internal fun quietestCut(samples: FloatArray): Int {
    if (samples.size <= CUT_SEARCH_SAMPLES) return samples.size

    var best = samples.size
    var bestEnergy = Double.MAX_VALUE
    var start = samples.size - CUT_SEARCH_SAMPLES
    while (start + CUT_WINDOW_SAMPLES <= samples.size) {
        var energy = 0.0
        for (i in start until start + CUT_WINDOW_SAMPLES) energy += samples[i] * samples[i]
        if (energy < bestEnergy) {
            bestEnergy = energy
            best = start + CUT_WINDOW_SAMPLES / 2
        }
        start += CUT_WINDOW_SAMPLES / 2
    }
    return best
}

/** Whisper fills silence and music with bracketed captions like `[BLANK_AUDIO]` or `(music)`. */
internal fun transcriptText(segments: List<Segment>): String =
    segments.map { it.text.trim() }
        .filterNot { it.isEmpty() || BRACKETED.matches(it) }
        .joinToString(" ")

private val BRACKETED = Regex("""^[\[(][^\])]*[\])]$""")
