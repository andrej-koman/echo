package dev.andrej.echo.export

import dev.andrej.echo.data.Segment
import dev.andrej.echo.data.Speaker
import dev.andrej.echo.data.TodoItem
import dev.andrej.echo.ui.notes.clock

/** What a note exports: segments when Whisper left any, [text] alone for a note without them. */
data class ExportNote(
    val title: String,
    val date: String,
    val text: String,
    val segments: List<Segment>,
    val speakers: List<Speaker>,
    val summary: String? = null,
    val tasks: List<TodoItem> = emptyList(),
)

data class ExportOptions(val speakerNames: Boolean = true, val timestamps: Boolean = false)

fun exportText(note: ExportNote, options: ExportOptions): String =
    lines(note, options).joinToString("\n\n") + "\n"

fun exportMarkdown(note: ExportNote, options: ExportOptions): String = buildString {
    append("# ").append(note.title).append("\n\n")
    append("_").append(note.date).append("_\n\n")
    note.summary?.let { append("## Summary\n\n").append(it.trim()).append("\n\n") }
    if (note.tasks.isNotEmpty()) {
        append("## Tasks\n\n")
        note.tasks.forEach { append(if (it.done) "- [x] " else "- [ ] ").append(it.text).append('\n') }
        append('\n')
    }
    append("## Transcript\n\n")
    append(lines(note, options, bold = true).joinToString("\n\n")).append('\n')
}

/** Timed by definition, so [ExportOptions.timestamps] doesn't apply. */
fun exportSrt(note: ExportNote, options: ExportOptions): String =
    note.segments.mapIndexed { i, segment ->
        val prefix = speakerPrefix(note, segment, options)
        "${i + 1}\n${srtTime(segment.startMs)} --> ${srtTime(segment.endMs)}\n$prefix${segment.text.trim()}\n"
    }.joinToString("\n")

/** Lines from one speaker run together into one paragraph, labelled once. */
private fun lines(note: ExportNote, options: ExportOptions, bold: Boolean = false): List<String> {
    if (note.segments.isEmpty()) return listOf(note.text.trim())
    val runs = mutableListOf<MutableList<Segment>>()
    note.segments.forEach { segment ->
        val last = runs.lastOrNull()
        if (last != null && last.first().speaker == segment.speaker && !options.timestamps) last += segment
        else runs += mutableListOf(segment)
    }
    return runs.map { run ->
        val first = run.first()
        val label = buildString {
            if (options.timestamps) append('[').append(clock(first.startMs)).append("] ")
            val name = if (options.speakerNames) speakerName(note, first) else null
            if (name != null) append(if (bold) "**$name:** " else "$name: ")
        }
        label + run.joinToString(" ") { it.text.trim() }
    }
}

private fun speakerPrefix(note: ExportNote, segment: Segment, options: ExportOptions): String =
    if (options.speakerNames) speakerName(note, segment)?.let { "$it: " }.orEmpty() else ""

private fun speakerName(note: ExportNote, segment: Segment): String? {
    val index = segment.speaker ?: return null
    return note.speakers.firstOrNull { it.index == index }?.label ?: "Speaker ${index + 1}"
}

internal fun srtTime(ms: Long): String =
    "%02d:%02d:%02d,%03d".format(ms / 3_600_000, ms / 60_000 % 60, ms / 1_000 % 60, ms % 1_000)

/** A file name from a note's title: no path separators or characters other apps reject. */
fun fileName(title: String): String =
    title.replace(Regex("""[\\/:*?"<>|\n\r\t]"""), " ").replace(Regex("\\s+"), " ").trim().take(60).ifEmpty { "Echo note" }
