package dev.andrej.echo.notify

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.andrej.echo.data.NotificationScheduler
import dev.andrej.echo.data.SettingsStore
import dev.andrej.echo.data.TodoItem
import dev.andrej.echo.data.fireTimeFor
import java.time.ZoneId

/**
 * PendingIntent identity is the Intent data URI, not `requestCode`: [PendingIntent] equality
 * goes through [Intent.filterEquals], which ignores extras, so `requestCode = itemId.hashCode()`
 * alone would be a 32-bit collision lottery where one item's alarm silently overwrites another's.
 */
class AlarmManagerNotificationScheduler(
    private val context: Context,
    private val settings: SettingsStore,
) : NotificationScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    override fun schedule(item: TodoItem) {
        val fireAt = fireTimeFor(
            item,
            ZoneId.systemDefault(),
            leadMinutes = settings.reminderLeadMinutes,
            morningHour = settings.reminderMorningHour,
            morningMinute = settings.reminderMorningMinute,
        )
        if (fireAt <= System.currentTimeMillis()) return

        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            data = Uri.parse("echo://item/${item.id}")
            putExtra(ReminderAlarmReceiver.EXTRA_ITEM_ID, item.id)
            putExtra(ReminderAlarmReceiver.EXTRA_TRANSCRIPT_ID, item.sourceTranscriptId)
            putExtra(ReminderAlarmReceiver.EXTRA_TEXT, item.text)
            putExtra(ReminderAlarmReceiver.EXTRA_DUE_AT, item.dueAt)
            putExtra(ReminderAlarmReceiver.EXTRA_HAS_TIME, item.hasTime)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pendingIntent)
    }

    override fun cancel(itemId: String) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            data = Uri.parse("echo://item/$itemId")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
        notificationManager.cancel(itemId.hashCode())
    }
}
