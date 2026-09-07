package dev.andrej.echo.data

import java.time.Instant
import java.time.ZoneId

interface NotificationScheduler {
    fun schedule(item: TodoItem)
    fun cancel(itemId: String)
}

private const val LEAD_MS = 15 * 60 * 1000L
private const val MORNING_HOUR = 9

/** Exact-time items warn 15 minutes ahead; date-only items get a 9am nudge on the day. */
fun fireTimeFor(item: TodoItem, zone: ZoneId = ZoneId.systemDefault()): Long =
    if (item.hasTime) {
        item.dueAt - LEAD_MS
    } else {
        Instant.ofEpochMilli(item.dueAt).atZone(zone).toLocalDate()
            .atTime(MORNING_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
    }
