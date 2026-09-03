package dev.andrej.echo

import android.app.ActivityManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.andrej.echo.auth.AuthRepository
import dev.andrej.echo.auth.GoogleAuthRepository
import dev.andrej.echo.ai.HttpModelDownloader
import dev.andrej.echo.ai.LiteRtLlmRunner
import dev.andrej.echo.ai.LlmRunnerProvider
import dev.andrej.echo.ai.MlKitLlmRunner
import dev.andrej.echo.ai.ModelStore
import dev.andrej.echo.ai.QWEN3_1_7B_INT4
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
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

    val modelStore = ModelStore(
        directory = File(applicationContext.filesDir, "models"),
        spec = QWEN3_1_7B_INT4,
        downloader = HttpModelDownloader(),
    )

    private val llmRunners = LlmRunnerProvider(
        listOf(
            MlKitLlmRunner(),
            LiteRtLlmRunner(
                store = modelStore,
                activityManager = applicationContext.getSystemService(ActivityManager::class.java),
            ),
        ),
    )

    val analyzer = TranscriptAnalyzer(llmRunners::runner)

    /**
     * Application-scoped on purpose: leaving Capture must not cancel a half-finished
     * Engine.initialize(), which would throw away the wait and leak the partial engine.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun warmUpLlm() {
        appScope.launch { analyzer.warmup() }
    }

    val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
            modelClass.isAssignableFrom(RecordViewModel::class.java) ->
                RecordViewModel(
                    engine = engine,
                    repository = repository,
                    derived = derived,
                    analyzer = analyzer,
                    settings = settings,
                    warmup = ::warmUpLlm,
                ) as T

            modelClass.isAssignableFrom(TranscriptsViewModel::class.java) ->
                TranscriptsViewModel(repository) as T

            modelClass.isAssignableFrom(AuthViewModel::class.java) ->
                AuthViewModel(auth) as T

            else -> error("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
