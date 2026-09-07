package dev.andrej.echo.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.andrej.echo.EchoApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Exact alarms do not survive a reboot, and an app update ([Intent.ACTION_MY_PACKAGE_REPLACED])
 * clears them exactly like a reboot does — which during `./gradlew installAndRun` development
 * means every alarm silently disappears without this.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pending = goAsync()
        val container = (context.applicationContext as EchoApplication).container
        CoroutineScope(Dispatchers.Default).launch {
            try {
                container.rescheduleNotifications()
            } finally {
                pending.finish()
            }
        }
    }
}
