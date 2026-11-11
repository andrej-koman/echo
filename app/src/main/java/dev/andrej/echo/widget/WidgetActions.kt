package dev.andrej.echo.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartService
import dev.andrej.echo.EchoApplication
import dev.andrej.echo.MainActivity
import dev.andrej.echo.RecordingService
import dev.andrej.echo.notify.RecordingActionReceiver
import dev.andrej.echo.notify.RecordingMode

private val TaskId = ActionParameters.Key<String>("task_id")

/**
 * The disc: idle starts straight away (the receiver opens Echo when the mic isn't granted),
 * recording stops and saves, paused resumes.
 */
internal fun recordAction(context: Context, snapshot: WidgetSnapshot): Action = when {
    snapshot.recording.mode == RecordingMode.Recording -> stopAction(context)
    snapshot.recording.mode == RecordingMode.Paused ->
        actionStartService(Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_RESUME))
    !snapshot.micAllowed -> openApp(context, "capture") { putExtra(MainActivity.EXTRA_OPEN_CAPTURE, true) }
    else -> actionSendBroadcast(
        Intent(context, RecordingActionReceiver::class.java).setAction(RecordingActionReceiver.ACTION_RECORD),
    )
}

internal fun stopAction(context: Context): Action =
    actionStartService(Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_STOP))

internal fun openNote(context: Context, id: String): Action =
    openApp(context, "note/$id") { putExtra(MainActivity.EXTRA_OPEN_NOTE, id) }

internal fun openTask(context: Context, id: String): Action =
    openApp(context, "task/$id") { putExtra(MainActivity.EXTRA_OPEN_TASK, id) }

/** There is no standalone tasks screen; the calendar's today is where they show. */
internal fun openToday(context: Context): Action =
    openApp(context, "today") { putExtra(MainActivity.EXTRA_OPEN_CALENDAR, true) }

internal fun openEcho(context: Context): Action = openApp(context, "home") { this }

internal fun tickAction(id: String): Action = actionRunCallback<TickTask>(actionParametersOf(TaskId to id))

internal fun snoozeAction(id: String): Action = actionRunCallback<SnoozeTask>(actionParametersOf(TaskId to id))

private fun openApp(context: Context, path: String, extras: Intent.() -> Intent): Action = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .setData(Uri.parse("echo://widget/$path"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .extras(),
)

internal fun Context.widgets() = (applicationContext as EchoApplication).container.widgets

class TickTask : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        parameters[TaskId]?.let { context.widgets().tick(it) }
    }
}

class SnoozeTask : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        parameters[TaskId]?.let { context.widgets().snooze(it) }
    }
}
