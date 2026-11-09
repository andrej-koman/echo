package dev.andrej.echo.transcribe

import dev.andrej.echo.data.Segment
import dev.andrej.echo.data.Transcript
import dev.andrej.echo.speech.SAMPLE_RATE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

internal suspend fun observeCappedUnlocks(
    isPro: Flow<Boolean>,
    transcripts: Flow<List<Transcript>>,
    enqueue: (String) -> Unit,
) {
    val scheduled = mutableSetOf<String>()
    combine(isPro, transcripts) { pro, notes ->
        pro to notes.filter { it.transcribedUntilMs != null }.map { it.id }.toSet()
    }.distinctUntilChanged().collect { (pro, capped) ->
        scheduled.retainAll(capped)
        if (!pro) scheduled.clear()
        if (pro) (capped - scheduled).forEach { id ->
            enqueue(id)
            scheduled += id
        }
    }
}

internal fun resumeChunk(
    chunk: TranscriptionCheckpoint.Chunk?,
    transcript: Transcript?,
    segments: List<Segment>,
    preserveCompleted: Boolean = false,
): TranscriptionCheckpoint.Chunk? {
    val seed = transcript?.transcribedUntilMs?.let { untilMs ->
        TranscriptionCheckpoint.Chunk(untilMs * SAMPLE_RATE / 1000, transcript.language,
            segments.takeWhile { it.startMs < untilMs })
    }
    if (seed == null) return if (preserveCompleted && chunk != null) {
        chunk.copy(segments = segments + chunk.segments.drop(segments.size))
    } else chunk
    if (chunk == null || chunk.nextSample <= seed.nextSample) return seed
    return chunk.copy(segments = seed.segments + chunk.segments.dropWhile { it.startMs < transcript.transcribedUntilMs })
}

internal fun preservePrefixSpeakers(assigned: List<Segment>, prefix: List<Segment>, existingCount: Int): List<Segment> {
    val mapping = assigned.take(prefix.size).mapIndexedNotNull { index, segment ->
        val detected = segment.speaker
        val saved = prefix[index].speaker
        if (detected != null && saved != null) detected to saved else null
    }.groupBy({ it.first }, { it.second }).mapValues { (_, saved) ->
        saved.groupingBy { it }.eachCount().maxBy { it.value }.key
    }.toMutableMap()
    var next = maxOf(existingCount, (prefix.mapNotNull { it.speaker }.maxOrNull() ?: -1) + 1)
    return assigned.mapIndexed { index, segment ->
        if (index < prefix.size) prefix[index]
        else segment.copy(speaker = segment.speaker?.let { mapping.getOrPut(it) { next++ } })
    }
}
