package dev.andrej.echo.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dev.andrej.echo.R
import dev.andrej.echo.ai.ModelState

/** Silent progress notification for [dev.andrej.echo.ai.ModelStore] downloads — never a sound, never a "finished" post. */
class ModelDownloadNotifier(private val context: Context) {

    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun update(modelId: String?, modelTitle: String?, state: ModelState?) {
        val downloading = state as? ModelState.Downloading
        if (downloading == null || modelId == null || modelTitle == null) {
            notificationManager.cancel(NOTIFICATION_ID)
            return
        }
        post(modelId, modelTitle, downloading)
    }

    private fun post(modelId: String, modelTitle: String, state: ModelState.Downloading) {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Model download", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
            },
        )

        val percent = (state.fraction * 100).toInt()
        val indeterminate = state.totalBytes <= 0

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_echo)
            .setContentTitle("Downloading $modelTitle")
            .setContentText("Once it's down, everything stays on your phone.")
            .setProgress(100, percent, indeterminate)
            .setSubText(if (indeterminate) null else "$percent%")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(
                R.drawable.ic_pause,
                "Pause",
                actionPendingIntent(ModelDownloadActionReceiver.ACTION_PAUSE, 1, modelId),
            )
            .addAction(
                R.drawable.ic_x,
                "Cancel",
                actionPendingIntent(ModelDownloadActionReceiver.ACTION_CANCEL, 2, modelId),
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun actionPendingIntent(action: String, requestCode: Int, modelId: String): PendingIntent {
        val intent = Intent(context, ModelDownloadActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_MODEL_ID, modelId)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_MODEL_ID = "model_id"
        private const val CHANNEL_ID = "model_download"
        private const val NOTIFICATION_ID = 20260102
    }
}
