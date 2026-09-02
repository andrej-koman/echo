package dev.andrej.echo.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dev.andrej.echo.R
import dev.andrej.echo.data.AuthStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Google identity via Credential Manager.
 *
 * Echo has no server, so the returned ID token is never verified anywhere — the signed-in
 * identity is a local label on a local-only app. If a backend ever appears, the token from
 * [GoogleIdTokenCredential.idToken] is what it would need to check.
 */
class GoogleAuthRepository(
    context: Context,
    private val store: AuthStore,
) : AuthRepository {

    private val applicationContext = context.applicationContext
    private val credentialManager = CredentialManager.create(applicationContext)
    private val serverClientId = applicationContext.getString(R.string.google_web_client_id)

    private val _state = MutableStateFlow(restore())
    override val state: StateFlow<AuthState> = _state.asStateFlow()

    private fun restore(): AuthState {
        val account = store.account
        return when {
            account != null -> AuthState.SignedIn(account)
            store.guest -> AuthState.Guest
            else -> AuthState.SignedOut
        }
    }

    override suspend fun signIn(activity: Activity): Result<Account> {
        if (serverClientId.isBlank()) {
            return Result.failure(
                SignInUnavailable(
                    "Google sign-in is not configured for this build. " +
                        "Set google_web_client_id in res/values/auth.xml.",
                ),
            )
        }

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
            .build()

        return try {
            val response = credentialManager.getCredential(activity, request)
            val credential = response.credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return Result.failure(SignInUnavailable("Unexpected credential type."))
            }

            val google = GoogleIdTokenCredential.createFrom(credential.data)
            val account = Account(
                id = google.id,
                displayName = google.displayName ?: google.givenName,
                email = google.id.takeIf { it.contains('@') },
                photoUrl = google.profilePictureUri?.toString(),
            )

            store.account = account
            store.guest = false
            _state.value = AuthState.SignedIn(account)
            Result.success(account)
        } catch (cancelled: GetCredentialCancellationException) {
            Result.failure(SignInCancelled())
        } catch (none: NoCredentialException) {
            Result.failure(SignInUnavailable("No Google account is available on this device."))
        } catch (failure: GetCredentialException) {
            Result.failure(SignInUnavailable(failure.message ?: "Google sign-in failed."))
        }
    }

    override fun continueAsGuest() {
        store.guest = true
        _state.value = AuthState.Guest
    }

    override suspend fun signOut() {
        store.account = null
        store.guest = false
        runCatching {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        }
        _state.value = AuthState.SignedOut
    }
}
