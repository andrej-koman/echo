package dev.andrej.echo.data

import android.content.Context

/** Which day an imported file is filed under, once the user said "Always use this choice". */
enum class ImportDay { Recorded, Today }

interface SettingsStore {
    /** Whisper language code such as `sl`; null auto-detects. */
    var transcriptionLanguage: String?

    /** Gates the analysis enqueue after transcription — off means every note waits for a manual Analyze. */
    var autoAnalyze: Boolean

    /** Diarization after each transcription, on by default. */
    var detectSpeakers: Boolean

    /** Gates AppContainer.downloadModel() — on, a download started off Wi-Fi is skipped. */
    var wifiOnlyDownload: Boolean

    var onboardingDone: Boolean

    /** A `WhisperModel.id`; null picks by the phone's memory. */
    var speechModel: String?

    /** Null asks on every import. */
    var importDay: ImportDay?
}

class SharedPreferencesSettingsStore(context: Context) : SettingsStore {

    private val preferences = context.getSharedPreferences("echo_settings", Context.MODE_PRIVATE)

    override var transcriptionLanguage: String?
        get() = preferences.getString(KEY_LANGUAGE, null)
        set(value) = preferences.edit().putString(KEY_LANGUAGE, value).apply()

    override var autoAnalyze: Boolean
        get() = preferences.getBoolean(KEY_AUTO_ANALYZE, true)
        set(value) = preferences.edit().putBoolean(KEY_AUTO_ANALYZE, value).apply()

    override var detectSpeakers: Boolean
        get() = preferences.getBoolean(KEY_DETECT_SPEAKERS, true)
        set(value) = preferences.edit().putBoolean(KEY_DETECT_SPEAKERS, value).apply()

    override var wifiOnlyDownload: Boolean
        get() = preferences.getBoolean(KEY_WIFI_ONLY_DOWNLOAD, false)
        set(value) = preferences.edit().putBoolean(KEY_WIFI_ONLY_DOWNLOAD, value).apply()

    override var onboardingDone: Boolean
        get() = preferences.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = preferences.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    override var speechModel: String?
        get() = preferences.getString(KEY_SPEECH_MODEL, null)
        set(value) = preferences.edit().putString(KEY_SPEECH_MODEL, value).apply()

    override var importDay: ImportDay?
        get() = preferences.getString(KEY_IMPORT_DAY, null)?.let { name -> ImportDay.entries.find { it.name == name } }
        set(value) = preferences.edit().putString(KEY_IMPORT_DAY, value?.name).apply()

    private companion object {
        const val KEY_LANGUAGE = "transcription_language"
        const val KEY_AUTO_ANALYZE = "auto_analyze"
        const val KEY_DETECT_SPEAKERS = "detect_speakers"
        const val KEY_WIFI_ONLY_DOWNLOAD = "wifi_only_download"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_SPEECH_MODEL = "speech_model"
        const val KEY_IMPORT_DAY = "import_day"
    }
}
