package dev.andrej.echo.ai

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
    val tasks: List<String>,
    /** Text paired with a resolved instant, or null when no usable time was given. */
    val reminders: List<Pair<String, Long?>>,
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
        Reply with JSON only. Always include all four keys below, using an empty array when
        there is nothing for it — never drop a key.

        {"title":"...","summary":"...","tasks":["..."],"reminders":[{"text":"...","due":"ISO8601 or null"}]}

        title: max 6 words. summary: max 2 sentences. tasks: things the speaker intends to do,
        imperative, no time attached. reminders: things with a stated or implied time — "remind
        me to X", "call Y tomorrow at Z" — text plus due resolved to a real ISO8601 timestamp
        using today's date above, or null if no usable time was given. No commentary.

        Example. Today is 2026-01-01.
        Voice note: "Remind me to call the dentist tomorrow at 9am. Also need to buy milk."
        {"title":"Call dentist, buy milk","summary":"Reminder to call the dentist tomorrow morning; also need milk.","tasks":["Buy milk"],"reminders":[{"text":"Call the dentist","due":"2026-01-02T09:00:00"}]}

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
    val tasks: List<String> = emptyList(),
    val reminders: List<RawReminder> = emptyList(),
)

@Serializable
private data class RawReminder(
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
fun parseAnalysis(raw: String, zone: ZoneId = ZoneId.systemDefault()): Analysis? {
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
        tasks = parsed.tasks.map { it.trim() }.filter { it.isNotBlank() },
        reminders = parsed.reminders
            .map { it.text.trim() to resolveDue(it.due, zone) }
            .filter { (text, _) -> text.isNotBlank() },
    )
}

/**
 * Only accepts what the model was asked for. Anything else — a relative phrase it failed to
 * resolve, a malformed stamp — becomes a reminder with no time rather than a wrong one.
 */
internal fun resolveDue(due: String?, zone: ZoneId): Long? {
    val value = due?.trim()?.removeSuffix("Z") ?: return null
    if (value.isBlank() || value.equals("null", ignoreCase = true)) return null

    return try {
        LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
    } catch (e: Exception) {
        try {
            LocalDate.parse(value).atStartOfDay(zone).toInstant().toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }
}
