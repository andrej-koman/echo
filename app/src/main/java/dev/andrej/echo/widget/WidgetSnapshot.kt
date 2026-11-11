package dev.andrej.echo.widget

import dev.andrej.echo.data.TodoItem
import dev.andrej.echo.data.Transcript
import dev.andrej.echo.notify.RecordingMode
import dev.andrej.echo.ui.notes.NoteStage
import dev.andrej.echo.ui.notes.Work
import dev.andrej.echo.ui.notes.rows
import dev.andrej.echo.ui.tasks.groupByDue
import dev.andrej.echo.ui.tasks.isOverdue
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** [base] is the `SystemClock.elapsedRealtime()` the take would have started at, for a ticking Chronometer. */
data class WidgetRecording(val mode: RecordingMode, val elapsedMs: Long = 0, val base: Long = 0)

data class WidgetTask(
    val id: String,
    val text: String,
    val time: String?,
    val overdue: Boolean,
    val done: Boolean = false,
    val snoozed: Boolean = false,
)

sealed interface NoteTag {
    data class Transcribing(val percent: Int) : NoteTag
    data object Writing : NoteTag
    data object Failed : NoteTag
}

data class WidgetNote(
    val id: String,
    val title: String,
    /** "Today, 9:42" — the combined widget's second line. */
    val date: String,
    /** Right-aligned when there is no tag: a time today, else the day. */
    val shortDate: String,
    val line: String,
    val tag: NoteTag? = null,
)

data class WidgetSnapshot(
    val recording: WidgetRecording = WidgetRecording(RecordingMode.Idle),
    val micAllowed: Boolean = true,
    val today: List<WidgetTask> = emptyList(),
    val recent: List<WidgetNote> = emptyList(),
)

private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/**
 * The widget "Today": overdue first, then today's timed and date-only tasks. Undated tasks stay in
 * the app. A task just ticked stays a moment, struck through, so the tap visibly landed.
 */
internal fun todayTasks(
    items: List<TodoItem>,
    now: Long,
    zone: ZoneId,
    justDone: Set<String> = emptySet(),
    snoozed: Set<String> = emptySet(),
): List<WidgetTask> {
    val visible = items.filter { !it.done || it.id in justDone }.map { if (it.id in justDone) it.copy(done = false) else it }
    val today = groupByDue(visible, now, zone).firstOrNull { it.label == "Today" }?.items.orEmpty()
    return today.filter { it.hasDate }.map { task ->
        WidgetTask(
            id = task.id,
            text = task.text,
            time = if (task.hasTime) Instant.ofEpochMilli(task.dueAt).atZone(zone).toLocalTime().format(timeFormat) else null,
            overdue = isOverdue(task, now, zone),
            done = task.id in justDone,
            snoozed = task.id in snoozed,
        )
    }
}

internal fun recentNotes(transcripts: List<Transcript>, work: Work, now: Long, count: Int = 3): List<WidgetNote> =
    rows(transcripts, now, work).take(count).map { row ->
        val tag = when (val stage = row.stage) {
            is NoteStage.Transcribing -> NoteTag.Transcribing((stage.fraction * 100).toInt())
            NoteStage.Analyzing -> NoteTag.Writing
            NoteStage.Failed -> NoteTag.Failed
            NoteStage.Waiting, NoteStage.Done -> null
        }
        val day = row.date.substringBefore(", ")
        WidgetNote(
            id = row.id,
            title = row.title,
            date = row.date,
            shortDate = if (day == "Today") row.date.substringAfter(", ") else day,
            line = when (tag) {
                null -> row.excerpt.trim().lineSequence().firstOrNull().orEmpty()
                NoteTag.Failed -> "${row.date} · open to retry"
                else -> "${row.date} · ${row.excerpt.trim().lineSequence().firstOrNull().orEmpty()}"
            },
            tag = tag,
        )
    }
