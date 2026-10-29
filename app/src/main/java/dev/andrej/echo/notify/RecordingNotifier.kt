package dev.andrej.echo.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dev.andrej.echo.R
import dev.andrej.echo.RecordingService
import dev.andrej.echo.ui.record.RecordUiState

/**
 * Always posted (idle "tap to record", or ongoing "listening" while [RecordingService] is
 * running) so recording can start/stop from the tray without opening the app.
 */
class RecordingNotifier(private val context: Context) {

    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun update(state: RecordUiState) {
        ensureChannel()
        notificationManager.notify(NOTIFICATION_ID, if (state.isRecording) listening() else idle())
    }

    fun postIdle() {
        ensureChannel()
        notificationManager.notify(NOTIFICATION_ID, idle())
    }

    fun listeningNotification(): Notification {
        ensureChannel()
        return listening()
    }

    private fun idle(): Notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_echo)
        .setContentTitle("Echo")
        .setContentText("Tap to record")
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setContentIntent(recordActionPendingIntent())
        .addAction(R.drawable.ic_mic, "Record", recordActionPendingIntent())
        .build()

    private fun listening(): Notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_echo)
        .setContentTitle("Echo is listening")
        .setContentText("Tap Stop to finish")
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .addAction(R.drawable.ic_x, "Stop", stopActionPendingIntent())
        .build()

    private fun recordActionPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        1,
        Intent(context, RecordingActionReceiver::class.java).setAction(RecordingActionReceiver.ACTION_RECORD),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun stopActionPendingIntent(): PendingIntent = PendingIntent.getService(
        context,
        2,
        Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_STOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ensureChannel() = notificationManager.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null)
        },
    )

    companion object {
        const val NOTIFICATION_ID = 20260103
        private const val CHANNEL_ID = "recording"
    }
}
