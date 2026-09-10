package dev.andrej.echo.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dev.andrej.echo.AppContainer
import dev.andrej.echo.EchoApplication
import dev.andrej.echo.MainActivity
import dev.andrej.echo.R
import dev.andrej.echo.ui.formatTime
import dev.andrej.echo.ui.tasks.groupByDue
import dev.andrej.echo.ui.tasks.isOverdue
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Fires once daily at [dev.andrej.echo.data.SettingsStore.reminderMorningHour]; suppressed on a clear day. */
class DailyBriefReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val container = (context.applicationContext as EchoApplication).container
        CoroutineScope(Dispatchers.Default).launch {
            try {
                postIfNeeded(context, container)
            } finally {
                container.rescheduleDailyBrief()
                pending.finish()
            }
        }
    }

    private suspend fun postIfNeeded(context: Context, container: AppContainer) {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val items = container.derived.items.first()
        val today = groupByDue(items, now, zone).firstOrNull { it.label == "Today" }?.items
        if (today.isNullOrEmpty()) return

        val timed = today.filter { it.hasTime }.sortedBy { it.dueAt }
        val untimed = today.filterNot { it.hasTime }
        val first = timed.firstOrNull()

        val title = buildString {
            append(if (today.size == 1) "One thing today" else "${today.size} things today")
            if (first != null) append(", first one at ${formatTime(first.dueAt)}")
        }

        val style = NotificationCompat.InboxStyle()
        timed.take(2).forEach { item ->
            val overdueSuffix = if (isOverdue(item, now, zone)) " · overdue" else ""
            style.addLine("${formatTime(item.dueAt)}  ${item.text}$overdueSuffix")
        }
        if (untimed.isNotEmpty()) {
            style.addLine("${untimed.size} more with no time set")
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Daily brief", NotificationManager.IMPORTANCE_DEFAULT),
        )

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_echo)
            .setContentTitle(title)
            .setStyle(style)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "daily_brief"
        private const val NOTIFICATION_ID = 20260101
    }
}
