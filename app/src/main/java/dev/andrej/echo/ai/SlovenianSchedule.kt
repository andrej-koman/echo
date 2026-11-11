package dev.andrej.echo.ai

import dev.andrej.echo.data.NewTodo
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

// Explicit letter lookarounds, not \b: the JVM's \b is ASCII-only, Android's ICU \b is not, and
// "č", "š", "ž" must count as letters on both.
private const val L = "(?<![\\p{L}\\p{N}])"
private const val R = "(?![\\p{L}\\p{N}])"
private fun words(pattern: String) = Regex("$L(?:$pattern)$R")

/** Locative "ob desetih" and nominative "četrt na deset" both name the hour. */
private val hourWords = mapOf(
    "enih" to 1, "eni" to 1, "ena" to 1, "dveh" to 2, "dve" to 2, "treh" to 3, "tri" to 3,
    "štirih" to 4, "štiri" to 4, "petih" to 5, "pet" to 5, "šestih" to 6, "šest" to 6,
    "sedmih" to 7, "sedem" to 7, "osmih" to 8, "osem" to 8, "devetih" to 9, "devet" to 9,
    "desetih" to 10, "deset" to 10, "enajstih" to 11, "enajst" to 11, "dvanajstih" to 12, "dvanajst" to 12,
)
private val hour = "(\\d{1,2}|" + hourWords.keys.sortedByDescending { it.length }.joinToString("|") + ")"

private val threeQuarters = words("tri\\s+četrt\\s+na\\s+$hour")
private val quarter = words("četrt\\s+na\\s+$hour")
private val half = words("pol\\s+$hour")
private val atClock = words("ob\\s+(\\d{1,2})[:.](\\d{2})")
private val atHour = words("ob\\s+$hour")
private val clock = words("([01]?\\d|2[0-3]):([0-5]\\d)")
private val noon = words("opoldne|opoldan")
private val midnight = words("opolnoči")
private val morning = words("zjutraj|dopoldne|dopoldan")
private val evening = words("popoldne|popoldan|zvečer|nocoj|drevi")
private val night = words("ponoči")
private val allDay = words("ves\\s+dan|cel\\s+dan|celi\\s+dan")

private val duration = words("čez\\s+(?:(\\d+|eno|en|pol|dve|tri|pet|deset|petnajst|dvajset|trideset)\\s+)?(minut\\p{L}*|ur\\p{L}*)")
private val durationWords = mapOf(
    "eno" to 1.0, "en" to 1.0, "pol" to 0.5, "dve" to 2.0, "tri" to 3.0, "pet" to 5.0,
    "deset" to 10.0, "petnajst" to 15.0, "dvajset" to 20.0, "trideset" to 30.0,
)

private val date = words(
    "pojutrišnjem|jutri|danes|nocoj|drevi|\\d{4}-\\d{2}-\\d{2}|" +
        "(?:(?:naslednj\\p{L}*|prihodnj\\p{L}*)\\s+)?(?:ponedeljek|torek|sred[ao]|četrtek|petek|sobot[ao]|nedelj[ao])",
)
private val weekdays = mapOf(
    "ponedeljek" to DayOfWeek.MONDAY, "torek" to DayOfWeek.TUESDAY, "sreda" to DayOfWeek.WEDNESDAY,
    "sredo" to DayOfWeek.WEDNESDAY, "četrtek" to DayOfWeek.THURSDAY, "petek" to DayOfWeek.FRIDAY,
    "sobota" to DayOfWeek.SATURDAY, "soboto" to DayOfWeek.SATURDAY, "nedelja" to DayOfWeek.SUNDAY,
    "nedeljo" to DayOfWeek.SUNDAY,
)
private val plan = words("moram|moramo|mora|bom|bomo|bo|grem|gremo|želim|hočem|rad|rada|imam|imamo|treba|naj|jutri|danes|nocoj")
private val past = words("včeraj|sem\\s+bil\\p{L}*|sem\\s+šl\\p{L}*|sem\\s+šel|smo\\s+bil\\p{L}*")

/**
 * Slovenian counterpart of the English source grounding in [groundTasks]: [phrase] is the task's
 * sentence (lowercase), [before] the note's text ahead of it, for a date such as "jutri" said
 * once for several plans. Times it cannot read keep the model's values.
 */
internal fun slovenianSchedule(phrase: String, before: String, recorded: LocalDateTime, zone: ZoneId, item: NewTodo): Due {
    duration.find(phrase)?.let { match ->
        val amount = match.groupValues[1].let { it.toDoubleOrNull() ?: durationWords[it] ?: 1.0 }
        val minutes = if (match.groupValues[2].startsWith("ur")) amount * 60 else amount
        return Due(recorded.plusMinutes(minutes.toLong()).atZone(zone).toInstant().toEpochMilli(), true, true)
    }
    val today = recorded.toLocalDate()
    val stated = statedDate(phrase, today)
    val day = stated ?: sharedDate(before, today)
    val wholeDay = allDay.containsMatchIn(phrase)
    val time = if (wholeDay) null else statedTime(phrase, item, zone)
        ?: if (item.hasTime && atHour.containsMatchIn(phrase)) Instant.ofEpochMilli(item.dueAt).atZone(zone).toLocalTime() else null
    if (day == null && time == null && !wholeDay) return Due(item.dueAt, item.hasTime, item.hasDate)
    val on = day ?: today
    return Due(on.atTime(time ?: LocalTime.MIDNIGHT).atZone(zone).toInstant().toEpochMilli(), time != null, true)
}

private fun statedDate(phrase: String, today: LocalDate): LocalDate? {
    val value = date.findAll(phrase).lastOrNull()?.value ?: return null
    return when (value) {
        "pojutrišnjem" -> today.plusDays(2)
        "jutri" -> today.plusDays(1)
        "danes", "nocoj", "drevi" -> today
        else -> runCatching { LocalDate.parse(value) }.getOrNull() ?: run {
            val weekday = weekdays[value.substringAfterLast(' ')] ?: return null
            val next = value.startsWith("naslednj") || value.startsWith("prihodnj")
            today.with(if (next) TemporalAdjusters.next(weekday) else TemporalAdjusters.nextOrSame(weekday))
        }
    }
}

private fun sharedDate(before: String, today: LocalDate): LocalDate? =
    before.split(Regex("(?<=[.!?])\\s+")).mapNotNull { sentence ->
        val lower = sentence.lowercase()
        if (!past.containsMatchIn(lower) && plan.containsMatchIn(lower)) statedDate(lower, today) else null
    }.lastOrNull()

private fun hourOf(token: String): Int? = token.toIntOrNull() ?: hourWords[token]

private fun statedTime(phrase: String, item: NewTodo, zone: ZoneId): LocalTime? {
    if (noon.containsMatchIn(phrase)) return LocalTime.NOON
    if (midnight.containsMatchIn(phrase)) return LocalTime.MIDNIGHT
    // "pol desetih" is half past nine: Slovenian counts toward the coming hour.
    val (hour, minute) = threeQuarters.find(phrase)?.let { hourOf(it.groupValues[1])?.let { h -> h - 1 to 45 } }
        ?: quarter.find(phrase)?.let { hourOf(it.groupValues[1])?.let { h -> h - 1 to 15 } }
        ?: half.find(phrase)?.let { hourOf(it.groupValues[1])?.let { h -> h - 1 to 30 } }
        ?: atClock.find(phrase)?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
        ?: atHour.find(phrase)?.let { hourOf(it.groupValues[1])?.let { h -> h to 0 } }
        ?: clock.find(phrase)?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
        ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    val resolved = when {
        hour > 12 -> hour
        evening.containsMatchIn(phrase) -> if (hour == 0) 12 else if (hour < 12) hour + 12 else hour
        night.containsMatchIn(phrase) -> if (hour in 6..11) hour + 12 else hour % 12
        morning.containsMatchIn(phrase) -> hour % 12
        // "ob šestih" alone: trust the model when it read the same hour as afternoon.
        item.hasTime -> {
            val model = Instant.ofEpochMilli(item.dueAt).atZone(zone).toLocalTime().hour
            if (model % 12 == hour % 12) model else hour
        }
        else -> hour
    }
    return LocalTime.of(resolved, minute)
}

private val scheduleOnlyWords = words(
    "ob|na|v|ta|to|ko|pa|in|do|od|okoli|približno|jutri|danes|nocoj|drevi|pojutrišnjem|zjutraj|dopoldne|dopoldan|" +
        "popoldne|popoldan|zvečer|ponoči|opoldne|opoldan|opolnoči|ura|uri|ure|ur|uro|minut\\p{L}*|pol|četrt|čez|ves|cel|dan|" +
        "ponedeljek|torek|sred[ao]|četrtek|petek|sobot[ao]|nedelj[ao]|naslednj\\p{L}*|prihodnj\\p{L}*|" +
        hourWords.keys.joinToString("|"),
)

/** Slovenian [isScheduleOnly]: the text names a when and no what. */
internal fun isSlovenianScheduleOnly(text: String): Boolean =
    text.lowercase().replace(scheduleOnlyWords, " ").replace(Regex("\\d"), " ").none { it.isLetter() }
