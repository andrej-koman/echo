package dev.andrej.echo.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import dev.andrej.echo.MainActivity
import dev.andrej.echo.R

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

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT),
        )

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("echo://item/$itemId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_TRANSCRIPT_ID, transcriptId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(text)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(itemId.hashCode(), notification)
    }

    companion object {
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_TRANSCRIPT_ID = "transcript_id"
        const val EXTRA_TEXT = "text"
        private const val CHANNEL_ID = "reminders"
    }
}
