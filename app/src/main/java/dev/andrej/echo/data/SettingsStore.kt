package dev.andrej.echo.data

import android.content.Context

/** Which day an imported file is filed under, once the user said "Always use this choice". */
enum class ImportDay { Recorded, Today }

enum class ThemeMode { System, Light, Dark }

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

    /** The active Notes AI model. Existing installs default to the original 1.7B model. */
    var notesAiModel: String

    /** A model whose download was explicitly requested and may be activated when ready. */
    var pendingNotesAiModel: String?

    /** Null asks on every import. */
    var importDay: ImportDay?

    var cachedIsPro: Boolean

    var themeMode: ThemeMode

    /** The always-there "Tap to record" notification; off by default since widgets and the tile cover it. */
    var idleNotification: Boolean
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

    override var notesAiModel: String
        get() = preferences.getString(KEY_NOTES_AI_MODEL, DEFAULT_NOTES_AI_MODEL) ?: DEFAULT_NOTES_AI_MODEL
        set(value) = preferences.edit().putString(KEY_NOTES_AI_MODEL, value).apply()

    override var pendingNotesAiModel: String?
        get() = preferences.getString(KEY_PENDING_NOTES_AI_MODEL, null)
        set(value) = preferences.edit().putString(KEY_PENDING_NOTES_AI_MODEL, value).apply()

    override var importDay: ImportDay?
        get() = preferences.getString(KEY_IMPORT_DAY, null)?.let { name -> ImportDay.entries.find { it.name == name } }
        set(value) = preferences.edit().putString(KEY_IMPORT_DAY, value?.name).apply()

    override var cachedIsPro: Boolean
        get() = preferences.getBoolean("billing_is_pro", false)
        set(value) = preferences.edit().putBoolean("billing_is_pro", value).apply()

    override var themeMode: ThemeMode
        get() = preferences.getString(KEY_THEME_MODE, null)
            ?.let { name -> ThemeMode.entries.find { it.name == name } } ?: ThemeMode.Dark
        set(value) = preferences.edit().putString(KEY_THEME_MODE, value.name).apply()

    override var idleNotification: Boolean
        get() = preferences.getBoolean(KEY_IDLE_NOTIFICATION, false)
        set(value) = preferences.edit().putBoolean(KEY_IDLE_NOTIFICATION, value).apply()

    private companion object {
        const val KEY_LANGUAGE = "transcription_language"
        const val KEY_AUTO_ANALYZE = "auto_analyze"
        const val KEY_DETECT_SPEAKERS = "detect_speakers"
        const val KEY_WIFI_ONLY_DOWNLOAD = "wifi_only_download"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_SPEECH_MODEL = "speech_model"
        const val KEY_NOTES_AI_MODEL = "notes_ai_model"
        const val KEY_PENDING_NOTES_AI_MODEL = "pending_notes_ai_model"
        const val KEY_IMPORT_DAY = "import_day"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_IDLE_NOTIFICATION = "idle_notification"
        const val DEFAULT_NOTES_AI_MODEL = "qwen3_1_7b"
    }
}
