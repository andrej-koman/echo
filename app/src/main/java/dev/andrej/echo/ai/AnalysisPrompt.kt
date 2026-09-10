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

/**
 * Words, not tokens: Gemini Nano caps input around 4000 tokens and there is no tokenizer on
 * this side of the interface. Long recordings are truncated until chunking exists.
 */
const val MAX_PROMPT_WORDS = 3000

private val promptDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val promptTime = DateTimeFormatter.ofPattern("HH:mm")

/**
 * [now] is the recording's own start time, not the device clock at prompt-build time — the two
 * can differ if analysis runs late (queued, retried). Without a current time at all, a relative
 * duration ("in 20 minutes") has nothing to add to, so the model guesses; it was seen inventing
 * `02:20` for a note recorded at 11am. The date and the time are both threaded through from the
 * same instant so they can never disagree with each other.
 */
fun buildAnalysisPrompt(text: String, now: LocalDateTime): String {
    val words = text.trim().split(Regex("\\s+"))
    val body = if (words.size <= MAX_PROMPT_WORDS) text.trim() else words.take(MAX_PROMPT_WORDS).joinToString(" ")

    return """
        Today is ${promptDate.format(now)}, the current time is ${promptTime.format(now)}. Extract
        structured data from this voice note. Reply with JSON only. Always include all three keys
        below, using an empty array when there is nothing for it — never drop a key.

        {"title":"...","summary":"...","items":[{"text":"...","due":"ISO date, ISO datetime, or null","due_relative_minutes":123 or null}]}

        title: max 6 words.

        summary: rewrite what was said into a clean, readable note. Keep everything the speaker
        said, do not compress it into a short blurb. The source is a speech-to-text transcript, so
        clean up its artifacts: fix capitalization the recognizer got wrong mid-sentence (a word
        capitalized only because it followed a fragment break), but leave real proper nouns,
        acronyms, and the start of each sentence capitalized. Add punctuation — periods at the end
        of sentences, commas where a reader needs the pause. If the speaker runs through a list of
        options or items, write a short lead sentence followed by a "- " bulleted list instead of
        leaving it as run-on prose. Otherwise use short paragraphs where that helps.

        items: things the speaker intends to do or be reminded of, one per stated action. text:
        the action itself, imperative, no time attached. due: if an explicit clock time or date
        was stated ("at 10am", "tomorrow at 3", "tomorrow", "on Friday") resolve it — a clock time
        to a full ISO8601 timestamp using today's date above, a date alone to a bare ISO date; for
        anything else use null. due_relative_minutes: if the speaker instead stated a duration
        from now ("in 20 minutes" -> 20, "in an hour" -> 60), the number of minutes from now — and
        leave due null in that case, don't resolve it yourself. If no date, time, or duration was
        stated at all, leave both due and due_relative_minutes null. No commentary.

        Example. Today is 2026-01-01, the current time is 08:00.
        Voice note: "Remind me to call the dentist tomorrow at 9am. Also water the plants in 20
        minutes. Also need to buy milk."
        {"title":"Call dentist, water plants","summary":"Call the dentist tomorrow morning to confirm the appointment. Also water the plants soon, and remember to buy milk on the way home.","items":[{"text":"Call the dentist","due":"2026-01-02T09:00:00","due_relative_minutes":null},{"text":"Water the plants","due":null,"due_relative_minutes":20},{"text":"Buy milk","due":null,"due_relative_minutes":null}]}

        Example. Today is 2026-01-01, the current time is 08:00.
        Voice note: "I think I should start a podcast I'm thinking about three names one name
        could be Time to be let's go together the fourth name could be I don't know"
        {"title":"Podcast name ideas","summary":"I think I should start a podcast. Three name ideas:\n- Time to Be\n- Let's Go Together\n- (undecided)","items":[]}

        Voice note:
        ""${'"'}
        $body
        ""${'"'}
    """.trimIndent()
}

@Serializable
private data class RawAnalysis(
    val title: String = "",
    val summary: String = "",
    val items: List<RawItem> = emptyList(),
)

@Serializable
private data class RawItem(
    val text: String = "",
    @SerialName("due") val due: String? = null,
    @SerialName("due_relative_minutes") val dueRelativeMinutes: Int? = null,
)

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/**
 * Reads the outermost braces rather than the whole string: a 1B model wraps its answer in prose
 * or a code fence often enough that both are expected, not exceptional. Null only when nothing
 * JSON-shaped was there at all.
 */
fun parseAnalysis(
    raw: String,
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zone),
    recordedAt: LocalDateTime = LocalDateTime.now(zone),
): Analysis? {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start == -1 || end <= start) return null

    val candidate = raw.substring(start, end + 1)
    val parsed = decode(candidate) ?: decode(repairMissingBrace(candidate)) ?: return null

    return Analysis(
        title = parsed.title.trim().ifBlank { null },
        summary = parsed.summary.trim().ifBlank { null },
        items = parsed.items
            .map { it.text.trim() to resolveDue(it.due, it.dueRelativeMinutes, zone, today, recordedAt) }
            .filter { (text, _) -> text.isNotBlank() }
            .map { (text, due) -> NewTodo(text, due.at, due.hasTime, due.hasDate) },
    )
}

private fun decode(candidate: String): RawAnalysis? = try {
    json.decodeFromString<RawAnalysis>(candidate)
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
