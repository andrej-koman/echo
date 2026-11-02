package dev.andrej.echo.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dev.andrej.echo.MainActivity
import dev.andrej.echo.R
import dev.andrej.echo.RecordingService
import dev.andrej.echo.ui.record.RecordUiState

enum class RecordingMode { Idle, Recording, Paused }

fun RecordUiState.mode(): RecordingMode = when {
    !isRecording -> RecordingMode.Idle
    isPaused -> RecordingMode.Paused
    else -> RecordingMode.Recording
}

/**
 * Always posted (idle "tap to record", or ongoing while [RecordingService] is running) so
 * recording can start, pause and stop from the tray without opening the app.
 */
class RecordingNotifier(private val context: Context) {

    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun update(mode: RecordingMode) {
        ensureChannel()
        notificationManager.notify(NOTIFICATION_ID, notification(mode))
    }

    fun postIdle() {
        ensureChannel()
        notificationManager.notify(NOTIFICATION_ID, idle())
    }

    fun notification(mode: RecordingMode): Notification {
        ensureChannel()
        return when (mode) {
            RecordingMode.Idle -> idle()
            RecordingMode.Recording -> recording(paused = false)
            RecordingMode.Paused -> recording(paused = true)
        }
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

    private fun recording(paused: Boolean): Notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_echo)
        .setContentTitle(if (paused) "Recording paused" else "Echo is recording")
        .setContentText("Tap Stop to finish")
        .setContentIntent(openCapturePendingIntent())
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .addAction(
            if (paused) R.drawable.ic_mic else R.drawable.ic_pause,
            if (paused) "Resume" else "Pause",
            servicePendingIntent(if (paused) RecordingService.ACTION_RESUME else RecordingService.ACTION_PAUSE, 3),
        )
        .addAction(R.drawable.ic_x, "Stop", servicePendingIntent(RecordingService.ACTION_STOP, 2))
        .build()

    private fun recordActionPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        1,
        Intent(context, RecordingActionReceiver::class.java).setAction(RecordingActionReceiver.ACTION_RECORD),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        context,
        requestCode,
        Intent(context, RecordingService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openCapturePendingIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        4,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_CAPTURE, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
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
