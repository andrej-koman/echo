package dev.andrej.echo.speech.speakers

import dev.andrej.echo.ai.ModelState
import dev.andrej.echo.ai.ModelStore
import dev.andrej.echo.data.Segment
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SpeakerTurn(val startMs: Long, val endMs: Long, val speaker: Int)

class Diarizer(
    private val segmentation: ModelStore,
    private val embedding: ModelStore,
) {
    val ready: Boolean
        get() = segmentation.state.value is ModelState.Ready && embedding.state.value is ModelState.Ready

    suspend fun download(onProgress: (Float) -> Unit) {
        val total = (segmentation.spec.bytes + embedding.spec.bytes).toFloat()
        segmentation.download()
        onProgress(segmentation.spec.bytes / total)
        embedding.download()
    }

    /** Not cancellable part way: sherpa-onnx offers no abort, so a cancelled caller waits for it. */
    suspend fun diarize(pcm: File, onProgress: (Int) -> Unit): List<SpeakerTurn> {
        val segmentationModel = segmentation.fileOrNull() ?: throw IOException("no segmentation model")
        val embeddingModel = embedding.fileOrNull() ?: throw IOException("no embedding model")
        val cpus = littleCores()
        val flat = withContext(Dispatchers.Default) {
            SpeakerNative.diarize(
                segmentationModel.absolutePath,
                embeddingModel.absolutePath,
                pcm.absolutePath,
                if (cpus.isEmpty()) 2 else cpus.size,
                cpus,
                onProgress::invoke,
            )
        } ?: throw IOException("diarization failed")
        return (flat.indices step 3).map { i ->
            SpeakerTurn((flat[i] * 1000).toLong(), (flat[i + 1] * 1000).toLong(), flat[i + 2].toInt())
        }
    }

    /** It runs beside Whisper, which keeps the faster cores busy; empty when cores all look alike. */
    private fun littleCores(): IntArray {
        val maxFreq = (0 until Runtime.getRuntime().availableProcessors()).associateWith { cpu ->
            File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq")
                .runCatching { readText().trim().toLong() }
                .getOrNull() ?: return IntArray(0)
        }
        val slowest = maxFreq.values.min()
        val little = maxFreq.filterValues { it == slowest }.keys
        return if (little.size == maxFreq.size) IntArray(0) else little.toIntArray()
    }
}

/**
 * Gives each segment the speaker it overlaps most, or the nearest turn's when it overlaps none,
 * then renumbers speakers by first appearance so "Speaker 1" is whoever talks first. Returns the
 * segments and the number of speakers.
 */
fun assignSpeakers(segments: List<Segment>, turns: List<SpeakerTurn>): Pair<List<Segment>, Int> {
    if (turns.isEmpty()) return segments to 0

    val raw = segments.map { segment ->
        val overlaps = turns.groupBy { it.speaker }.mapValues { (_, own) ->
            own.sumOf { maxOf(0L, minOf(it.endMs, segment.endMs) - maxOf(it.startMs, segment.startMs)) }
        }
        val best = overlaps.maxBy { it.value }
        if (best.value > 0) {
            best.key
        } else {
            turns.minBy { maxOf(it.startMs - segment.endMs, segment.startMs - it.endMs) }.speaker
        }
    }
    val order = raw.distinct().withIndex().associate { (index, speaker) -> speaker to index }
    return segments.mapIndexed { i, segment -> segment.copy(speaker = order.getValue(raw[i])) } to order.size
}
