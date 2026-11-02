package dev.andrej.echo.ui.detail

import dev.andrej.echo.data.Segment

/** The segment playing at [positionMs]: the last one started by then, or -1 before the first. */
internal fun segmentAt(segments: List<Segment>, positionMs: Long): Int {
    var low = 0
    var high = segments.lastIndex
    var found = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (segments[mid].startMs <= positionMs) {
            found = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return found
}

/** Case-insensitive, literal (no regex) — what a person typing into a find box expects. */
internal fun matchRanges(text: String, query: String): List<IntRange> {
    if (query.isEmpty()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var from = 0
    while (true) {
        val at = text.indexOf(query, from, ignoreCase = true)
        if (at < 0) return ranges
        ranges += at until at + query.length
        from = at + query.length
    }
}

internal fun countMatches(segments: List<Segment>, query: String): Int =
    segments.sumOf { matchRanges(it.text, query).size }

/** A segment left empty by the replacement is dropped, as an edited-away one would be. */
internal fun replaceAll(segments: List<Segment>, query: String, replacement: String): List<Segment> =
    if (query.isEmpty()) {
        segments
    } else {
        segments.mapNotNull { segment ->
            val text = segment.text.replace(query, replacement, ignoreCase = true).trim()
            text.takeIf { it.isNotEmpty() }?.let { segment.copy(text = it) }
        }
    }

/** Applies inline edits by index; a segment edited to nothing is dropped. */
internal fun applyEdits(segments: List<Segment>, edits: Map<Int, String>): List<Segment> =
    segments.mapIndexedNotNull { i, segment ->
        val text = (edits[i] ?: segment.text).trim()
        text.takeIf { it.isNotEmpty() }?.let { segment.copy(text = it) }
    }

internal fun assignSpeaker(segments: List<Segment>, indices: Set<Int>, speaker: Int): List<Segment> =
    segments.mapIndexed { i, segment -> if (i in indices) segment.copy(speaker = speaker) else segment }

/**
 * Splits segment [index] around the selected characters [selection] (end exclusive) and gives the
 * selected part [speaker]. Times are interpolated by character offset — Whisper's segments carry
 * no word timings to do better with.
 */
internal fun assignSelection(segments: List<Segment>, index: Int, selection: IntRange, speaker: Int): List<Segment> {
    val segment = segments[index]
    val text = segment.text
    val start = selection.first.coerceIn(0, text.length)
    val end = (selection.last + 1).coerceIn(start, text.length)
    fun at(offset: Int) = segment.startMs + (segment.endMs - segment.startMs) * offset / text.length.coerceAtLeast(1)

    val parts = listOf(
        Segment(segment.startMs, at(start), text.substring(0, start), segment.speaker),
        Segment(at(start), at(end), text.substring(start, end), speaker),
        Segment(at(end), segment.endMs, text.substring(end), segment.speaker),
    ).mapNotNull { part -> part.text.trim().takeIf { it.isNotEmpty() }?.let { part.copy(text = it) } }
    return segments.subList(0, index) + parts + segments.subList(index + 1, segments.size)
}
