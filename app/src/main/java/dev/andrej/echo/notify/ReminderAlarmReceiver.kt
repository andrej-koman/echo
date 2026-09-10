package dev.andrej.echo.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import dev.andrej.echo.EchoApplication
import dev.andrej.echo.MainActivity
import dev.andrej.echo.R
import dev.andrej.echo.ui.formatDue
import dev.andrej.echo.ui.midnight
import dev.andrej.echo.ui.notes.title
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Channel creation belongs here, not in the scheduler: at alarm time the process may be fresh
 * and [dev.andrej.echo.AppContainer] never touched, but [NotificationManager.createNotificationChannel]
 * is idempotent so it costs nothing to repeat.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return
        val transcriptId = intent.getStringExtra(EXTRA_TRANSCRIPT_ID) ?: return
        val text = intent.getStringExtra(EXTRA_TEXT) ?: return
        val dueAt = intent.getLongExtra(EXTRA_DUE_AT, 0L)
        val hasTime = intent.getBooleanExtra(EXTRA_HAS_TIME, true)

        val pending = goAsync()
        val container = (context.applicationContext as EchoApplication).container
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val sourceTitle = container.repository.transcripts.first()
                    .firstOrNull { it.id == transcriptId }
                    ?.let { it.title ?: title(it.text) }
                    .orEmpty()

                post(context, itemId, text, dueAt, hasTime, sourceTitle)
            } finally {
                pending.finish()
            }
        }
    }

    private fun post(
        context: Context,
        itemId: String,
        text: String,
        dueAt: Long,
        hasTime: Boolean,
        sourceTitle: String,
    ) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT),
        )

        val now = System.currentTimeMillis()
        val dueLabel = if (hasTime) {
            formatDue(dueAt, now)
        } else {
            val days = TimeUnit.MILLISECONDS.toDays(dueAt.midnight() - now.midnight())
            when (days) {
                0L -> "Today"
                1L -> "Tomorrow"
                else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(dueAt))
            }
        }
        val contentText = if (sourceTitle.isNotBlank()) "$dueLabel · $sourceTitle" else dueLabel

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("echo://item/$itemId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_TASK_ID, itemId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_echo)
            .setContentTitle(text)
            .setContentText(contentText)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_check, "Done", actionPendingIntent(context, itemId, ReminderActionReceiver.ACTION_DONE, 1))
            .addAction(
                R.drawable.ic_clock,
                "Snooze to tonight",
                actionPendingIntent(context, itemId, ReminderActionReceiver.ACTION_SNOOZE, 2),
            )
            .build()

        notificationManager.notify(itemId.hashCode(), notification)
    }

    private fun actionPendingIntent(context: Context, itemId: String, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("echo://item/$itemId/$action")
            putExtra(ReminderActionReceiver.EXTRA_ITEM_ID, itemId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_TRANSCRIPT_ID = "transcript_id"
        const val EXTRA_TEXT = "text"
        const val EXTRA_DUE_AT = "due_at"
        const val EXTRA_HAS_TIME = "has_time"
        private const val CHANNEL_ID = "reminders"
    }
}
