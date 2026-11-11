package dev.andrej.echo.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import dev.andrej.echo.EchoApplication
import dev.andrej.echo.MainActivity
import dev.andrej.echo.R
import dev.andrej.echo.ai.AnalysisBlock
import dev.andrej.echo.ai.AnalysisOutcome
import dev.andrej.echo.data.Transcript
import dev.andrej.echo.ui.notes.title
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** What a finished (or stuck) note tells the user while Echo is in the background. */
sealed interface NoteStatus {
    data class Ready(val tasks: Int) : NoteStatus
    data object TranscriptReady : NoteStatus
    data class Failed(val reason: String) : NoteStatus
}

/** Null when the outcome is not worth a notification. */
internal fun noteStatus(outcome: AnalysisOutcome, tasks: Int): NoteStatus? = when (outcome) {
    is AnalysisOutcome.Success -> NoteStatus.Ready(tasks)
    is AnalysisOutcome.Blocked -> when (outcome.reason) {
        AnalysisBlock.EmptyTranscript -> null
        is AnalysisBlock.WaitingForModel, is AnalysisBlock.NoBackend -> NoteStatus.TranscriptReady
        is AnalysisBlock.GenerationFailed -> NoteStatus.Failed("Something went wrong while writing the summary.")
        AnalysisBlock.EmptyResult -> NoteStatus.Failed("The summary came out empty.")
    }
}

internal const val TRANSCRIPTION_FAILED = "Echo couldn't turn the recording into text."

class NoteStatusNotifier(
    private val context: Context,
    private val appVisible: () -> Boolean,
) {
    private val notifications = context.getSystemService(NotificationManager::class.java)

    // A parked note is retried on every drain; one card per note and status is enough.
    private val shown = mutableMapOf<String, NoteStatus>()

    init {
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Notes ready", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    @Synchronized
    fun post(note: Transcript, status: NoteStatus) {
        if (appVisible() || shown[note.id] == status) return
        shown[note.id] = status
        val title = note.title ?: title(note.text)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_echo)
            .setColor(RecordingNotifier.BRONZE)
            .setContentTitle(title)
            .setContentIntent(openNote(note.id, tasks = false))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setGroup(GROUP)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        when (status) {
            is NoteStatus.Ready -> {
                val summary = note.summary?.trim().orEmpty()
                builder.setSubText(if (status.tasks == 0) "Note ready" else "Note ready · ${tasks(status.tasks)}")
                    .setContentText(summary.lineSequence().firstOrNull())
                    .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
                    .setPublicVersion(public("Note ready"))
                if (status.tasks > 0) {
                    builder.addAction(R.drawable.ic_list_checks, "View tasks", openNote(note.id, tasks = true))
                }
            }
            NoteStatus.TranscriptReady -> builder.setSubText("Transcript ready")
                .setContentText("“${note.text.trim().lineSequence().firstOrNull().orEmpty()}”")
                .setPublicVersion(public("Transcript ready"))
            is NoteStatus.Failed -> {
                val text = "${status.reason} Your recording and transcript are saved."
                builder.setSubText("Couldn't finish")
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setPublicVersion(public("Couldn't finish"))
                    .addAction(R.drawable.ic_rotate_ccw, "Retry", retry(note.id))
            }
        }
        notifications.notify(TAG_PREFIX + note.id, 0, builder.build())
        postSummary()
    }

    @Synchronized
    fun cancel(id: String) {
        shown.remove(id)
        notifications.cancel(TAG_PREFIX + id, 0)
        postSummary()
    }

    /** Android shows a group's children under this once there are two or more. */
    private fun postSummary() {
        val count = notifications.activeNotifications.count { it.tag?.startsWith(TAG_PREFIX) == true }
        if (count < 2) {
            notifications.cancel(SUMMARY_ID)
            return
        }
        notifications.notify(
            SUMMARY_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_echo)
                .setColor(RecordingNotifier.BRONZE)
                .setContentTitle("$count notes ready")
                .setGroup(GROUP)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setSilent(true)
                .build(),
        )
    }

    private fun public(label: String): Notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_echo)
        .setColor(RecordingNotifier.BRONZE)
        .setContentTitle("Echo")
        .setSubText(label)
        .build()

    private fun openNote(id: String, tasks: Boolean): PendingIntent = PendingIntent.getActivity(
        context,
        if (tasks) 1 else 0,
        Intent(context, MainActivity::class.java)
            .setData(Uri.parse("echo://note/$id"))
            .putExtra(MainActivity.EXTRA_OPEN_NOTE, id)
            .putExtra(MainActivity.EXTRA_OPEN_NOTE_TASKS, tasks)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun retry(id: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, RetryReceiver::class.java).setData(Uri.parse("echo://retry/$id")).putExtra(EXTRA_NOTE_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun tasks(count: Int) = if (count == 1) "1 task" else "$count tasks"

    /** Retry swaps the card for the normal progress notification. */
    class RetryReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getStringExtra(EXTRA_NOTE_ID) ?: return
            val container = (context.applicationContext as EchoApplication).container
            container.noteStatus.cancel(id)
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    container.retryNote(id)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    private companion object {
        const val CHANNEL_ID = "notes_ready"
        const val GROUP = "dev.andrej.echo.NOTES_READY"
        const val TAG_PREFIX = "note:"
        const val SUMMARY_ID = 20261111
        const val EXTRA_NOTE_ID = "note_id"
    }
}
