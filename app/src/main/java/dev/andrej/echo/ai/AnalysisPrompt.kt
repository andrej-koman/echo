package dev.andrej.echo.ai

import dev.andrej.echo.data.NewTodo
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class Analysis(
    val title: String?,
    val summary: String?,
    val items: List<NewTodo>,
)

data class NoteSummary(val title: String?, val summary: String?)

private val promptDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val promptTime = DateTimeFormatter.ofPattern("HH:mm")

fun buildSummaryPrompt(text: String): String = """
    Summarize this voice note. Reply with JSON only, both keys always present:

    {"title":"...","summary":"..."}

    title: what the note is about, at most 6 words.

    summary: the gist, in the language the speaker used. One or two sentences for a short note;
    for a long one, a short paragraph or a few "- " bullet points. Write it as the speaker's own
    note, never as a report about it: no "The speaker…", "Tells…", "Describes…", "Mentions…".

    Example. Voice note: "ok so the tap in the kitchen is dripping again, I need to call the
    plumber, probably tomorrow, and I should check if we still have spare washers"
    {"title":"Dripping kitchen tap","summary":"The kitchen tap is dripping again. Call the plumber tomorrow and check for spare washers."}

    Voice note:
    ""${'"'}
    $text
    ""${'"'}
""".trimIndent()

/** A long note is summarized part by part; this folds the parts' summaries into one. */
fun buildMergePrompt(parts: List<NoteSummary>): String = """
    These are summaries of consecutive parts of one long voice note. Combine them into a single
    summary of the whole note. Reply with JSON only, both keys always present:

    {"title":"...","summary":"..."}

    title: what the whole note is about, at most 6 words.

    summary: a short paragraph or a few "- " bullet points covering every part, in the language
    the parts are written in. Written as the speaker's own note, never as a report about it: no
    "The speaker…", "Tells…", "Describes…".

    ${parts.mapIndexed { i, part -> "Part ${i + 1}: ${part.title.orEmpty()}\n${part.summary.orEmpty()}" }.joinToString("\n\n")}
""".trimIndent()

/**
 * A yes/no asked before the tasks prompt: told in words not to list steps someone explains, the
 * 1.7B model listed every step of a how-to video as a task anyway.
 */
fun buildIntentPrompt(text: String): String = """
    Does the speaker of this voice note talk about things they themselves plan to do, or want to
    be reminded of? Explaining how to do something, teaching, telling a story or describing what
    already happened does not count. Reply with JSON only: {"plans":true} or {"plans":false}.

    Voice note:
    ""${'"'}
    $text
    ""${'"'}
""".trimIndent()

/**
 * [lines] are numbered with their index in the whole transcript, not in this chunk, so the
 * model's `line` answer maps straight back to a segment.
 *
 * [now] is the recording's own start time, not the device clock at prompt-build time — the two
 * can differ if analysis runs late (queued, retried). Without a current time at all, a relative
 * duration ("in 20 minutes") has nothing to add to, so the model guesses; it was seen inventing
 * `02:20` for a note recorded at 11am.
 */
fun buildTasksPrompt(lines: List<IndexedValue<String>>, now: LocalDateTime): String = """
    Today is ${promptDate.format(now)}, the current time is ${promptTime.format(now)}. List the
    tasks in this voice note: things the speaker says they themselves will do or want to be
    reminded of, one per stated intention. Steps the speaker explains or teaches to others
    ("click download", "go to Applications") are not tasks; neither is anything they already
    did. List each task once. Reply with JSON only:

    {"items":[{"text":"...","due":"ISO date, ISO datetime, or null","due_relative_minutes":123 or null,"line":0}]}

    text: the action itself, imperative, no time attached, in the language the speaker used.
    due: if an explicit clock time or date was stated ("at 10am", "tomorrow at 3", "tomorrow",
    "on Friday") resolve it — a clock time to a full ISO8601 timestamp using today's date above,
    a date alone to a bare ISO date; for anything else use null. due_relative_minutes: if the
    speaker instead stated a duration from now ("in 20 minutes" -> 20, "in an hour" -> 60), the
    number of minutes from now — and leave due null in that case, don't resolve it yourself. If
    no date, time, or duration was stated at all, leave both null. line: the number of the line
    the task was said on. If there are no tasks, reply {"items":[]}. No commentary.

    Example. Today is 2026-01-01, the current time is 08:00.
    [0] Remind me to call the dentist tomorrow at 9am.
    [1] Also water the plants in 20 minutes.
    [2] Oh and I need to buy milk.
    {"items":[{"text":"Call the dentist","due":"2026-01-02T09:00:00","due_relative_minutes":null,"line":0},{"text":"Water the plants","due":null,"due_relative_minutes":20,"line":1},{"text":"Buy milk","due":null,"due_relative_minutes":null,"line":2}]}

    Example. Today is 2026-01-01, the current time is 08:00.
    [0] I think I should start a podcast, I'm thinking about names.
    [1] One could be Time to Be, another Let's Go Together.
    {"items":[]}

    Voice note:
    ${lines.joinToString("\n") { "[${it.index}] ${it.value}" }}
""".trimIndent()

@Serializable
private data class RawSummary(
    val title: String = "",
    val summary: String = "",
)

@Serializable
private data class RawIntent(
    val plans: Boolean? = null,
)

@Serializable
private data class RawTasks(
    val items: List<RawItem>? = null,
)

@Serializable
private data class RawItem(
    val text: String = "",
    @SerialName("due") val due: String? = null,
    @SerialName("due_relative_minutes") val dueRelativeMinutes: Int? = null,
    val line: Int? = null,
)

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/** Null when the reply holds no JSON, or JSON with neither a title nor a summary. */
fun parseSummary(raw: String): NoteSummary? {
    val parsed = decodeOutermost<RawSummary>(raw) ?: return null
    val summary = NoteSummary(
        title = parsed.title.trim().ifBlank { null },
        summary = parsed.summary.trim().ifBlank { null },
    )
    return summary.takeIf { it.title != null || it.summary != null }
}

/** Null when the reply holds no `plans` answer. */
fun parseIntent(raw: String): Boolean? = decodeOutermost<RawIntent>(raw)?.plans

/**
 * Null when the reply holds no `items` array; an empty one is a real answer. [lineOffsetMs]
 * turns the model's line number into a moment in the recording, null for a number it never saw.
 */
fun parseTasks(
    raw: String,
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zone),
    recordedAt: LocalDateTime = LocalDateTime.now(zone),
    lineOffsetMs: (Int) -> Long? = { null },
): List<NewTodo>? {
    val items = decodeOutermost<RawTasks>(raw)?.items ?: return null
    return items
        .filter { it.text.isNotBlank() }
        .map { item ->
            val due = resolveDue(item.due, item.dueRelativeMinutes, zone, today, recordedAt)
            NewTodo(item.text.trim(), due.at, due.hasTime, due.hasDate, item.line?.let(lineOffsetMs))
        }
}

/**
 * Reads the outermost braces rather than the whole string: a small model wraps its answer in
 * prose or a code fence often enough that both are expected, not exceptional.
 */
private inline fun <reified T> decodeOutermost(raw: String): T? {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start == -1 || end <= start) return null

    val candidate = raw.substring(start, end + 1)
    return decode<T>(candidate) ?: decode<T>(repairMissingBrace(candidate))
}

private inline fun <reified T> decode(candidate: String): T? = try {
    json.decodeFromString<T>(candidate)
} catch (e: Exception) {
    null
}

/**
 * The on-device model occasionally writes `]` where it means to close an item object, e.g.
 * `"due":"...T10:00:00"]]}` instead of `"...":"..."}]}` — one `]` too many, one `}` too few.
 * Only applied as a fallback after a first decode fails, and only when the brace count is short
 * by exactly one, so a well-formed-but-unusual response is never rewritten.
 */
private fun repairMissingBrace(candidate: String): String {
    val missing = candidate.count { it == '{' } - candidate.count { it == '}' }
    if (missing != 1) return candidate

    val index = candidate.indices.firstOrNull { i -> candidate[i] == ']' && candidate.getOrNull(i - 1) != '}' }
        ?: return candidate

    return candidate.substring(0, index) + "}" + candidate.substring(index + 1)
}

data class Due(val at: Long, val hasTime: Boolean, val hasDate: Boolean)

/**
 * `hasTime`/`hasDate` are signalled purely by which branch resolved: a stated duration or an
 * exact timestamp both resolve to a real clock time, a bare date resolves to that day's start
 * with no time, and anything else — an unresolved relative phrase, a malformed stamp, or
 * nothing at all — means no date was ever stated, so `hasDate` is false and `at` is a
 * placeholder (today's start of day) that's never read once `hasDate` is false.
 *
 * [recordedAt] anchors [dueRelativeMinutes] arithmetic (the transcript's own recording moment,
 * computed in Kotlin rather than trusted to the model's arithmetic) — deliberately distinct
 * from [today], which anchors the "nothing stated" fallback against real wall-clock today so
 * re-analysing an old transcript doesn't resolve into the past.
 */
internal fun resolveDue(
    due: String?,
    dueRelativeMinutes: Int?,
    zone: ZoneId,
    today: LocalDate,
    recordedAt: LocalDateTime,
): Due {
    if (dueRelativeMinutes != null) {
        val at = recordedAt.plusMinutes(dueRelativeMinutes.toLong()).atZone(zone).toInstant().toEpochMilli()
        return Due(at, hasTime = true, hasDate = true)
    }

    val value = due?.trim()?.removeSuffix("Z")
    if (!value.isNullOrBlank() && !value.equals("null", ignoreCase = true)) {
        try {
            return Due(LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli(), hasTime = true, hasDate = true)
        } catch (e: Exception) {
            try {
                return Due(LocalDate.parse(value).atStartOfDay(zone).toInstant().toEpochMilli(), hasTime = false, hasDate = true)
            } catch (e: Exception) {
                // Falls through to "nothing stated" below.
            }
        }
    }
    return Due(today.atStartOfDay(zone).toInstant().toEpochMilli(), hasTime = false, hasDate = false)
}
