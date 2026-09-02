package dev.andrej.echo.ui.auth

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.andrej.echo.auth.AuthRepository
import dev.andrej.echo.auth.AuthState
import dev.andrej.echo.auth.SignInCancelled
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AuthRepository,
) : ViewModel() {

    val state: StateFlow<AuthState> = repository.state

    var busy by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    fun signIn(activity: Activity) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            repository.signIn(activity).onFailure { failure ->
                error = if (failure is SignInCancelled) null else failure.message
            }
            busy = false
        }
    }

    fun continueAsGuest() {
        repository.continueAsGuest()
    }

    fun signOut() {
        viewModelScope.launch { repository.signOut() }
    }
}
