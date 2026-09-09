package dev.andrej.echo.data

import java.time.Instant
import java.time.ZoneId

interface NotificationScheduler {
    fun schedule(item: TodoItem)
    fun cancel(itemId: String)
}

/** Exact-time items warn [leadMinutes] ahead; date-only items get a nudge at [morningHour] on the day. */
fun fireTimeFor(
    item: TodoItem,
    zone: ZoneId = ZoneId.systemDefault(),
    leadMinutes: Int = 15,
    morningHour: Int = 9,
): Long =
    if (item.hasTime) {
        item.dueAt - leadMinutes * 60 * 1000L
    } else {
        Instant.ofEpochMilli(item.dueAt).atZone(zone).toLocalDate()
            .atTime(morningHour, 0).atZone(zone).toInstant().toEpochMilli()
    }
