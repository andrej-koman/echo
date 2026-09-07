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

        {"title":"...","summary":"...","items":[{"text":"...","due":"ISO date, ISO datetime, or null"}]}

        title: max 6 words. summary: max 2 sentences. items: things the speaker intends to do or
        be reminded of, one per stated action. text: the action itself, imperative, no time
        attached. due: if a clock time was stated ("at 10am", "tomorrow at 3") or a duration from
        now ("in 20 minutes", "in an hour") resolve to a full ISO8601 timestamp using today's date
        and current time above; if only a date was stated ("tomorrow", "on Friday") resolve to a
        bare ISO date; if no date or time was stated at all, use null. No commentary.

        Example. Today is 2026-01-01, the current time is 08:00.
        Voice note: "Remind me to call the dentist tomorrow at 9am. Also water the plants in 20
        minutes. Also need to buy milk."
        {"title":"Call dentist, water plants","summary":"Reminder to call the dentist tomorrow morning; also water the plants soon and buy milk.","items":[{"text":"Call the dentist","due":"2026-01-02T09:00:00"},{"text":"Water the plants","due":"2026-01-01T08:20:00"},{"text":"Buy milk","due":null}]}

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
fun parseAnalysis(raw: String, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): Analysis? {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start == -1 || end <= start) return null

    val candidate = raw.substring(start, end + 1)
    val parsed = decode(candidate) ?: decode(repairMissingBrace(candidate)) ?: return null

    return Analysis(
        title = parsed.title.trim().ifBlank { null },
        summary = parsed.summary.trim().ifBlank { null },
        items = parsed.items
            .map { it.text.trim() to resolveDue(it.due, zone, today) }
            .filter { (text, _) -> text.isNotBlank() }
            .map { (text, due) -> NewTodo(text, due.at, due.hasTime) },
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

data class Due(val at: Long, val hasTime: Boolean)

/**
 * `hasTime` is signalled purely by which parse branch succeeded: an exact timestamp resolves
 * with a clock time, a bare date resolves to that day's start with none, and anything else —
 * an unresolved relative phrase, a malformed stamp, or nothing at all — defaults to today's
 * start of day, itself timeless.
 */
internal fun resolveDue(due: String?, zone: ZoneId, today: LocalDate): Due {
    val value = due?.trim()?.removeSuffix("Z")
    if (!value.isNullOrBlank() && !value.equals("null", ignoreCase = true)) {
        try {
            return Due(LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli(), hasTime = true)
        } catch (e: Exception) {
            try {
                return Due(LocalDate.parse(value).atStartOfDay(zone).toInstant().toEpochMilli(), hasTime = false)
            } catch (e: Exception) {
                // Falls through to today's start of day below.
            }
        }
    }
    return Due(today.atStartOfDay(zone).toInstant().toEpochMilli(), hasTime = false)
}
