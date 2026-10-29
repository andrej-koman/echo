package dev.andrej.echo.notify

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dev.andrej.echo.MainActivity
import dev.andrej.echo.RecordingService

/**
 * The idle notification's Record action. A Service can't prompt for a runtime permission, so this
 * branches: if RECORD_AUDIO is already granted it starts [RecordingService] directly (headless,
 * no UI); otherwise it opens [MainActivity] straight to Capture, which already has the request flow.
 */
class RecordingActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RECORD) return

        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

        if (granted) {
            context.startForegroundService(
                Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_START),
            )
        } else {
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_OPEN_CAPTURE, true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
            )
        }
    }

    companion object {
        const val ACTION_RECORD = "dev.andrej.echo.action.RECORD_FROM_NOTIFICATION"
    }
}
