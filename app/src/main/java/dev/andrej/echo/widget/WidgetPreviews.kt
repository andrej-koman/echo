package dev.andrej.echo.widget

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import dev.andrej.echo.notify.RecordingMode

/** Sample content for the launcher's widget picker, so each widget shows what it holds rather than a blank grid. */
internal val previewSnapshot = WidgetSnapshot(
    recording = WidgetRecording(RecordingMode.Idle),
    today = listOf(
        WidgetTask("p1", "Send the invoice to Marko", "10:00", overdue = false),
        WidgetTask("p2", "Book train tickets for Ljubljana", "14:30", overdue = false),
        WidgetTask("p3", "Reply to Ana about Saturday", "18:00", overdue = false),
        WidgetTask("p4", "Pick up the dry cleaning", null, overdue = false),
    ),
    recent = listOf(
        WidgetNote(
            id = "p1",
            title = "Weekly planning",
            date = "Today, 9:42",
            shortDate = "9:42",
            line = "Launch moves to Thursday; Marko owns the release notes.",
        ),
        WidgetNote("p2", "Kitchen renovation quote", "Today, 8:15", "8:15", "Today, 8:15 · Tiles, cabinets and the plumber’s estimate.", tag = NoteTag.Writing),
        WidgetNote(
            id = "p3",
            title = "Podcast idea",
            date = "Yesterday, 21:03",
            shortDate = "Yesterday",
            line = "An episode on how people take notes while walking.",
        ),
    ),
).let { it.copy(agenda = WidgetAgenda("Today", it.today)) }

private const val PREFS = "widget_previews"
private const val KEY_PUBLISHED_FOR = "published_for_install"

/**
 * Generated previews (Android 15+) are set at runtime and rate limited by the system, so they are
 * pushed once per install or update; a rate-limited attempt is retried on the next app start.
 */
internal suspend fun publishWidgetPreviews(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
    val installed = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (prefs.getLong(KEY_PUBLISHED_FOR, 0) == installed) return
    val manager = GlanceAppWidgetManager(context)
    val receivers = listOf(
        RecordWidgetReceiver::class,
        CombinedWidgetReceiver::class,
        TasksWidgetReceiver::class,
        RecentWidgetReceiver::class,
    )
    val allSet = receivers.all { receiver ->
        runCatching { manager.setWidgetPreviews(receiver) == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS }
            .onFailure { Log.w("WidgetPreviews", "Couldn't set preview for ${receiver.simpleName}", it) }
            .getOrDefault(false)
    }
    if (allSet) prefs.edit().putLong(KEY_PUBLISHED_FOR, installed).apply()
}
