package dev.andrej.echo

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import java.time.ZoneId
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.andrej.echo.auth.AuthRepository
import dev.andrej.echo.auth.GoogleAuthRepository
import dev.andrej.echo.ai.AiCardState
import dev.andrej.echo.ai.HttpModelDownloader
import dev.andrej.echo.ai.LiteRtLlmRunner
import dev.andrej.echo.ai.LlmAvailability
import dev.andrej.echo.ai.LlmBackend
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
import dev.andrej.echo.ai.ModelState
import dev.andrej.echo.notify.AlarmManagerNotificationScheduler
import dev.andrej.echo.notify.DailyBriefReceiver
import dev.andrej.echo.notify.DailyBriefScheduler
import dev.andrej.echo.notify.ModelDownloadNotifier
import dev.andrej.echo.notify.ReminderAlarmReceiver
import dev.andrej.echo.speech.AndroidSpeechEngine
import dev.andrej.echo.speech.TranscriptionEngine
import dev.andrej.echo.ui.auth.AuthViewModel
import dev.andrej.echo.ui.home.HomeViewModel
import dev.andrej.echo.ui.home.snoozeTime
import dev.andrej.echo.ui.profile.HistoryViewModel
import dev.andrej.echo.ui.profile.ProfileViewModel
import dev.andrej.echo.ui.profile.SettingsViewModel
import dev.andrej.echo.ui.record.RecordViewModel
import dev.andrej.echo.ui.tasks.TasksViewModel
import dev.andrej.echo.ui.notes.NotesViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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

    private val notificationScheduler = AlarmManagerNotificationScheduler(applicationContext, settings)

    private val dailyBriefScheduler = DailyBriefScheduler(applicationContext, settings)

    private val modelDownloadNotifier = ModelDownloadNotifier(applicationContext)

    val derived: DerivedRepository =
        JsonDerivedRepository(applicationContext.filesDir, notificationScheduler)

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

    private val preferredBackend = MutableStateFlow(LlmBackend.fromId(settings.preferredLlmBackend))

    private val llmRunners = LlmRunnerProvider(
        candidates = mapOf(LlmBackend.NANO to mlKitRunner, LlmBackend.LITERT to liteRtRunner),
        preferredBackend = { preferredBackend.value },
    )

    val analyzer = TranscriptAnalyzer(llmRunners::runner)

    private fun aiCardStateFor(backend: LlmBackend): Flow<AiCardState> = when (backend) {
        LlmBackend.NANO -> flow { emit(mlKitRunner.availability().toCardState()) }
        LlmBackend.LITERT -> modelStore.state.map { liteRtRunner.availability().toCardState() }
    }

    private val autoAiCardState: Flow<AiCardState> = flow {
        if (mlKitRunner.availability() is LlmAvailability.Ready) {
            emit(AiCardState.NanoReady)
        } else {
            emitAll(modelStore.state.map { liteRtRunner.availability().toCardState() })
        }
    }

    /** The backend actually driving analysis right now: the user's explicit pick, or whichever wins automatically. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val aiCardState: Flow<AiCardState> = preferredBackend.flatMapLatest { pref ->
        val live = pref?.let(::aiCardStateFor) ?: autoAiCardState
        flow {
            emit(AiCardState.Checking)
            emitAll(live)
        }
    }

    fun setPreferredLlmBackend(backend: LlmBackend?) {
        settings.preferredLlmBackend = backend?.id
        preferredBackend.value = backend
        llmRunners.invalidate()
        pendingAnalysisWorker.requestDrain()
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

        appScope.launch {
            modelStore.state.collect(modelDownloadNotifier::update)
        }

        dailyBriefScheduler.scheduleNext()
    }

    /** Called from Activity.onStart — a capable backend may have appeared while backgrounded. */
    fun onAppForegrounded() {
        llmRunners.invalidate()
        pendingAnalysisWorker.requestDrain()
    }

    /** Exact alarms do not survive a reboot or an app update — [dev.andrej.echo.notify.BootReceiver] calls this. */
    suspend fun rescheduleNotifications() {
        derived.items.first().forEach { if (!it.done) notificationScheduler.schedule(it) }
    }

    /** [dev.andrej.echo.notify.DailyBriefReceiver] calls this after every fire, and [dev.andrej.echo.notify.BootReceiver] after a reboot. */
    fun rescheduleDailyBrief() {
        dailyBriefScheduler.scheduleNext()
    }

    /** Debug-only: fires each notification once, with fake data where the real path needs an alarm or a download in progress. */
    fun sendTestReminderNotification() {
        if (!BuildConfig.DEBUG) return
        val intent = Intent(applicationContext, ReminderAlarmReceiver::class.java).apply {
            data = Uri.parse("echo://item/test-reminder")
            putExtra(ReminderAlarmReceiver.EXTRA_ITEM_ID, "test-reminder")
            putExtra(ReminderAlarmReceiver.EXTRA_TRANSCRIPT_ID, "test-transcript")
            putExtra(ReminderAlarmReceiver.EXTRA_TEXT, "Call the landlord")
            putExtra(ReminderAlarmReceiver.EXTRA_DUE_AT, System.currentTimeMillis())
            putExtra(ReminderAlarmReceiver.EXTRA_HAS_TIME, true)
        }
        applicationContext.sendBroadcast(intent)
    }

    fun sendTestDailyBrief() {
        if (!BuildConfig.DEBUG) return
        applicationContext.sendBroadcast(Intent(applicationContext, DailyBriefReceiver::class.java))
    }

    fun sendTestModelDownloadNotification() {
        if (!BuildConfig.DEBUG) return
        modelDownloadNotifier.update(ModelState.Downloading(bytesDownloaded = 412_000_000L, totalBytes = 1_100_000_000L))
    }

    /** "Snooze" from the reminder notification — the same configurable-duration rule Home's overdue pill uses. */
    suspend fun snoozeReminder(itemId: String) {
        val item = derived.items.first().firstOrNull { it.id == itemId } ?: return
        derived.update(
            item.id,
            item.text,
            snoozeTime(System.currentTimeMillis(), settings.snoozeMinutes),
            hasTime = true,
            notify = item.notify,
        )
    }

    private var downloadJob: Job? = null

    fun downloadModel() {
        if (downloadJob?.isActive == true) return
        if (settings.wifiOnlyDownload && !onWifi()) return
        downloadJob = appScope.launch { modelStore.download() }
    }

    private fun onWifi(): Boolean {
        val connectivity = applicationContext.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
        return capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    fun deleteModel() {
        appScope.launch { modelStore.delete() }
    }

    fun storageUsedBytes(): Long =
        applicationContext.filesDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

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

            modelClass.isAssignableFrom(NotesViewModel::class.java) ->
                NotesViewModel(repository, analysisQueue, pendingAnalysisWorker, derived) as T

            modelClass.isAssignableFrom(AuthViewModel::class.java) ->
                AuthViewModel(auth) as T

            modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                HomeViewModel(repository, derived, analysisQueue, settings) as T

            modelClass.isAssignableFrom(TasksViewModel::class.java) ->
                TasksViewModel(derived, repository, aiCardState) as T

            modelClass.isAssignableFrom(ProfileViewModel::class.java) ->
                ProfileViewModel(repository, derived) as T

            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(
                    settings = settings,
                    engine = engine,
                    transcripts = repository,
                    derived = derived,
                    aiCardState = aiCardState,
                    onSetLlmBackend = ::setPreferredLlmBackend,
                    litertModelBytes = modelStore.spec.bytes,
                    onDownloadModel = ::downloadModel,
                    onCancelDownload = ::cancelDownload,
                    onDeleteModel = ::deleteModel,
                    storageUsedBytes = ::storageUsedBytes,
                    onTestReminderNotification = ::sendTestReminderNotification,
                    onTestDailyBriefNotification = ::sendTestDailyBrief,
                    onTestModelDownloadNotification = ::sendTestModelDownloadNotification,
                ) as T

            modelClass.isAssignableFrom(HistoryViewModel::class.java) ->
                HistoryViewModel(repository, derived) as T

            else -> error("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
