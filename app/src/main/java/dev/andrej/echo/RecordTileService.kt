package dev.andrej.echo

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import dev.andrej.echo.notify.RecordingMode
import dev.andrej.echo.notify.clock
import dev.andrej.echo.notify.mode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Record from the shade. The timer only ticks while the shade is open and listening. */
class RecordTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening: Job? = null

    private val controller get() = (application as EchoApplication).container.recordingController

    override fun onStartListening() {
        listening?.cancel()
        listening = scope.launch {
            while (true) {
                render()
                delay(1_000)
            }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        when {
            controller.state.value.mode() == RecordingMode.Recording -> startService(service(RecordingService.ACTION_STOP))
            controller.state.value.mode() == RecordingMode.Paused -> startService(service(RecordingService.ACTION_RESUME))
            !micAllowed() -> openCapture()
            else -> RecordingService.start(this)
        }
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val state = controller.state.value
        when {
            state.mode() == RecordingMode.Recording -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "Recording"
                tile.subtitle = "${clock(state.elapsedMs)} · tap to stop"
                tile.icon = Icon.createWithResource(this, R.drawable.ic_square)
            }
            state.mode() == RecordingMode.Paused -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "Paused"
                tile.subtitle = "${clock(state.elapsedMs)} · tap to resume"
                tile.icon = Icon.createWithResource(this, R.drawable.ic_pause)
            }
            !micAllowed() -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Record"
                tile.subtitle = "Mic access needed"
                tile.icon = Icon.createWithResource(this, R.drawable.ic_mic_off)
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Record"
                tile.subtitle = "Echo"
                tile.icon = Icon.createWithResource(this, R.drawable.ic_mic)
            }
        }
        tile.updateTile()
    }

    private fun service(action: String) = Intent(this, RecordingService::class.java).setAction(action)

    private fun micAllowed() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun openCapture() {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_CAPTURE, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
