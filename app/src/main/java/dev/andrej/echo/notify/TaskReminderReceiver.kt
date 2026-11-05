package dev.andrej.echo.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.andrej.echo.EchoApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AndroidTaskReminderDelivery.ACTION_REMIND) return
        val id = intent.getStringExtra(AndroidTaskReminderDelivery.EXTRA_TASK_ID) ?: return
        if (!intent.hasExtra(AndroidTaskReminderDelivery.EXTRA_REMINDER_AT)) return
        val at = intent.getLongExtra(AndroidTaskReminderDelivery.EXTRA_REMINDER_AT, 0)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                (context.applicationContext as EchoApplication).container.taskReminders.deliver(id, at)
            } finally {
                pending.finish()
            }
        }
    }
}
