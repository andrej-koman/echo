package dev.andrej.echo.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val dueTimeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
private val dueDateFormat = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())

fun formatDue(dueAt: Long, now: Long): String {
    val days = TimeUnit.MILLISECONDS.toDays(dueAt.midnight() - now.midnight())
    return when (days) {
        0L -> "Today, ${dueTimeFormat.format(Date(dueAt))}"
        1L -> "Tomorrow, ${dueTimeFormat.format(Date(dueAt))}"
        else -> dueDateFormat.format(Date(dueAt))
    }
}

fun formatTime(dueAt: Long): String = dueTimeFormat.format(Date(dueAt))

fun Long.midnight(): Long = Calendar.getInstance().apply {
    timeInMillis = this@midnight
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
