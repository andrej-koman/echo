package dev.andrej.echo.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.andrej.echo.data.SettingsStore
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Reschedules itself for tomorrow each time [DailyBriefReceiver] fires — there is no exact repeating alarm. */
class DailyBriefScheduler(
    private val context: Context,
    private val settings: SettingsStore,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun scheduleNext() {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        var fireAt = Instant.ofEpochMilli(now).atZone(zone)
            .withHour(settings.reminderMorningHour)
            .withMinute(settings.reminderMorningMinute)
            .withSecond(0)
            .withNano(0)
            .toInstant()
            .toEpochMilli()
        if (fireAt <= now) fireAt += TimeUnit.DAYS.toMillis(1)

        val intent = Intent(context, DailyBriefReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pendingIntent)
    }
}
