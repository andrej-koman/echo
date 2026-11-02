package dev.andrej.echo

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import dev.andrej.echo.notify.RecordingNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Exists only to satisfy Android's foreground-service requirement for background mic access —
 * recording itself is owned by [RecordingController], reached the same way as everywhere else
 * ([EchoApplication.container]) so a session started here is the same one [ui.record.RecordViewModel]
 * sees if the app is opened mid-recording.
 */
class RecordingService : Service() {

    private val container by lazy { (application as EchoApplication).container }
    private val controller get() = container.recordingController
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val notification = container.recordingNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    startForeground(RecordingNotifier.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                } else {
                    startForeground(RecordingNotifier.NOTIFICATION_ID, notification)
                }
                controller.startRecording()
            }
            ACTION_PAUSE -> controller.pause()
            ACTION_RESUME -> controller.resume()
            ACTION_STOP -> controller.stopRecording()
        }
        observeState()
        return START_NOT_STICKY
    }

    /** Ends the foreground session as soon as recording stops — the save/transcribe work it kicked
     *  off runs on the app-scoped [AppContainer.appScope], which outlives this Service. */
    private fun observeState() {
        if (job?.isActive == true) return
        job = CoroutineScope(Dispatchers.Default).launch {
            controller.state.collect { state ->
                if (!state.isRecording) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "dev.andrej.echo.action.START_RECORDING"
        const val ACTION_PAUSE = "dev.andrej.echo.action.PAUSE_RECORDING"
        const val ACTION_RESUME = "dev.andrej.echo.action.RESUME_RECORDING"
        const val ACTION_STOP = "dev.andrej.echo.action.STOP_RECORDING"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, RecordingService::class.java).setAction(ACTION_START))
        }
    }
}
