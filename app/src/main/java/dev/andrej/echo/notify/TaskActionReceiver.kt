package dev.andrej.echo.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.andrej.echo.EchoApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Done and Snooze 1h on a reminder; the reminder alarms and widgets follow the database change. */
class TaskActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(AndroidTaskReminderDelivery.EXTRA_TASK_ID) ?: return
        val container = (context.applicationContext as EchoApplication).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_DONE -> container.derived.setDone(id, true)
                    ACTION_SNOOZE -> container.derived.snooze(id)
                }
                container.taskReminders.dismiss(id)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "dev.andrej.echo.action.TASK_DONE"
        const val ACTION_SNOOZE = "dev.andrej.echo.action.TASK_SNOOZE"
    }
}
