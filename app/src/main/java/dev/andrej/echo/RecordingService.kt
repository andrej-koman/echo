package dev.andrej.echo

import android.app.Service
import android.content.Intent
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
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(RecordingNotifier.NOTIFICATION_ID, container.recordingListeningNotification())
                container.recordingController.startRecording()
            }
            ACTION_STOP -> container.recordingController.stopRecording()
        }
        observeState()
        return START_NOT_STICKY
    }

    /** Ends the foreground session as soon as recording stops — the save/analyze work it kicked
     *  off runs on the app-scoped [AppContainer.appScope], which outlives this Service. */
    private fun observeState() {
        if (job?.isActive == true) return
        job = CoroutineScope(Dispatchers.Default).launch {
            container.recordingController.state.collect { state ->
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
        const val ACTION_STOP = "dev.andrej.echo.action.STOP_RECORDING"
    }
}
