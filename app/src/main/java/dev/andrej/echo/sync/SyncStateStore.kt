package dev.andrej.echo.sync

import android.content.Context

interface SyncStateStore {
    var lastPulledTranscriptAt: Long
    var lastPushedTranscriptAt: Long
    var lastPulledTodoAt: Long
    var lastPushedTodoAt: Long

    /** Wall-clock stamp of the last fully successful sync run, for display only. 0 = never. */
    var lastSyncCompletedAt: Long
}

class SharedPreferencesSyncStateStore(context: Context) : SyncStateStore {

    private val preferences = context.getSharedPreferences("echo_sync", Context.MODE_PRIVATE)

    override var lastPulledTranscriptAt: Long
        get() = preferences.getLong(KEY_PULLED_TRANSCRIPT, 0L)
        set(value) = preferences.edit().putLong(KEY_PULLED_TRANSCRIPT, value).apply()

    override var lastPushedTranscriptAt: Long
        get() = preferences.getLong(KEY_PUSHED_TRANSCRIPT, 0L)
        set(value) = preferences.edit().putLong(KEY_PUSHED_TRANSCRIPT, value).apply()

    override var lastPulledTodoAt: Long
        get() = preferences.getLong(KEY_PULLED_TODO, 0L)
        set(value) = preferences.edit().putLong(KEY_PULLED_TODO, value).apply()

    override var lastPushedTodoAt: Long
        get() = preferences.getLong(KEY_PUSHED_TODO, 0L)
        set(value) = preferences.edit().putLong(KEY_PUSHED_TODO, value).apply()

    override var lastSyncCompletedAt: Long
        get() = preferences.getLong(KEY_LAST_SYNC_COMPLETED, 0L)
        set(value) = preferences.edit().putLong(KEY_LAST_SYNC_COMPLETED, value).apply()

    private companion object {
        const val KEY_PULLED_TRANSCRIPT = "pulled_transcript"
        const val KEY_PUSHED_TRANSCRIPT = "pushed_transcript"
        const val KEY_PULLED_TODO = "pulled_todo"
        const val KEY_PUSHED_TODO = "pushed_todo"
        const val KEY_LAST_SYNC_COMPLETED = "last_sync_completed"
    }
}
