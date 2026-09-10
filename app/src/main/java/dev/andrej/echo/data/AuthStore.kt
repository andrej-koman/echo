package dev.andrej.echo.data

import android.content.Context

interface AuthStore {
    /** Set once the user picks "without an account"; cleared on sign-in and sign-out. */
    var guest: Boolean
}

class SharedPreferencesAuthStore(context: Context) : AuthStore {

    private val preferences = context.getSharedPreferences("echo_auth", Context.MODE_PRIVATE)

    override var guest: Boolean
        get() = preferences.getBoolean(KEY_GUEST, false)
        set(value) = preferences.edit().putBoolean(KEY_GUEST, value).apply()

    private companion object {
        const val KEY_GUEST = "guest"
    }
}
