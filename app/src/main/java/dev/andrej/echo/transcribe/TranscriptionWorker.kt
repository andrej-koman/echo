package dev.andrej.echo.transcribe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dev.andrej.echo.EchoApplication
import dev.andrej.echo.R
import dev.andrej.echo.transcribe.Transcriptions.Companion.toData
import kotlinx.coroutines.CancellationException

class TranscriptionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val notificationId = id.hashCode()
    private var lastPercent = -1

    override suspend fun doWork(): Result {
        val transcriptId = inputData.getString(KEY_ID) ?: return Result.failure()
        val transcriptions = (applicationContext as EchoApplication).container.transcriptions

        try {
            setForeground(foregroundInfo(TranscriptionStatus.Queued))
        } catch (e: IllegalStateException) {
            // Started from the background (a retry, or after a reboot): Android refuses the
            // foreground service, and the job runs under WorkManager's normal limits instead.
            Log.w(TAG, "no foreground service: ${e.message}")
        }

        return try {
            transcriptions.run(transcriptId, ::report)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "transcription of $transcriptId failed (attempt $runAttemptCount)", e)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    private fun report(status: TranscriptionStatus) {
        val percent = status.percent()
        if (percent == lastPercent) return
        lastPercent = percent
        setProgressAsync(status.toData())
        notificationManager.notify(notificationId, notification(status))
    }

    private fun foregroundInfo(status: TranscriptionStatus): ForegroundInfo {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        return ForegroundInfo(notificationId, notification(status), type)
    }

    private fun notification(status: TranscriptionStatus): Notification {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Transcription", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
            },
        )
        val percent = status.percent()
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_echo)
            .setContentTitle(
                if (status is TranscriptionStatus.DownloadingModel) "Getting the speech model" else "Transcribing",
            )
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .setSubText(if (percent < 0) null else "$percent%")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    private fun TranscriptionStatus.percent(): Int = when (this) {
        is TranscriptionStatus.DownloadingModel -> (fraction * 100).toInt()
        is TranscriptionStatus.Transcribing -> (fraction * 100).toInt()
        else -> -1
    }

    companion object {
        const val KEY_ID = "transcript_id"
        private const val TAG = "TranscriptionWorker"
        private const val CHANNEL_ID = "transcription"
        private const val MAX_RETRIES = 3
    }
}
