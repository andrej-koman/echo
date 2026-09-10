package dev.andrej.echo.sync

import android.util.Log
import dev.andrej.echo.auth.AuthRepository
import dev.andrej.echo.auth.AuthState
import dev.andrej.echo.data.DerivedRepository
import dev.andrej.echo.data.SettingsStore
import dev.andrej.echo.data.TodoItem
import dev.andrej.echo.data.TranscriptRepository
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Client-driven, triggered-pull sync: no persistent socket, no server process. Every local
 * write, app foreground, and sign-in/enable calls [requestSync]; each run pulls remote changes
 * newer than the last pull cursor, then pushes local changes newer than the last push cursor.
 * Whole-row last-write-wins by [dev.andrej.echo.data.Transcript.updatedAt] /
 * [TodoItem.updatedAt] — no field-level merge.
 */
class SyncEngine(
    private val transcripts: TranscriptRepository,
    private val derived: DerivedRepository,
    private val postgrest: Postgrest,
    private val auth: AuthRepository,
    private val settings: SettingsStore,
    private val cursors: SyncStateStore,
    private val scope: CoroutineScope,
) {

    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val runLock = Mutex()

    private val _status = MutableStateFlow(
        SyncStatus(syncing = false, lastSyncedAt = cursors.lastSyncCompletedAt.takeIf { it > 0 }),
    )

    /** For display only — not consulted by the sync logic itself. */
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    init {
        scope.launch {
            @Suppress("OPT_IN_USAGE")
            requests.debounce(1000).collect { runSync() }
        }
    }

    fun requestSync() {
        requests.tryEmit(Unit)
    }

    private suspend fun runSync() {
        val account = (auth.state.value as? AuthState.SignedIn)?.account ?: return
        if (!settings.syncEnabled) return

        runLock.withLock {
            _status.value = _status.value.copy(syncing = true)
            val result = runCatching {
                syncTranscripts(account.id)
                syncTodoItems(account.id)
            }
            val lastSyncedAt = if (result.isSuccess) {
                System.currentTimeMillis().also { cursors.lastSyncCompletedAt = it }
            } else {
                Log.w(TAG, "sync failed", result.exceptionOrNull())
                _status.value.lastSyncedAt
            }
            _status.value = SyncStatus(syncing = false, lastSyncedAt = lastSyncedAt)
        }
    }

    private suspend fun syncTranscripts(userId: String) {
        val remoteChanged = postgrest.from(TRANSCRIPTS_TABLE)
            .select {
                filter {
                    eq("user_id", userId)
                    filter("updated_at", FilterOperator.GT, cursors.lastPulledTranscriptAt)
                }
            }
            .decodeList<TranscriptDto>()

        val local = transcripts.allForSync().associateBy { it.id }
        remoteChanged.forEach { remote ->
            val localUpdatedAt = local[remote.id]?.updatedAt ?: -1L
            if (remote.updatedAt > localUpdatedAt) {
                transcripts.upsertFromSync(remote.toDomain())
            }
        }
        remoteChanged.maxOfOrNull { it.updatedAt }?.let { cursors.lastPulledTranscriptAt = it }

        val toPush = transcripts.allForSync().filter { it.updatedAt > cursors.lastPushedTranscriptAt }
        if (toPush.isNotEmpty()) {
            postgrest.from(TRANSCRIPTS_TABLE).upsert(toPush.map { TranscriptDto.from(it, userId) })
            cursors.lastPushedTranscriptAt = toPush.maxOf { it.updatedAt }
        }
    }

    private suspend fun syncTodoItems(userId: String) {
        val remoteChanged = postgrest.from(TODO_ITEMS_TABLE)
            .select {
                filter {
                    eq("user_id", userId)
                    filter("updated_at", FilterOperator.GT, cursors.lastPulledTodoAt)
                }
            }
            .decodeList<TodoItemDto>()

        val local = derived.allForSync().associateBy(TodoItem::id)
        remoteChanged.forEach { remote ->
            val localUpdatedAt = local[remote.id]?.updatedAt ?: -1L
            if (remote.updatedAt > localUpdatedAt) {
                derived.upsertFromSync(remote.toDomain())
            }
        }
        remoteChanged.maxOfOrNull { it.updatedAt }?.let { cursors.lastPulledTodoAt = it }

        val toPush = derived.allForSync().filter { it.updatedAt > cursors.lastPushedTodoAt }
        if (toPush.isNotEmpty()) {
            postgrest.from(TODO_ITEMS_TABLE).upsert(toPush.map { TodoItemDto.from(it, userId) })
            cursors.lastPushedTodoAt = toPush.maxOf { it.updatedAt }
        }
    }

    private companion object {
        const val TAG = "SyncEngine"
        const val TRANSCRIPTS_TABLE = "transcripts"
        const val TODO_ITEMS_TABLE = "todo_items"
    }
}

data class SyncStatus(val syncing: Boolean, val lastSyncedAt: Long?)
