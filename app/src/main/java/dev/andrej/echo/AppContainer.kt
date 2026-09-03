package dev.andrej.echo

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.andrej.echo.auth.AuthRepository
import dev.andrej.echo.auth.GoogleAuthRepository
import dev.andrej.echo.ai.LlmRunnerProvider
import dev.andrej.echo.ai.MlKitLlmRunner
import dev.andrej.echo.ai.TranscriptAnalyzer
import dev.andrej.echo.data.AuthStore
import dev.andrej.echo.data.DerivedRepository
import dev.andrej.echo.data.JsonDerivedRepository
import dev.andrej.echo.data.JsonTranscriptRepository
import dev.andrej.echo.data.SettingsStore
import dev.andrej.echo.data.SharedPreferencesAuthStore
import dev.andrej.echo.data.SharedPreferencesSettingsStore
import dev.andrej.echo.data.TranscriptRepository
import dev.andrej.echo.speech.AndroidSpeechEngine
import dev.andrej.echo.speech.TranscriptionEngine
import dev.andrej.echo.ui.auth.AuthViewModel
import dev.andrej.echo.ui.record.RecordViewModel
import dev.andrej.echo.ui.transcripts.TranscriptsViewModel

class AppContainer(context: Context) {

    private val applicationContext = context.applicationContext

    val repository: TranscriptRepository =
        JsonTranscriptRepository(applicationContext.filesDir)

    val settings: SettingsStore =
        SharedPreferencesSettingsStore(applicationContext)

    private val authStore: AuthStore =
        SharedPreferencesAuthStore(applicationContext)

    val auth: AuthRepository =
        GoogleAuthRepository(applicationContext, authStore)

    val engine: TranscriptionEngine =
        AndroidSpeechEngine(applicationContext)

    val derived: DerivedRepository =
        JsonDerivedRepository(applicationContext.filesDir)

    // Ordered by preference: Nano costs nothing to use where the device has it.
    private val llmRunners = LlmRunnerProvider(listOf(MlKitLlmRunner()))

    val analyzer = TranscriptAnalyzer(llmRunners::runner)

    val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
            modelClass.isAssignableFrom(RecordViewModel::class.java) ->
                RecordViewModel(engine, repository, settings) as T

            modelClass.isAssignableFrom(TranscriptsViewModel::class.java) ->
                TranscriptsViewModel(repository) as T

            modelClass.isAssignableFrom(AuthViewModel::class.java) ->
                AuthViewModel(auth) as T

            else -> error("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
