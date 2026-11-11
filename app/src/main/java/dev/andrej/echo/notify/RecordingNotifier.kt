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

/** Idle "tap to record" (opt-in, see [dev.andrej.echo.data.SettingsStore.idleNotification]), or ongoing while [RecordingService] runs. */
class RecordingNotifier(private val context: Context) {

    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun update(state: RecordUiState, idleEnabled: Boolean) {
        ensureChannels()
        if (state.mode() == RecordingMode.Idle && !idleEnabled) {
            notificationManager.cancel(NOTIFICATION_ID)
        } else {
            notificationManager.notify(NOTIFICATION_ID, notification(state))
        }
    }

    /** The service posts this before the controller has started, so an idle state still reads as recording. */
    fun foreground(state: RecordUiState): Notification {
        ensureChannels()
        return if (state.mode() == RecordingMode.Paused) paused(state.elapsedMs) else recording(state.elapsedMs)
    }

    private fun notification(state: RecordUiState): Notification = when (state.mode()) {
        RecordingMode.Idle -> idle()
        RecordingMode.Recording -> recording(state.elapsedMs)
        RecordingMode.Paused -> paused(state.elapsedMs)
    }

    private fun idle(): Notification = NotificationCompat.Builder(context, IDLE_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_mic)
        .setColor(BRONZE)
        .setContentTitle("Tap to record")
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setShowWhen(false)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setContentIntent(recordActionPendingIntent())
        .build()

    private fun recording(elapsedMs: Long): Notification = ongoing()
        .setSmallIcon(R.drawable.ic_mic)
        .setColor(CLAY)
        .setContentTitle("Recording")
        .setContentText("Tap to go back to the recording")
        // The system ticks the chronometer; posting every second would churn the notification.
        .setUsesChronometer(true)
        .setShowWhen(true)
        .setWhen(System.currentTimeMillis() - elapsedMs)
        .addAction(R.drawable.ic_pause, "Pause", servicePendingIntent(RecordingService.ACTION_PAUSE, 3))
        .addAction(R.drawable.ic_square, "Stop and save", servicePendingIntent(RecordingService.ACTION_STOP, 2))
        .build()

    private fun paused(elapsedMs: Long): Notification = ongoing()
        .setSmallIcon(R.drawable.ic_pause)
        .setColor(BRONZE)
        .setContentTitle("Recording paused")
        .setContentText("Nothing is being recorded")
        .setSubText("Paused at ${clock(elapsedMs)}")
        .setShowWhen(false)
        .addAction(R.drawable.ic_mic, "Resume", servicePendingIntent(RecordingService.ACTION_RESUME, 3))
        .addAction(R.drawable.ic_square, "Stop and save", servicePendingIntent(RecordingService.ACTION_STOP, 2))
        .build()

    private fun ongoing() = NotificationCompat.Builder(context, CHANNEL_ID)
        .setContentIntent(openCapturePendingIntent())
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)

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

    private fun ensureChannels() {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
            },
        )
        // Separate channel: importance can't be lowered on an existing one, and the shortcut sits minimized.
        notificationManager.createNotificationChannel(
            NotificationChannel(IDLE_CHANNEL_ID, "Record shortcut", NotificationManager.IMPORTANCE_MIN).apply {
                setSound(null, null)
            },
        )
    }

    companion object {
        const val NOTIFICATION_ID = 20260103
        private const val CHANNEL_ID = "recording"
        private const val IDLE_CHANNEL_ID = "record_shortcut"
        const val BRONZE = 0xFFD4A373.toInt()
        const val CLAY = 0xFFC0603F.toInt()
    }
}

internal fun clock(ms: Long): String {
    val seconds = ms / 1000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
