package dev.andrej.echo.data

import java.util.Locale

data class TaskSource(val start: Int, val end: Int, val actionStart: Int)

private val sourceWords = Regex("[\\p{L}\\p{N}]+")
private val sourceFiller = setOf(
    "a", "an", "the", "to", "at", "in", "on", "for", "of", "with", "and", "my", "me", "i",
    "go", "get", "make", "have", "do", "be", "it", "please", "remind", "need", "should", "will",
)

/** Character ranges keep a sentence intact even when Whisper split it across segments. */
fun taskSentences(text: String): List<IntRange> {
    val continuation = "(?:then|also|oh,? and also|and (?:(?:i )?(?:need|have|will|should)|buy|call|go|pick|turn|visit|water|send|email|meet|pay|submit|finish|take|book|make|do|bring|collect))\\b"
    val nextAction = Regex("^$continuation", RegexOption.IGNORE_CASE)
    val boundaries = Regex("(?<=[.!?])\\s+|\\s+(?=(?i:$continuation))").findAll(text).filterNot {
        Regex("\\b[ap]\\.m\\.$", RegexOption.IGNORE_CASE).containsMatchIn(text.substring(0, it.range.first)) &&
            !nextAction.containsMatchIn(text.substring(it.range.last + 1))
    }
    val ranges = mutableListOf<IntRange>()
    var start = 0
    for (boundary in boundaries) {
        ranges += start until boundary.range.first
        start = boundary.range.last + 1
    }
    if (start < text.length) ranges += start until text.length
    return ranges.filter { !text.substring(it).isBlank() }
}

/** A stored quote must still exist; edited-away text must never link to a different sentence. */
fun taskSource(text: String, action: String, quote: String? = null, near: Int? = null, validateAction: Boolean = false): TaskSource? {
    val words = sourceWords.findAll(action).map { it.value.lowercase(Locale.ROOT) }
        .filter { it !in sourceFiller }.toSet()
    val quoted = if (!quote.isNullOrBlank()) {
        val pattern = Regex(quote.trim().split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
        pattern.findAll(text).map { it.range }.toList()
    } else null
    if (quoted != null && !validateAction) {
        if (quoted.isEmpty() || (quoted.size > 1 && near == null)) return null
        val range = quoted.minBy { kotlin.math.abs(it.first - (near ?: it.first)) }
        val actionWord = sourceWords.findAll(text.substring(range)).firstOrNull { it.value.lowercase(Locale.ROOT) in words }
        val start = actionWord?.let { range.first + it.range.first } ?: near?.takeIf { it in range } ?: range.first
        return TaskSource(range.first, range.last + 1, start)
    }
    if (words.isEmpty()) return null
    val candidates = if (quoted != null) taskSentences(text).flatMap { sentence ->
        quoted.mapNotNull { range ->
            val start = maxOf(sentence.first, range.first)
            val end = minOf(sentence.last, range.last)
            if (start <= end) start..end else null
        }
    } else taskSentences(text)
    val scored = candidates.mapNotNull { range ->
        val matches = sourceWords.findAll(text.substring(range)).filter { it.value.lowercase(Locale.ROOT) in words }.toList()
        val score = matches.map { it.value.lowercase(Locale.ROOT) }.toSet().size
        if (score.toDouble() / words.size < 0.6) null
        else Triple(range, score, range.first + matches.first().range.first)
    }
    val best = scored.maxOfOrNull { it.second } ?: return null
    val finalists = scored.filter { it.second == best }
    if (finalists.size > 1 && near == null) return null
    val chosen = finalists.minBy { kotlin.math.abs(it.third - (near ?: it.third)) }
    return TaskSource(chosen.first.first, chosen.first.last + 1, chosen.third)
}

private val phraseTime = Regex(
    "\\b(?:day after tomorrow|tomorrow|today|tonight|(?:next |this |on )?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)" +
        "|at\\s+(?:\\d|noon|midnight)|\\d{1,2}(?::\\d{2})?\\s*[ap]\\.?m\\b|all[ -]day)",
    RegexOption.IGNORE_CASE,
)

private val phraseLeadIn = setOf("i", "me", "my", "and", "remind", "please")

/** Drops lead-in filler ("I think that", "Then") so only the when and the action stay marked. */
fun taskPhrase(text: String, source: TaskSource): IntRange {
    val time = phraseTime.find(text.substring(source.start, source.actionStart))?.let { source.start + it.range.first }
    val start = time ?: sourceWords.findAll(text.substring(source.start, source.actionStart)).toList().asReversed()
        .takeWhile { it.value.lowercase(Locale.ROOT) in sourceFiller - phraseLeadIn }
        .lastOrNull()?.let { source.start + it.range.first } ?: source.actionStart
    var end = source.end
    while (end > start && (text[end - 1].isWhitespace() || text[end - 1] in ".,!?;:")) end--
    if (Regex("\\b[ap]\\.m$", RegexOption.IGNORE_CASE).containsMatchIn(text.substring(start, end))) end++
    return start until end
}

data class TaskTextLink(val taskId: String, val start: Int, val end: Int)

fun taskPlaybackOffset(task: TodoItem, segments: List<Segment>): Long? {
    val lines = segments.map { it.text.trim() }
    val starts = lines.runningFold(0) { at, line -> at + line.length + 1 }
    val near = segments.indexOfFirst { it.startMs == task.sourceOffsetMs }.takeIf { it >= 0 }?.let { starts[it] }
    val source = taskSource(lines.joinToString(" "), task.text, task.sourceQuote, near) ?: return null
    return segments.indices.lastOrNull { starts[it] <= source.actionStart }?.let { segments[it].startMs }
}

/** Offsets are relative to the trimmed segment rendered by the UI. */
fun taskLinks(texts: List<String>, tasks: List<TodoItem>): List<List<TaskTextLink>> {
    val trimmed = texts.map { it.trim() }
    val text = trimmed.joinToString(" ")
    val starts = trimmed.runningFold(0) { at, line -> at + line.length + 1 }
    val links = tasks.mapNotNull { task ->
        taskSource(text, task.text, task.sourceQuote)?.let { task.id to taskPhrase(text, it) }
    }
    return trimmed.mapIndexed { i, line ->
        links.mapNotNull { (id, source) ->
            val start = maxOf(source.first, starts[i]) - starts[i]
            val end = minOf(source.last + 1, starts[i] + line.length) - starts[i]
            if (start < end) TaskTextLink(id, start, end) else null
        }
    }
}
