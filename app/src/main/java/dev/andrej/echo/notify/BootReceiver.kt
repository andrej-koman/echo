package dev.andrej.echo.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.andrej.echo.EchoApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A reboot or an app update ([Intent.ACTION_MY_PACKAGE_REPLACED]) clears the idle "tap to record"
 * notification, and nothing else starts the process to post it again until the app is opened.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            )) return

        val pending = goAsync()
        val container = (context.applicationContext as EchoApplication).container
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                    container.repostRecordingNotification()
                }
                container.taskReminders.reconcile(refreshDates = true, force = true)
            } finally {
                pending.finish()
            }
        }
    }
}
