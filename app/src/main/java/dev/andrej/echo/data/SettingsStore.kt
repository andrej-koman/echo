package dev.andrej.echo.data

import android.content.Context

interface SettingsStore {
    /** BCP-47 tag, null before the user has picked one. */
    var languageTag: String?

    /** Gates auto-enqueue in RecordViewModel.stopRecording() — off means every note waits for a manual Analyze. */
    var autoAnalyze: Boolean

    /** Gates AppContainer.downloadModel() — on, a download started off Wi-Fi is skipped. */
    var wifiOnlyDownload: Boolean

    /** Minutes before a timed item's dueAt that its reminder fires. */
    var reminderLeadMinutes: Int

    /** Local hour (0-23) a date-only item's reminder fires on. */
    var reminderMorningHour: Int
}

class SharedPreferencesSettingsStore(context: Context) : SettingsStore {

    private val preferences = context.getSharedPreferences("echo_settings", Context.MODE_PRIVATE)

    override var languageTag: String?
        get() = preferences.getString(KEY_LANGUAGE, null)
        set(value) = preferences.edit().putString(KEY_LANGUAGE, value).apply()

    override var autoAnalyze: Boolean
        get() = preferences.getBoolean(KEY_AUTO_ANALYZE, true)
        set(value) = preferences.edit().putBoolean(KEY_AUTO_ANALYZE, value).apply()

    override var wifiOnlyDownload: Boolean
        get() = preferences.getBoolean(KEY_WIFI_ONLY_DOWNLOAD, false)
        set(value) = preferences.edit().putBoolean(KEY_WIFI_ONLY_DOWNLOAD, value).apply()

    override var reminderLeadMinutes: Int
        get() = preferences.getInt(KEY_REMINDER_LEAD_MINUTES, 15)
        set(value) = preferences.edit().putInt(KEY_REMINDER_LEAD_MINUTES, value).apply()

    override var reminderMorningHour: Int
        get() = preferences.getInt(KEY_REMINDER_MORNING_HOUR, 9)
        set(value) = preferences.edit().putInt(KEY_REMINDER_MORNING_HOUR, value).apply()

    private companion object {
        const val KEY_LANGUAGE = "language"
        const val KEY_AUTO_ANALYZE = "auto_analyze"
        const val KEY_WIFI_ONLY_DOWNLOAD = "wifi_only_download"
        const val KEY_REMINDER_LEAD_MINUTES = "reminder_lead_minutes"
        const val KEY_REMINDER_MORNING_HOUR = "reminder_morning_hour"
    }
}
