package dev.andrej.echo.widget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import dev.andrej.echo.data.DerivedRepository
import dev.andrej.echo.data.TodoItem
import dev.andrej.echo.data.Transcript
import dev.andrej.echo.notify.mode
import dev.andrej.echo.ui.notes.Work
import dev.andrej.echo.ui.record.RecordUiState
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One snapshot behind every widget. Widgets read [snapshot] when they compose; a change pushes an
 * update to all of them, at most once a second since transcription progress moves per percent.
 */
class WidgetUpdater internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val derived: DerivedRepository,
    recording: StateFlow<RecordUiState>,
    items: Flow<List<TodoItem>>,
    transcripts: Flow<List<Transcript>>,
    work: Flow<Work>,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _snapshot = MutableStateFlow(WidgetSnapshot())
    val snapshot: StateFlow<WidgetSnapshot> = _snapshot.asStateFlow()

    private val justDone = MutableStateFlow(emptySet<String>())
    private val snoozed = MutableStateFlow(emptySet<String>())
    private val tick = MutableStateFlow(0)

    private val recordingState = recording
        .distinctUntilChangedBy { it.mode() }
        .map { WidgetRecording(it.mode(), it.elapsedMs, SystemClock.elapsedRealtime() - it.elapsedMs) }

    private val transient = combine(justDone, snoozed, tick) { done, later, _ -> done to later }

    private val notes = combine(transcripts, work) { list, current -> list to current }

    init {
        scope.launch {
            combine(recordingState, items, notes, transient) { rec, tasks, (list, current), (done, later) ->
                val time = now()
                WidgetSnapshot(
                    recording = rec,
                    micAllowed = micAllowed(),
                    today = todayTasks(tasks, time, ZoneId.systemDefault(), done, later),
                    recent = recentNotes(list, current, time),
                )
            }.distinctUntilChanged().collectLatest { next ->
                _snapshot.value = next
                updateAll()
                delay(1_000)
            }
        }
        // Overdue rows and the day roll over with the clock, not with any data change.
        scope.launch {
            while (true) {
                delay(15 * 60_000L)
                refresh()
            }
        }
    }

    /** Mic permission and the clock change outside any flow; the app calls this when it comes forward. */
    fun refresh() = tick.update { it + 1 }

    suspend fun tick(taskId: String) {
        justDone.update { it + taskId }
        derived.setDone(taskId, true)
        scope.launch {
            delay(2_000)
            justDone.update { it - taskId }
        }
    }

    suspend fun snooze(taskId: String) {
        snoozed.update { it + taskId }
        derived.snooze(taskId)
        scope.launch {
            delay(4_000)
            snoozed.update { it - taskId }
        }
    }

    private fun micAllowed() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private suspend fun updateAll() {
        RecordWidget().updateAll(context)
        CombinedWidget().updateAll(context)
        TasksWidget().updateAll(context)
        RecentWidget().updateAll(context)
    }
}
