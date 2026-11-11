package dev.andrej.echo.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.andrej.echo.MainActivity
import dev.andrej.echo.R
import dev.andrej.echo.data.TodoItem

class AndroidTaskReminderDelivery(
    private val context: Context,
    private val noteTitle: (transcriptId: String) -> String? = { null },
) : TaskReminderDelivery {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    init {
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Task reminders", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    override fun notificationsAllowed(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            notifications.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE

    override fun schedule(id: String, at: Long) {
        val pending = alarmIntent(id, at)
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                return
            } catch (_: SecurityException) {
                // Access can be revoked between the permission check and the binder call.
            }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    override fun cancelAlarm(id: String) {
        val pending = PendingIntent.getBroadcast(
            context, 0, alarmBaseIntent(id), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarms.cancel(pending)
        pending.cancel()
    }

    override fun cancelNotification(id: String) { notifications.cancel(TAG_PREFIX + id, 0) }

    override fun postedTaskIds(): Set<String> = notifications.activeNotifications
        .mapNotNull { it.tag?.takeIf { tag -> tag.startsWith(TAG_PREFIX) }?.removePrefix(TAG_PREFIX) }.toSet()

    override fun post(task: TodoItem, alert: Boolean): Boolean {
        if (!notificationsAllowed()) return false
        val intent = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("echo://task/${task.id}"))
            .putExtra(MainActivity.EXTRA_OPEN_TASK, task.id)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val open = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val source = noteTitle(task.sourceTranscriptId)?.let { "From “$it”" }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bell)
            .setColor(RecordingNotifier.BRONZE)
            .setSubText("Reminder")
            .setContentTitle(task.text)
            .setContentText(source)
            .addAction(R.drawable.ic_check, "Done", actionIntent(TaskActionReceiver.ACTION_DONE, task.id))
            .addAction(R.drawable.ic_clock, "Snooze 1h", actionIntent(TaskActionReceiver.ACTION_SNOOZE, task.id))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(!alert)
            .build()
        notifications.notify(TAG_PREFIX + task.id, 0, notification)
        return notificationsAllowed()
    }

    private fun actionIntent(action: String, id: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, TaskActionReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("echo://task-action/$id"))
            .putExtra(EXTRA_TASK_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun alarmBaseIntent(id: String) = Intent(context, TaskReminderReceiver::class.java)
        .setAction(ACTION_REMIND)
        .setData(Uri.parse("echo://reminder/$id"))
        .putExtra(EXTRA_TASK_ID, id)

    private fun alarmIntent(id: String, at: Long): PendingIntent = PendingIntent.getBroadcast(
        context, 0, alarmBaseIntent(id).putExtra(EXTRA_REMINDER_AT, at),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_ID = "task_reminders"
        const val ACTION_REMIND = "dev.andrej.echo.REMIND_TASK"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_REMINDER_AT = "reminder_at"
        private const val TAG_PREFIX = "task:"
    }
}
