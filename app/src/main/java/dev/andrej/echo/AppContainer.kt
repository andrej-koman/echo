package dev.andrej.echo

import android.app.ActivityManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.andrej.echo.auth.AuthRepository
import dev.andrej.echo.auth.GoogleAuthRepository
import dev.andrej.echo.ai.AiCardState
import dev.andrej.echo.ai.HttpModelDownloader
import dev.andrej.echo.ai.LiteRtLlmRunner
import dev.andrej.echo.ai.LlmAvailability
import dev.andrej.echo.ai.LlmRunnerProvider
import dev.andrej.echo.ai.MlKitLlmRunner
import dev.andrej.echo.ai.ModelStore
import dev.andrej.echo.ai.PendingAnalysisWorker
import dev.andrej.echo.ai.QWEN3_1_7B_INT4
import dev.andrej.echo.ai.TranscriptAnalyzer
import dev.andrej.echo.ai.toCardState
import dev.andrej.echo.data.AnalysisQueueRepository
import dev.andrej.echo.data.AuthStore
import dev.andrej.echo.data.DerivedRepository
import dev.andrej.echo.data.JsonAnalysisQueueRepository
import dev.andrej.echo.data.JsonDerivedRepository
import dev.andrej.echo.data.JsonTranscriptRepository
import dev.andrej.echo.data.SettingsStore
import dev.andrej.echo.data.SharedPreferencesAuthStore
import dev.andrej.echo.data.SharedPreferencesSettingsStore
import dev.andrej.echo.data.TranscriptRepository
import dev.andrej.echo.speech.AndroidSpeechEngine
import dev.andrej.echo.speech.TranscriptionEngine
import dev.andrej.echo.ui.auth.AiViewModel
import dev.andrej.echo.ui.auth.AuthViewModel
import dev.andrej.echo.ui.home.HomeViewModel
import dev.andrej.echo.ui.record.RecordViewModel
import dev.andrej.echo.ui.reminders.RemindersViewModel
import dev.andrej.echo.ui.tasks.TasksViewModel
import dev.andrej.echo.ui.transcripts.TranscriptsViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
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

    val analysisQueue: AnalysisQueueRepository =
        JsonAnalysisQueueRepository(applicationContext.filesDir)

    val modelStore = ModelStore(
        directory = File(applicationContext.filesDir, "models"),
        spec = QWEN3_1_7B_INT4,
        downloader = HttpModelDownloader(),
    )

    private val mlKitRunner = MlKitLlmRunner()

    private val liteRtRunner = LiteRtLlmRunner(
        store = modelStore,
        activityManager = applicationContext.getSystemService(ActivityManager::class.java),
    )

    private val llmRunners = LlmRunnerProvider(listOf(mlKitRunner, liteRtRunner))

    val analyzer = TranscriptAnalyzer(llmRunners::runner)

    val aiCardState: Flow<AiCardState> = flow {
        emit(AiCardState.Checking)
        if (mlKitRunner.availability() is LlmAvailability.Ready) {
            emit(AiCardState.NanoReady)
        } else {
            emitAll(modelStore.state.map { liteRtRunner.availability().toCardState() })
        }
    }

    /**
     * Application-scoped on purpose: leaving Capture must not cancel a half-finished
     * Engine.initialize(), which would throw away the wait and leak the partial engine. The same
     * reasoning covers model download/delete, which must survive leaving AccountScreen.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun warmUpLlm() {
        appScope.launch { analyzer.warmup() }
    }

    val pendingAnalysisWorker = PendingAnalysisWorker(
        queue = analysisQueue,
        transcripts = repository,
        derived = derived,
        analyzer = analyzer,
        scope = appScope,
    )

    init {
        // Collapsed to the state's kind, not raw equality — Downloading ticks progress on every
        // chunk and neither invalidating the runner cache nor scanning the queue needs to happen
        // that often. The payoff case is Absent/Failed -> Ready: a finished download used to sit
        // there unnoticed until the app was killed and relaunched (LlmRunnerProvider cached its
        // "no backend" answer for the process's life).
        appScope.launch {
            modelStore.state
                .map { it.javaClass.simpleName }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    llmRunners.invalidate()
                    pendingAnalysisWorker.requestDrain()
                }
        }
    }

    /** Called from Activity.onStart — a capable backend may have appeared while backgrounded. */
    fun onAppForegrounded() {
        llmRunners.invalidate()
        pendingAnalysisWorker.requestDrain()
    }

    private var downloadJob: Job? = null

    fun downloadModel() {
        if (downloadJob?.isActive == true) return
        downloadJob = appScope.launch { modelStore.download() }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    fun deleteModel() {
        appScope.launch { modelStore.delete() }
    }

    val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
            modelClass.isAssignableFrom(RecordViewModel::class.java) ->
                RecordViewModel(
                    engine = engine,
                    repository = repository,
                    queue = analysisQueue,
                    worker = pendingAnalysisWorker,
                    settings = settings,
                    warmup = ::warmUpLlm,
                ) as T

            modelClass.isAssignableFrom(TranscriptsViewModel::class.java) ->
                TranscriptsViewModel(repository, analysisQueue, pendingAnalysisWorker) as T

            modelClass.isAssignableFrom(AuthViewModel::class.java) ->
                AuthViewModel(auth) as T

            modelClass.isAssignableFrom(AiViewModel::class.java) ->
                AiViewModel(
                    aiCardState = aiCardState,
                    onDownloadModel = ::downloadModel,
                    onCancelDownload = ::cancelDownload,
                    onDeleteModel = ::deleteModel,
                ) as T

            modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                HomeViewModel(repository, derived, analysisQueue) as T

            modelClass.isAssignableFrom(TasksViewModel::class.java) ->
                TasksViewModel(derived, repository, aiCardState) as T

            modelClass.isAssignableFrom(RemindersViewModel::class.java) ->
                RemindersViewModel(derived, repository) as T

            else -> error("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
