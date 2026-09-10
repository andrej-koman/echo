package dev.andrej.echo.notify

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.andrej.echo.EchoApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Done/Snooze buttons on the task reminder notification ([ReminderAlarmReceiver]). */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return
        val action = intent.action ?: return

        val pending = goAsync()
        val container = (context.applicationContext as EchoApplication).container
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (action) {
                    ACTION_DONE -> container.derived.setDone(itemId, true)
                    ACTION_SNOOZE -> container.snoozeReminder(itemId)
                }
                context.getSystemService(NotificationManager::class.java).cancel(itemId.hashCode())
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "dev.andrej.echo.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "dev.andrej.echo.action.REMINDER_SNOOZE"
        const val EXTRA_ITEM_ID = "item_id"
    }
}
