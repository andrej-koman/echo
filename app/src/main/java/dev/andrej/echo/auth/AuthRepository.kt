package dev.andrej.echo.auth

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

data class Account(
    val id: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
)

sealed interface AuthState {
    /** Before the stored session has been read. */
    data object Unknown : AuthState

    data object SignedOut : AuthState

    data class SignedIn(val account: Account) : AuthState

    /** Chose to use Echo without an account. Everything still works; nothing is tied to an identity. */
    data object Guest : AuthState
}

class SignInCancelled : Exception()

class SignInUnavailable(message: String) : Exception(message)

interface AuthRepository {

    val state: StateFlow<AuthState>

    /**
     * Credential Manager needs an Activity context to show the account sheet, so the caller
     * passes one down rather than the repository holding the application context.
     */
    suspend fun signIn(activity: Activity): Result<Account>

    fun continueAsGuest()

    suspend fun signOut()

    /** Same effect as [signOut] today — no server exists to actually delete an account against. */
    suspend fun deleteAccount()
}
