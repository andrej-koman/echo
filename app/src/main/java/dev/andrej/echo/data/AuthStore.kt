package dev.andrej.echo.data

import android.content.Context
import dev.andrej.echo.auth.Account

interface AuthStore {
    var account: Account?

    /** Set once the user picks "without an account"; cleared on sign-in and sign-out. */
    var guest: Boolean
}

class SharedPreferencesAuthStore(context: Context) : AuthStore {

    private val preferences = context.getSharedPreferences("echo_auth", Context.MODE_PRIVATE)

    override var account: Account?
        get() {
            val id = preferences.getString(KEY_ID, null) ?: return null
            return Account(
                id = id,
                displayName = preferences.getString(KEY_NAME, null),
                email = preferences.getString(KEY_EMAIL, null),
                photoUrl = preferences.getString(KEY_PHOTO, null),
            )
        }
        set(value) {
            preferences.edit().apply {
                if (value == null) {
                    remove(KEY_ID)
                    remove(KEY_NAME)
                    remove(KEY_EMAIL)
                    remove(KEY_PHOTO)
                } else {
                    putString(KEY_ID, value.id)
                    putString(KEY_NAME, value.displayName)
                    putString(KEY_EMAIL, value.email)
                    putString(KEY_PHOTO, value.photoUrl)
                }
            }.apply()
        }

    override var guest: Boolean
        get() = preferences.getBoolean(KEY_GUEST, false)
        set(value) = preferences.edit().putBoolean(KEY_GUEST, value).apply()

    private companion object {
        const val KEY_ID = "account_id"
        const val KEY_NAME = "account_name"
        const val KEY_EMAIL = "account_email"
        const val KEY_PHOTO = "account_photo"
        const val KEY_GUEST = "guest"
    }
}
