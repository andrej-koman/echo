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

    Use only information explicitly stated in the voice note below for both title and summary.
    Do not invent or assume any topic, event, person, object, or action. The example shows the
    format and style only: never use its details unless the voice note itself states them.

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

    Use only information explicitly stated in the parts below for both title and summary.
    Do not invent or assume any topic, event, person, object, or action, or add missing details.

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
    Decide whether this voice note contains the speaker's own intended actions or obligations.
    "I have to", "I need to", "I will", appointments, errands, and requests for reminders count
    as plans, even if the speaker never says the word "plan". If at least one such action is
    present, answer true. Instructions teaching someone else, past events, and general discussion
    without a personal action are false. Reply with JSON only: {"plans":true} or {"plans":false}.

    Examples:
    "Tomorrow I have to visit the dentist at 9 and pick up groceries at 11." -> {"plans":true}
    "I need to call Alex." -> {"plans":true}
    "Remind me to water the plants." -> {"plans":true}
    "To install the game, download Steam and click Install." -> {"plans":false}
    "Yesterday I went shopping." -> {"plans":false}
    "I'm thinking about possible names for a podcast." -> {"plans":false}

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
    did. Extract concrete actions only: vague filler such as "some stuff to do" is not a task.
    List each task once. A shared date such as "tomorrow" applies to all the following actions
    in the same plan unless the speaker states a different date. Reply with JSON only:

    {"items":[{"text":"...","source":"exact words from the note","due":"ISO date, ISO datetime, or null","due_relative_minutes":123 or null,"line":0}]}

    text: the action itself, imperative, no time attached, in the language the speaker used.
    It names what to do, never only when: "Tomorrow at 10 a.m." is a due, not a text. For an
    appointment or a reminder, the text is the thing to do or attend ("Go to the vet."), not
    the word "reminder". Use sentence case: start with a capital letter, preserve proper names,
    and use correct punctuation including a final period. Example: "Go to the barber shop."
    due: if an explicit clock time or date was stated ("at 10am", "tomorrow at 3", "tomorrow",
    "on Friday") resolve it — use the stated or shared date, using today ONLY if no date was
    stated. Tomorrow means ${promptDate.format(now.plusDays(1))}. A clock time becomes a full
    ISO8601 timestamp; a date alone or "all day" becomes a bare ISO date, NEVER midnight.
    12 a.m. is 00:00; 12 p.m. is 12:00; 6 p.m. is 18:00. "Around" still names a time.
    source: copy the task's complete source sentence, including its date/time, verbatim from
    the note. It may span numbered lines. Do not paraphrase it. line: the line containing the
    action words, not an earlier introduction or date. due_relative_minutes: if the
    speaker instead stated a duration from now ("in 20 minutes" -> 20, "in an hour" -> 60), the
    number of minutes from now — and leave due null in that case, don't resolve it yourself. If
    no date, time, or duration was stated at all, leave both null. line: the number of the line
    the task was said on. If there are no tasks, reply {"items":[]}. No commentary.

    Example. Today is 2026-01-01, the current time is 08:00.
    [0] Remind me to call the dentist tomorrow at 9am.
    [1] Also water the plants in 20 minutes.
    [2] Oh and I need to buy milk.
    {"items":[{"text":"Call the dentist.","source":"Remind me to call the dentist tomorrow at 9am.","due":"2026-01-02T09:00:00","due_relative_minutes":null,"line":0},{"text":"Water the plants.","source":"Also water the plants in 20 minutes.","due":null,"due_relative_minutes":20,"line":1},{"text":"Buy milk.","source":"Oh and I need to buy milk.","due":null,"due_relative_minutes":null,"line":2}]}

    Example. Today is 2026-03-10, the current time is 19:30.
    [4] So tomorrow I have a vet appointment at 9 a.m. and
    [5] also I want a reminder for tomorrow at 5 p.m. that I have to pick up the car.
    {"items":[{"text":"Go to the vet appointment.","source":"So tomorrow I have a vet appointment at 9 a.m.","due":"2026-03-11T09:00:00","due_relative_minutes":null,"line":4},{"text":"Pick up the car.","source":"also I want a reminder for tomorrow at 5 p.m. that I have to pick up the car.","due":"2026-03-11T17:00:00","due_relative_minutes":null,"line":5}]}

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
    val source: String? = null,
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
            NewTodo(item.text.trim(), due.at, due.hasTime, due.hasDate, item.line?.let(lineOffsetMs), item.source)
        }
}

/**
 * True when task text names only a date or time — the 1.7B model was seen writing
 * "Tomorrow at 10 a.m." as the text and leaving the action out.
 */
fun isScheduleOnly(text: String): Boolean = text.lowercase()
    .replace(Regex("\\b[ap]\\.?m\\.?(?!\\w)"), " ")
    .replace(scheduleWords, " ")
    .none { it.isLetter() }

private val scheduleWords = Regex(
    "\\b(?:at|on|by|in|for|the|this|next|around|about|until|from|to|and|day after|tomorrow|today|tonight|" +
        "morning|afternoon|evening|night|noon|midnight|o'clock|all|day|week|weekend|month|" +
        "monday|tuesday|wednesday|thursday|friday|saturday|sunday|" +
        "minutes?|hours?|an?|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|half|quarter|past)\\b|\\d",
)

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
