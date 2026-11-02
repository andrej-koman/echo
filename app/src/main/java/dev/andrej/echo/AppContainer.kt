package dev.andrej.echo

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.andrej.echo.ai.AiCardState
import dev.andrej.echo.ai.HttpModelDownloader
import dev.andrej.echo.ai.LiteRtLlmRunner
import dev.andrej.echo.ai.LlmRunnerProvider
import dev.andrej.echo.ai.ModelSlot
import dev.andrej.echo.ai.ModelStore
import dev.andrej.echo.ai.PendingAnalysisWorker
import dev.andrej.echo.ai.QWEN3_1_7B_INT4
import dev.andrej.echo.ai.TranscriptAnalyzer
import dev.andrej.echo.ai.toCardState
import dev.andrej.echo.data.AnalysisQueueRepository
import dev.andrej.echo.data.DerivedRepository
import dev.andrej.echo.data.DictionaryRepository
import dev.andrej.echo.data.FolderRepository
import dev.andrej.echo.data.SettingsStore
import dev.andrej.echo.data.SharedPreferencesSettingsStore
import dev.andrej.echo.data.SqlAnalysisQueueRepository
import dev.andrej.echo.data.SqlDerivedRepository
import dev.andrej.echo.data.SqlTranscriptRepository
import dev.andrej.echo.data.TranscriptRepository
import dev.andrej.echo.data.echoDatabase
import dev.andrej.echo.ai.ModelState
import dev.andrej.echo.notify.ModelDownloadNotifier
import dev.andrej.echo.notify.RecordingNotifier
import dev.andrej.echo.notify.mode
import dev.andrej.echo.speech.AudioFocus
import dev.andrej.echo.speech.AudioRecorder
import dev.andrej.echo.speech.speakers.Diarizer
import dev.andrej.echo.speech.speakers.SPEAKER_EMBEDDING
import dev.andrej.echo.speech.speakers.SPEAKER_SEGMENTATION
import dev.andrej.echo.speech.whisper.WhisperNative
import dev.andrej.echo.speech.whisper.whisperModelFor
import dev.andrej.echo.transcribe.Transcriptions
import dev.andrej.echo.ui.folders.FoldersViewModel
import dev.andrej.echo.ui.profile.DictionaryViewModel
import dev.andrej.echo.ui.profile.HistoryViewModel
import dev.andrej.echo.ui.profile.ProfileViewModel
import dev.andrej.echo.ui.profile.SettingsViewModel
import dev.andrej.echo.ui.record.RecordViewModel
import dev.andrej.echo.ui.tasks.TasksViewModel
import dev.andrej.echo.ui.notes.NotesViewModel
import dev.andrej.echo.ui.onboarding.OnboardingViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class AppContainer(context: Context) {

    private val applicationContext = context.applicationContext

    private val database = run {
        deleteJsonStore(applicationContext.filesDir)
        echoDatabase(applicationContext)
    }

    val repository: TranscriptRepository = SqlTranscriptRepository(database)

    val settings: SettingsStore =
        SharedPreferencesSettingsStore(applicationContext)

    /**
     * Application-scoped on purpose: leaving Capture must not cancel a half-finished
     * Engine.initialize(), which would throw away the wait and leak the partial engine. The same
     * reasoning covers model download/delete, which must survive leaving Settings.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val modelDownloadNotifier = ModelDownloadNotifier(applicationContext)

    val derived: DerivedRepository = SqlDerivedRepository(database)

    val analysisQueue: AnalysisQueueRepository = SqlAnalysisQueueRepository(database)

    val dictionary = DictionaryRepository(database)

    val folders = FolderRepository(database)

    val modelStore = ModelStore(
        directory = File(applicationContext.filesDir, "models"),
        spec = QWEN3_1_7B_INT4,
        downloader = HttpModelDownloader(),
    )

    private val activityManager = applicationContext.getSystemService(ActivityManager::class.java)

    private val modelSlot = ModelSlot()

    private val liteRtRunner = LiteRtLlmRunner(
        store = modelStore,
        activityManager = activityManager,
        slot = modelSlot,
    )

    private val whisperModel = whisperModelFor(
        ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }.totalMem,
    )

    private val whisperStore = ModelStore(
        directory = File(applicationContext.filesDir, "models"),
        spec = whisperModel.spec,
        downloader = HttpModelDownloader(),
    )

    private val diarizer = Diarizer(
        segmentation = ModelStore(File(applicationContext.filesDir, "models"), SPEAKER_SEGMENTATION, HttpModelDownloader()),
        embedding = ModelStore(File(applicationContext.filesDir, "models"), SPEAKER_EMBEDDING, HttpModelDownloader()),
    )

    private val llmRunners = LlmRunnerProvider(listOf(liteRtRunner))

    val analyzer = TranscriptAnalyzer(llmRunners::runner, repository::segments)

    val aiCardState: Flow<AiCardState> = flow {
        emit(AiCardState.Checking)
        emitAll(modelStore.state.map { liteRtRunner.availability().toCardState() })
    }

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

    val transcriptions = Transcriptions(
        context = applicationContext,
        repository = repository,
        modelStore = whisperStore,
        settings = settings,
        dictionary = dictionary,
        analysisQueue = analysisQueue,
        analysis = pendingAnalysisWorker,
        modelSlot = modelSlot,
        diarizer = diarizer,
        scope = appScope,
    )

    val recordingController = RecordingController(
        recorder = AudioRecorder(),
        interruptions = AudioFocus(applicationContext),
        repository = repository,
        audioFile = transcriptions::recordingFile,
        onRecorded = transcriptions::transcribe,
        onDiscarded = transcriptions::discard,
        scope = appScope,
    )

    private val recordingNotifier = RecordingNotifier(applicationContext)

    private val _onboardingDone = MutableStateFlow(
        settings.onboardingDone || runBlocking { repository.transcripts.first() }.isNotEmpty(),
    )
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    fun finishOnboarding() {
        settings.onboardingDone = true
        _onboardingDone.value = true
    }

    /** Debug-only. Data is kept, so a cold start still skips onboarding because notes exist. */
    fun replayOnboarding() {
        if (!BuildConfig.DEBUG) return
        settings.onboardingDone = false
        _onboardingDone.value = false
    }

    /** Both models start on Wi-Fi only; on mobile data the first recording fetches Whisper. */
    fun startOnboardingDownloads() {
        if (!onWifi()) return
        appScope.launch {
            whisperStore.download()
            if (settings.detectSpeakers) diarizer.download {}
        }
        appScope.launch {
            if (llmNeedsDownload.first()) downloadModel()
        }
    }

    val llmNeedsDownload: Flow<Boolean> =
        aiCardState.filter { it != AiCardState.Checking }.map { it is AiCardState.NeedsDownload }

    suspend fun deleteAllData() {
        transcriptions.discardAll()
        analysisQueue.clear()
        derived.clear()
        repository.clear()
    }

    fun recordingNotification() = recordingNotifier.notification(recordingController.state.value.mode())

    init {
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

        appScope.launch {
            recordingController.state.map { it.mode() }.distinctUntilChanged().collect(recordingNotifier::update)
        }

        recordingNotifier.postIdle()
        transcriptions.recoverInterruptedRecordings()
    }

    /** Called from Activity.onStart — a capable backend may have appeared while backgrounded. */
    fun onAppForegrounded() {
        llmRunners.invalidate()
        pendingAnalysisWorker.requestDrain()
    }

    /** [dev.andrej.echo.notify.BootReceiver] calls this — the idle notification does not survive a reboot either. */
    fun repostRecordingNotification() {
        recordingNotifier.update(recordingController.state.value.mode())
    }

    /** Debug-only: shows the download notification with fake progress. */
    fun sendTestModelDownloadNotification() {
        if (!BuildConfig.DEBUG) return
        modelDownloadNotifier.update(ModelState.Downloading(bytesDownloaded = 412_000_000L, totalBytes = 1_100_000_000L))
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
                    controller = recordingController,
                    onStarted = { RecordingService.start(applicationContext) },
                    warmup = ::warmUpLlm,
                ) as T

            modelClass.isAssignableFrom(NotesViewModel::class.java) ->
                NotesViewModel(
                    repository, analysisQueue, pendingAnalysisWorker, derived,
                    transcription = transcriptions.status,
                    onImport = transcriptions::import,
                    onRetryTranscription = transcriptions::transcribe,
                    onDeleted = transcriptions::discard,
                    audioFile = transcriptions::audioFile,
                    folders = folders.folders,
                ) as T

            modelClass.isAssignableFrom(OnboardingViewModel::class.java) ->
                OnboardingViewModel(
                    needsDownload = llmNeedsDownload,
                    onStartDownloads = ::startOnboardingDownloads,
                    onDownloadNow = ::downloadModel,
                    onFinish = ::finishOnboarding,
                ) as T

            modelClass.isAssignableFrom(FoldersViewModel::class.java) ->
                FoldersViewModel(folders, repository) as T

            modelClass.isAssignableFrom(TasksViewModel::class.java) ->
                TasksViewModel(derived, repository, aiCardState) as T

            modelClass.isAssignableFrom(ProfileViewModel::class.java) ->
                ProfileViewModel(repository, derived) as T

            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(
                    settings = settings,
                    whisperLanguages = { WhisperNative.languages().toList() },
                    transcripts = repository,
                    derived = derived,
                    aiCardState = aiCardState,
                    litertModelBytes = modelStore.spec.bytes,
                    onDownloadModel = ::downloadModel,
                    onCancelDownload = ::cancelDownload,
                    onDeleteModel = ::deleteModel,
                    storageUsedBytes = ::storageUsedBytes,
                    onTestModelDownloadNotification = ::sendTestModelDownloadNotification,
                    onDeleteAllData = ::deleteAllData,
                    onReplayOnboarding = ::replayOnboarding,
                ) as T

            modelClass.isAssignableFrom(DictionaryViewModel::class.java) ->
                DictionaryViewModel(dictionary) as T

            modelClass.isAssignableFrom(HistoryViewModel::class.java) ->
                HistoryViewModel(repository, derived) as T

            else -> error("Unknown ViewModel: ${modelClass.name}")
        }
    }
}

/**
 * The JSON files that came before the database are not migrated (they hold no audio). Imported
 * audio under `recordings/` belonged to them, so it goes too.
 */
private fun deleteJsonStore(filesDir: File) {
    val transcripts = File(filesDir, "transcripts.json")
    if (!transcripts.exists()) return
    File(filesDir, "recordings").deleteRecursively()
    listOf("derived.json", "pending_analysis.json").forEach { File(filesDir, it).delete() }
    transcripts.delete()
}
