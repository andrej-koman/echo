package dev.andrej.echo.notify

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class TaskReminderTime(val at: Long, val date: String? = null)

fun taskReminderTime(dueAt: Long, hasTime: Boolean, hasDate: Boolean, now: Long, zone: ZoneId): TaskReminderTime {
    if (!hasDate) return TaskReminderTime(now + 3_600_000)
    if (hasTime) return TaskReminderTime(maxOf(now, dueAt))
    val date = Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDate().toString()
    return TaskReminderTime(dateReminderTime(date, now, zone), date)
}

fun dateReminderTime(date: String, now: Long, zone: ZoneId): Long =
    maxOf(now, LocalDate.parse(date).atTime(9, 0).atZone(zone).toInstant().toEpochMilli())
