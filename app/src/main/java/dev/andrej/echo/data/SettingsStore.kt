package dev.andrej.echo.data

import android.content.Context

interface SettingsStore {
    /** The chosen speech language as a BCP-47 tag, or null before the user has picked one. */
    var languageTag: String?
}

class SharedPreferencesSettingsStore(context: Context) : SettingsStore {

    private val preferences = context.getSharedPreferences("echo_settings", Context.MODE_PRIVATE)

    override var languageTag: String?
        get() = preferences.getString(KEY_LANGUAGE, null)
        set(value) = preferences.edit().putString(KEY_LANGUAGE, value).apply()

    private companion object {
        const val KEY_LANGUAGE = "language"
    }
}
