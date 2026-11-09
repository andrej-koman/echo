package dev.andrej.echo.ai

import dev.andrej.echo.data.NewTodo
import dev.andrej.echo.data.TaskSource
import dev.andrej.echo.data.taskSource
import dev.andrej.echo.data.taskSentences
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Source wording overrides model arithmetic only for expressions we can resolve unambiguously. */
internal fun groundTasks(
    items: List<NewTodo>,
    lines: List<String>,
    offsets: List<Long>,
    recordedAt: LocalDateTime,
    zone: ZoneId,
): List<NewTodo> {
    val text = lines.joinToString(" ")
    val starts = lines.runningFold(0) { at, line -> at + line.length + 1 }
    return items.map { item ->
        val near = offsets.indexOf(item.sourceOffsetMs).takeIf { it >= 0 }?.let { starts[it] }
        val source = taskSource(text, item.text, item.sourceQuote, near, validateAction = true)
            ?: taskSource(text, item.text, near = near)
            ?: return@map item.copy(sourceOffsetMs = null, sourceQuote = null)
        val phrase = text.substring(source.start, source.end)
        val actionLine = lines.indices.lastOrNull { starts[it] <= source.actionStart }
        val schedule = sourceSchedule(text, source, recordedAt, zone, item)
        item.copy(
            dueAt = schedule.at,
            hasTime = schedule.hasTime,
            hasDate = schedule.hasDate,
            sourceOffsetMs = actionLine?.let { offsets.getOrNull(it) },
            sourceQuote = phrase,
        )
    }
}

private fun sourceSchedule(text: String, source: TaskSource, recorded: LocalDateTime, zone: ZoneId, item: NewTodo): Due {
    val context = taskSentences(text).firstOrNull { source.actionStart in it }
    val phrase = text.substring(context ?: (source.start until source.end)).lowercase(Locale.ROOT)
    val duration = Regex("\\bin\\s+(\\d+|an?|one)\\s+(minutes?|hours?)\\b").find(phrase)
    if (duration != null) {
        val amount = duration.groupValues[1].toLongOrNull() ?: 1L
        val minutes = if (duration.groupValues[2].startsWith("hour")) amount * 60 else amount
        return Due(recorded.plusMinutes(minutes).atZone(zone).toInstant().toEpochMilli(), true, true)
    }
    if (Regex("\\bin\\s+.{1,25}\\b(minutes?|hours?)\\b").containsMatchIn(phrase)) {
        return Due(item.dueAt, item.hasTime, item.hasDate)
    }
    val unsupportedDate = Regex("\\b(?:on\\s+(?:\\p{L}+\\s+)?\\d{1,2}|(?:next|this)\\s+(?:week|month|year)|in\\s+\\d+\\s+days?)\\b").containsMatchIn(phrase)
    val localDate = statedDate(phrase, recorded.toLocalDate())
    val date = localDate ?: if (unsupportedDate) null else sharedDate(text.substring(0, context?.first ?: source.start), recorded.toLocalDate())
    val clock = statedTime(phrase)
    val allDay = Regex("\\ball[ -]day\\b").containsMatchIn(phrase)
    if (date == null && clock == null && !allDay) return Due(item.dueAt, item.hasTime, item.hasDate)
    val day = date ?: if (unsupportedDate && item.hasDate) java.time.Instant.ofEpochMilli(item.dueAt).atZone(zone).toLocalDate() else recorded.toLocalDate()
    val unresolvedClock = Regex("\\bat\\s+(?:\\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\\b|\\b[ap]\\.?m\\.?").containsMatchIn(phrase)
    val time = if (allDay) null else clock ?: if (unresolvedClock && item.hasTime) java.time.Instant.ofEpochMilli(item.dueAt).atZone(zone).toLocalTime() else null
    return Due(day.atTime(time ?: LocalTime.MIDNIGHT).atZone(zone).toInstant().toEpochMilli(), time != null, true)
}

private fun sharedDate(text: String, today: LocalDate): LocalDate? = taskSentences(text).mapNotNull { range ->
    val phrase = text.substring(range).lowercase(Locale.ROOT)
    val past = Regex("\\b(yesterday|last|met|was|were|had|did|went|called|bought|finished|completed)\\b").containsMatchIn(phrase)
    val plan = Regex("\\b(tomorrow|today|tonight|next|plan|plans|schedule|need|will|should)\\b|\\bhave to\\b").containsMatchIn(phrase)
    val dateLeads = dateExpression.find(phrase)?.range?.first == 0
    if (!past && (plan || dateLeads)) statedDate(phrase, today) else null
}.lastOrNull()

private val dateExpression = Regex("\\b(day after tomorrow|tomorrow|today|tonight|(?:next |this |on )?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|\\d{4}-\\d{2}-\\d{2})\\b")

private fun statedDate(text: String, today: LocalDate): LocalDate? {
    val value = dateExpression.findAll(text).lastOrNull()?.value ?: return null
    return when (value) {
        "day after tomorrow" -> today.plusDays(2)
        "tomorrow" -> today.plusDays(1)
        "today", "tonight" -> today
        else -> {
            runCatching { LocalDate.parse(value) }.getOrNull() ?: run {
                val name = value.substringAfterLast(' ').uppercase(Locale.ROOT)
                val weekday = runCatching { DayOfWeek.valueOf(name) }.getOrNull() ?: return null
                today.with(if (value.startsWith("next ")) TemporalAdjusters.next(weekday) else TemporalAdjusters.nextOrSame(weekday))
            }
        }
    }
}

private fun statedTime(text: String): LocalTime? {
    if (Regex("\\bnoon\\b").containsMatchIn(text)) return LocalTime.NOON
    if (Regex("\\bmidnight\\b").containsMatchIn(text)) return LocalTime.MIDNIGHT
    val match = Regex("\\b(1[0-2]|0?[1-9])(?::([0-5]\\d))?\\s*([ap])\\.?m\\.?(?!\\w)").find(text)
    if (match != null) {
        val hour = match.groupValues[1].toInt() % 12 + if (match.groupValues[3] == "p") 12 else 0
        return LocalTime.of(hour, match.groupValues[2].ifEmpty { "0" }.toInt())
    }
    val military = Regex("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b").find(text) ?: return null
    return LocalTime.of(military.groupValues[1].toInt(), military.groupValues[2].toInt())
}
