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

fun buildAnalysisPrompt(text: String, today: LocalDate): String {
    val words = text.trim().split(Regex("\\s+"))
    val body = if (words.size <= MAX_PROMPT_WORDS) text.trim() else words.take(MAX_PROMPT_WORDS).joinToString(" ")

    return """
        Today is ${promptDate.format(today)}. Extract structured data from this voice note.
        Reply with JSON only. Always include all three keys below, using an empty array when
        there is nothing for it — never drop a key.

        {"title":"...","summary":"...","items":[{"text":"...","due":"ISO date, ISO datetime, or null"}]}

        title: max 6 words. summary: max 2 sentences. items: things the speaker intends to do or
        be reminded of, one per stated action. text: the action itself, imperative, no time
        attached. due: if a clock time was stated ("at 10am", "tomorrow at 3") resolve to a full
        ISO8601 timestamp using today's date above; if only a date was stated ("tomorrow",
        "on Friday") resolve to a bare ISO date; if no date or time was stated at all, use null.
        No commentary.

        Example. Today is 2026-01-01.
        Voice note: "Remind me to call the dentist tomorrow at 9am. Also need to buy milk."
        {"title":"Call dentist, buy milk","summary":"Reminder to call the dentist tomorrow morning; also need milk.","items":[{"text":"Call the dentist","due":"2026-01-02T09:00:00"},{"text":"Buy milk","due":null}]}

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

    val parsed = try {
        json.decodeFromString<RawAnalysis>(raw.substring(start, end + 1))
    } catch (e: Exception) {
        return null
    }

    return Analysis(
        title = parsed.title.trim().ifBlank { null },
        summary = parsed.summary.trim().ifBlank { null },
        items = parsed.items
            .map { it.text.trim() to resolveDue(it.due, zone, today) }
            .filter { (text, _) -> text.isNotBlank() }
            .map { (text, due) -> NewTodo(text, due.at, due.hasTime) },
    )
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
