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
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Google identity via Credential Manager, session and identity owned by Supabase Auth.
 *
 * The Credential Manager call is unchanged from the pre-Supabase implementation — it just
 * hands the resulting Google ID token to Supabase's `signInWith(IDToken)` instead of storing
 * it locally, so Supabase verifies it and becomes the source of truth for the session.
 */
class SupabaseAuthRepository(
    context: Context,
    private val supabase: SupabaseClient,
    private val store: AuthStore,
    scope: CoroutineScope,
) : AuthRepository {

    private val applicationContext = context.applicationContext
    private val credentialManager = CredentialManager.create(applicationContext)
    private val serverClientId = applicationContext.getString(R.string.google_web_client_id)
    private val supabaseConfigured =
        applicationContext.getString(R.string.supabase_url).isNotBlank() &&
            applicationContext.getString(R.string.supabase_anon_key).isNotBlank()

    private val guest = MutableStateFlow(store.guest)

    override val state: StateFlow<AuthState> = combine(
        supabase.auth.sessionStatus,
        guest,
        ::toAuthState,
    ).stateIn(scope, SharingStarted.Eagerly, AuthState.Unknown)

    private fun toAuthState(status: SessionStatus, isGuest: Boolean): AuthState = when (status) {
        is SessionStatus.Initializing -> AuthState.Unknown
        is SessionStatus.Authenticated -> AuthState.SignedIn(status.session.user!!.toAccount())
        is SessionStatus.NotAuthenticated -> if (isGuest) AuthState.Guest else AuthState.SignedOut
        is SessionStatus.RefreshFailure -> AuthState.SignedOut
    }

    private fun UserInfo.toAccount() = Account(
        id = id,
        displayName = userMetadata?.get("full_name")?.toString()?.trim('"'),
        email = email,
        photoUrl = userMetadata?.get("avatar_url")?.toString()?.trim('"'),
    )

    override suspend fun signIn(activity: Activity): Result<Account> {
        if (serverClientId.isBlank() || !supabaseConfigured) {
            return Result.failure(
                SignInUnavailable(
                    "Google sign-in is not configured for this build. Set " +
                        "google_web_client_id, supabase_url and supabase_anon_key in " +
                        "res/values/auth.xml.",
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
            supabase.auth.signInWith(IDToken) {
                idToken = google.idToken
                provider = Google
            }

            store.guest = false
            guest.value = false
            val account = (supabase.auth.sessionStatus.value as? SessionStatus.Authenticated)
                ?.session?.user?.toAccount()
                ?: return Result.failure(SignInUnavailable("Sign-in did not return a session."))
            Result.success(account)
        } catch (cancelled: GetCredentialCancellationException) {
            Result.failure(SignInCancelled())
        } catch (none: NoCredentialException) {
            Result.failure(SignInUnavailable("No Google account is available on this device."))
        } catch (failure: GetCredentialException) {
            Result.failure(SignInUnavailable(failure.message ?: "Google sign-in failed."))
        } catch (failure: Exception) {
            Result.failure(SignInUnavailable(failure.message ?: "Sign-in failed."))
        }
    }

    override fun continueAsGuest() {
        store.guest = true
        guest.value = true
    }

    override suspend fun signOut() {
        store.guest = false
        guest.value = false
        runCatching { supabase.auth.signOut() }
        runCatching {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        }
    }

    override suspend fun deleteAccount() {
        signOut()
    }
}
