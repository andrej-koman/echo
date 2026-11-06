package dev.andrej.echo.ui.onboarding

import dev.andrej.echo.ai.ModelState
import dev.andrej.echo.ai.NotesAiModel
import dev.andrej.echo.speech.whisper.WHISPER_LARGE_V3_TURBO
import dev.andrej.echo.speech.whisper.WHISPER_SMALL
import dev.andrej.echo.speech.whisper.WhisperModel

enum class RecordUse { QuickThoughts, Meetings, Lectures, ExistingAudio }

enum class Priority { Faster, Accurate, HelpMeChoose }

data class Recommendation(val speech: WhisperModel, val detectSpeakers: Boolean)

/** Only offers what [choices] (the phone's memory limit) allows; [default] is the phone's own pick. */
fun recommendSetup(
    uses: Set<RecordUse>,
    priority: Priority,
    choices: List<WhisperModel>,
    default: WhisperModel,
): Recommendation {
    val speech = when (priority) {
        Priority.Faster -> WHISPER_SMALL.takeIf { it in choices } ?: default
        Priority.Accurate -> WHISPER_LARGE_V3_TURBO.takeIf { it in choices } ?: default
        Priority.HelpMeChoose -> default
    }
    return Recommendation(speech, detectSpeakers = RecordUse.Meetings in uses)
}

/** Screen 4 hides "More accurate" on phones that cannot hold the large model. */
fun accurateAvailable(choices: List<WhisperModel>): Boolean = WHISPER_LARGE_V3_TURBO in choices

data class SetupRow(val label: String, val totalBytes: Long, val ready: Boolean) {
    val remainingBytes: Long get() = if (ready) 0 else totalBytes
}

data class SetupSummary(val rows: List<SetupRow>, val freeBytes: Long) {
    val totalDownloadBytes: Long get() = rows.sumOf { it.remainingBytes }
}

/** The files [buildSetupSummary] counts as one row each; the speech row includes the small VAD file. */
data class SetupModelStates(
    val speech: ModelState,
    val vad: ModelState,
    val speakers: List<ModelState>,
    val notesAi: ModelState,
)

fun buildSetupSummary(
    speech: WhisperModel,
    vadBytes: Long,
    speakerBytes: Long,
    notesAi: NotesAiModel,
    detectSpeakers: Boolean,
    states: SetupModelStates,
    freeBytes: Long,
): SetupSummary {
    val rows = buildList {
        add(
            SetupRow(
                label = speech.name,
                totalBytes = speech.spec.bytes + vadBytes,
                ready = states.speech is ModelState.Ready && states.vad is ModelState.Ready,
            ),
        )
        if (detectSpeakers) {
            add(SetupRow("Speaker detection", speakerBytes, states.speakers.all { it is ModelState.Ready }))
        }
        add(SetupRow(notesAi.title, notesAi.spec.bytes, states.notesAi is ModelState.Ready))
    }
    return SetupSummary(rows, freeBytes)
}

sealed interface SetupStatus {
    /** Nothing requested, or nothing left to fetch. */
    data object Idle : SetupStatus

    data class Downloading(val fraction: Float) : SetupStatus

    data object WaitingForWifi : SetupStatus

    data object Failed : SetupStatus

    data object Done : SetupStatus
}

enum class SetupWork { None, Queued, Running, Failed }

/** [states] are the models the setup needs, in any order. */
fun computeSetupStatus(states: List<ModelState>, work: SetupWork): SetupStatus {
    if (states.all { it is ModelState.Ready }) return SetupStatus.Done
    if (work == SetupWork.Failed || (work != SetupWork.Running && states.any { it is ModelState.Failed })) {
        return SetupStatus.Failed
    }
    if (work == SetupWork.None) return SetupStatus.Idle
    val downloading = states.filterIsInstance<ModelState.Downloading>()
    if (work == SetupWork.Queued && downloading.isEmpty()) return SetupStatus.WaitingForWifi
    val total = downloading.sumOf { it.totalBytes }
    val done = downloading.sumOf { it.bytesDownloaded }
    return SetupStatus.Downloading(if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f)
}

fun formatSize(bytes: Long): String =
    if (bytes >= 1_000_000_000L) "%.1f GB".format(java.util.Locale.US, bytes / 1_000_000_000.0)
    else "${(bytes / 1_000_000.0).toInt().coerceAtLeast(1)} MB"
