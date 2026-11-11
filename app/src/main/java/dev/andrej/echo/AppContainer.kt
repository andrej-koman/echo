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
import dev.andrej.echo.ai.ModelDownloadWorker
import dev.andrej.echo.ai.ModelSlot
import dev.andrej.echo.ai.ModelStore
import dev.andrej.echo.ai.PendingAnalysisWorker
import dev.andrej.echo.ai.NotesAiModel
import dev.andrej.echo.ai.NotesAiSelection
import dev.andrej.echo.ai.TranscriptAnalyzer
import dev.andrej.echo.ai.toCardState
import dev.andrej.echo.data.AnalysisQueueRepository
import dev.andrej.echo.data.DerivedRepository
import dev.andrej.echo.data.DictionaryRepository
import dev.andrej.echo.data.FolderRepository
import dev.andrej.echo.data.SettingsStore
import dev.andrej.echo.data.ThemeMode
import dev.andrej.echo.data.StorageUsage
import dev.andrej.echo.data.scanStorage
import dev.andrej.echo.data.SharedPreferencesSettingsStore
import dev.andrej.echo.data.SqlAnalysisQueueRepository
import dev.andrej.echo.data.SqlDerivedRepository
import dev.andrej.echo.data.SqlTranscriptRepository
import dev.andrej.echo.data.TranscriptRepository
import android.content.Intent
import android.net.Uri
import dev.andrej.echo.data.Backup
import dev.andrej.echo.data.BackupException
import dev.andrej.echo.data.DATABASE_NAME
import dev.andrej.echo.data.db.EchoDatabase
import dev.andrej.echo.data.echoDriver
import dev.andrej.echo.data.schemaVersionOf
import kotlinx.coroutines.withContext
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
import dev.andrej.echo.speech.whisper.WHISPER_VAD
import dev.andrej.echo.speech.whisper.SpeechModels
import dev.andrej.echo.transcribe.Transcriptions
import dev.andrej.echo.ui.folders.FoldersViewModel
import dev.andrej.echo.ui.profile.DictionaryViewModel
import dev.andrej.echo.ui.profile.HistoryViewModel
import dev.andrej.echo.ui.profile.SettingsViewModel
import dev.andrej.echo.ui.record.RecordViewModel
import dev.andrej.echo.ui.tasks.TasksViewModel
import dev.andrej.echo.ui.notes.NotesViewModel
import dev.andrej.echo.ui.onboarding.OnboardingViewModel
import dev.andrej.echo.ui.onboarding.SetupModelStates
import dev.andrej.echo.ui.onboarding.SetupStatus
import dev.andrej.echo.ui.onboarding.SetupSummary
import dev.andrej.echo.ui.onboarding.SetupWork
import dev.andrej.echo.ui.onboarding.buildSetupSummary
import dev.andrej.echo.ui.onboarding.computeSetupStatus
import dev.andrej.echo.ui.profile.buildLanguageOptions
import dev.andrej.echo.speech.whisper.WhisperModel
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalCoroutinesApi::class)
class AppContainer(context: Context) {

    private val applicationContext = context.applicationContext

    private val databaseFile = applicationContext.getDatabasePath(DATABASE_NAME)

    private val backup = Backup(
        filesDir = applicationContext.filesDir,
        databaseFile = databaseFile,
        schemaVersion = EchoDatabase.Schema.version,
        schemaVersionOf = ::schemaVersionOf,
    )

    private val driver = run {
        deleteJsonStore(applicationContext.filesDir)
        backup.applyStaged()
        echoDriver(applicationContext)
    }

    private val database = EchoDatabase(driver)

    val repository: TranscriptRepository = SqlTranscriptRepository(database)

    val settings: SettingsStore =
        SharedPreferencesSettingsStore(applicationContext)

    val billing: dev.andrej.echo.billing.Billing = if (BuildConfig.REVENUECAT_API_KEY.isBlank()) {
        dev.andrej.echo.billing.FakeBilling(settings)
    } else {
        dev.andrej.echo.billing.RevenueCatBilling(applicationContext, BuildConfig.REVENUECAT_API_KEY, settings)
    }

    private val _themeMode = MutableStateFlow(settings.themeMode)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(value: ThemeMode) {
        settings.themeMode = value
        _themeMode.value = value
    }

    /**
     * Application-scoped on purpose: leaving Capture must not cancel a half-finished
     * Engine.initialize(), which would throw away the wait and leak the partial engine. The same
     * reasoning covers model download/delete, which must survive leaving Settings.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val modelDownloadNotifier = ModelDownloadNotifier(applicationContext)

    val derived: DerivedRepository = SqlDerivedRepository(database)

    val taskReminders = dev.andrej.echo.notify.TaskReminders(
        derived,
        dev.andrej.echo.data.SqlTaskReminderRepository(database),
        dev.andrej.echo.notify.AndroidTaskReminderDelivery(applicationContext),
    )

    val analysisQueue: AnalysisQueueRepository = SqlAnalysisQueueRepository(database)

    val dictionary = DictionaryRepository(database)

    val folders = FolderRepository(database)

    private val modelStores = NotesAiModel.entries.associateWith { model ->
        ModelStore(
            directory = File(applicationContext.filesDir, "models"),
            spec = model.spec,
            downloader = HttpModelDownloader(),
        )
    }

    private val modelSelection = NotesAiSelection(settings, modelStores)
    private val downloadTarget = MutableStateFlow<NotesAiModel?>(null)

    private val activityManager = applicationContext.getSystemService(ActivityManager::class.java)

    private val modelSlot = ModelSlot()

    private val liteRtRunners = NotesAiModel.entries.associateWith { model ->
        LiteRtLlmRunner(
            store = modelStores.getValue(model),
            activityManager = activityManager,
            slot = modelSlot,
            modelId = model.id,
        )
    }

    private val speechModels = SpeechModels(
        directory = File(applicationContext.filesDir, "models"),
        totalMemoryBytes = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }.totalMem,
        settings = settings,
    )

    private val vadStore = ModelStore(File(applicationContext.filesDir, "models"), WHISPER_VAD, HttpModelDownloader())

    private val diarizer = Diarizer(
        segmentation = ModelStore(File(applicationContext.filesDir, "models"), SPEAKER_SEGMENTATION, HttpModelDownloader()),
        embedding = ModelStore(File(applicationContext.filesDir, "models"), SPEAKER_EMBEDDING, HttpModelDownloader()),
    )

    val analyzer = TranscriptAnalyzer(
        runners = { liteRtRunners.getValue(modelSelection.active.value) },
        segments = repository::segments,
    )

    val aiCardState: Flow<AiCardState> = flow {
        emit(AiCardState.Checking)
        emitAll(
            modelSelection.active.flatMapLatest { active ->
                    modelStores.getValue(active).state.map {
                        liteRtRunners.getValue(active).availability().toCardState()
                    }
                },
        )
    }

    val notesAiModelStates = combine(
        modelSelection.active,
        combine(NotesAiModel.entries.map { modelStores.getValue(it).state }) { states -> states.toList() },
    ) { _, states -> modelSelection.statuses(states) }

    private val modelDownloadState = downloadTarget.flatMapLatest { model ->
        if (model == null) flowOf(null)
        else modelStores.getValue(model).state.map { model to it }
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
        speechModels = speechModels,
        vadStore = vadStore,
        settings = settings,
        dictionary = dictionary,
        analysisQueue = analysisQueue,
        analysis = pendingAnalysisWorker,
        modelSlot = modelSlot,
        diarizer = diarizer,
        scope = appScope,
        isPro = billing.isPro,
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

    private val workManager = WorkManager.getInstance(applicationContext)
    /**
     * Onboarding's download button. [requireWifi] queues the job until an unmetered network
     * exists; the mobile-data button replaces any such waiting job. Neither touches
     * [SettingsStore.wifiOnlyDownload], which stays the user's own preference.
     */
    fun startSetupDownloads(requireWifi: Boolean) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (requireWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork(
            ModelDownloadWorker.WORK_NAME,
            if (requireWifi) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /** The worker's body. True once every model the current settings need is on disk. */
    suspend fun downloadSetupModels(): Boolean {
        speechModels.store().download()
        vadStore.download()
        if (settings.detectSpeakers) diarizer.download {}
        downloadModelNow(modelSelection.active.value)
        return setupStores().all { it.state.value is ModelState.Ready }
    }

    private fun setupStores(): List<ModelStore> = buildList {
        add(speechModels.store())
        add(vadStore)
        if (settings.detectSpeakers) addAll(diarizer.stores)
        add(modelStores.getValue(modelSelection.active.value))
    }

    val setupStatus: Flow<SetupStatus> =
        workManager.getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.WORK_NAME).flatMapLatest { infos ->
            val work = when (infos.lastOrNull()?.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> SetupWork.Queued
                WorkInfo.State.RUNNING -> SetupWork.Running
                WorkInfo.State.FAILED -> SetupWork.Failed
                else -> SetupWork.None
            }
            combine(setupStores().map { it.state }) { states -> computeSetupStatus(states.toList(), work) }
        }

    private fun setupSummary(speech: WhisperModel, detectSpeakers: Boolean): Flow<SetupSummary> {
        val notes = modelSelection.active.value
        val flows = listOf(
            speechModels.store(speech).state,
            vadStore.state,
            notes.let { modelStores.getValue(it).state },
        ) + diarizer.stores.map { it.state }
        return combine(flows) { states ->
            buildSetupSummary(
                speech = speech,
                vadBytes = WHISPER_VAD.bytes,
                speakerBytes = diarizer.stores.sumOf { it.spec.bytes },
                notesAi = notes,
                detectSpeakers = detectSpeakers,
                states = SetupModelStates(states[0], states[1], states.drop(3), states[2]),
                freeBytes = applicationContext.filesDir.usableSpace,
            )
        }
    }

    suspend fun deleteAllData() {
        transcriptions.discardAll()
        pendingAnalysisWorker.cancelAllAndAwait()
        repository.clear()
        analysisQueue.clear()
        derived.clear()
    }

    suspend fun saveBackup(uri: Uri) = withContext(Dispatchers.IO) {
        val out = applicationContext.contentResolver.openOutputStream(uri, "wt")
            ?: throw BackupException("Couldn't open that file.")
        out.use {
            backup.write(it) { dir ->
                // An open write transaction stops any commit or checkpoint while the files are copied.
                database.transaction {
                    databaseFile.copyTo(File(dir, databaseFile.name))
                    File(databaseFile.path + "-wal").takeIf { wal -> wal.isFile }
                        ?.copyTo(File(dir, databaseFile.name + "-wal"))
                }
            }
        }
    }

    suspend fun restoreBackup(uri: Uri) {
        if (recordingController.state.value.isRecording) throw BackupException("Stop the recording first.")
        withContext(Dispatchers.IO) {
            val input = applicationContext.contentResolver.openInputStream(uri)
                ?: throw BackupException("Couldn't open that file.")
            input.use { backup.stage(it) }
        }
        transcriptions.discardAll()
        pendingAnalysisWorker.cancelAllAndAwait()
        applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)?.let {
            applicationContext.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        Runtime.getRuntime().exit(0)
    }

    fun recordingNotification() = recordingNotifier.notification(recordingController.state.value.mode())

    init {
        appScope.launch {
            taskReminders.reconcile(refreshDates = true, force = true)
            derived.items.distinctUntilChanged().collect { taskReminders.reconcile() }
        }
        appScope.launch {
            modelDownloadState.collect { target ->
                if (target == null) modelDownloadNotifier.update(null, null, null)
                else modelDownloadNotifier.update(target.first.id, target.first.title, target.second)
            }
        }

        modelStores.forEach { (model, store) ->
            appScope.launch {
                store.state.map { it::class }.distinctUntilChanged().collect {
                    modelSelection.modelStateChanged(model)
                    pendingAnalysisWorker.requestDrain()
                }
            }
        }

        appScope.launch {
            modelSelection.active.drop(1).collect {
                pendingAnalysisWorker.requestDrain()
            }
        }

        appScope.launch {
            recordingController.state.map { it.mode() }.distinctUntilChanged().collect(recordingNotifier::update)
        }

        recordingNotifier.postIdle()
        transcriptions.recoverInterruptedRecordings()
        transcriptions.observeUnlocks()
    }

    /** Called from Activity.onStart — a capable backend may have appeared while backgrounded. */
    fun onAppForegrounded() {
        pendingAnalysisWorker.requestDrain()
        appScope.launch { taskReminders.reconcile(refreshDates = true, force = true) }
    }

    /** [dev.andrej.echo.notify.BootReceiver] calls this — the idle notification does not survive a reboot either. */
    fun repostRecordingNotification() {
        recordingNotifier.update(recordingController.state.value.mode())
    }

    /** Debug-only: shows the download notification with fake progress. */
    fun sendTestModelDownloadNotification() {
        if (!BuildConfig.DEBUG) return
        modelDownloadNotifier.update(
            NotesAiModel.Default.id,
            NotesAiModel.Default.title,
            ModelState.Downloading(bytesDownloaded = 412_000_000L, totalBytes = 1_100_000_000L),
        )
    }

    private var downloadJob: Job? = null

    fun downloadModel(model: NotesAiModel = modelSelection.active.value) {
        if (downloadJob?.isActive == true) return
        if (settings.wifiOnlyDownload && !onWifi()) return
        downloadJob = appScope.launch { downloadModelNow(model) }
    }

    /** Skips the Wi-Fi-only check: callers are an explicit user action or a job with its own constraint. */
    private suspend fun downloadModelNow(model: NotesAiModel) {
        modelSelection.startDownload(model)
        downloadTarget.value = model
        try {
            modelStores.getValue(model).download()
            modelSelection.modelStateChanged(model)
        } finally {
            modelSelection.downloadEnded(model)
            downloadTarget.value = null
        }
    }

    private fun onWifi(): Boolean {
        val connectivity = applicationContext.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
        return capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    fun cancelDownload(modelId: String? = null) {
        val target = downloadTarget.value ?: return
        if (modelId == null || target.id == modelId) downloadJob?.cancel()
    }

    fun deleteModel(modelId: String = modelSelection.active.value.id) {
        val disable = modelId == "none"
        val model = if (disable) modelSelection.active.value else NotesAiModel.fromId(modelId)
        appScope.launch {
            if (disable || downloadTarget.value == model) downloadJob?.cancelAndJoin()
            modelSlot.evictLlm(model.id)
            modelStores.getValue(model).delete()
            if (disable) modelSelection.disable() else modelSelection.modelRemoved(model)
            pendingAnalysisWorker.requestDrain()
        }
    }

    fun storageUsage(): StorageUsage = scanStorage(
        filesDir = applicationContext.filesDir,
        databasesDir = databaseFile.parentFile!!,
        preferencesDir = File(applicationContext.filesDir.parentFile, "shared_prefs"),
        cacheDir = applicationContext.cacheDir,
        noBackupDir = applicationContext.noBackupFilesDir,
    )

    val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
            modelClass.isAssignableFrom(RecordViewModel::class.java) ->
                RecordViewModel(
                    controller = recordingController,
                    isPro = billing.isPro,
                    onStarted = { RecordingService.start(applicationContext) },
                    warmup = ::warmUpLlm,
                ) as T

            modelClass.isAssignableFrom(NotesViewModel::class.java) ->
                NotesViewModel(
                    repository, analysisQueue, pendingAnalysisWorker, derived,
                    transcription = transcriptions.status,
                    live = transcriptions.live,
                    speechModel = { speechModels.current.name },
                    onImport = transcriptions::offer,
                    offered = transcriptions.offered,
                    importStarted = transcriptions.started,
                    onAcceptImport = transcriptions::accept,
                    onDeclineImport = transcriptions::decline,
                    importDay = { settings.importDay },
                    onRetryTranscription = transcriptions::transcribe,
                    onStopTranscription = transcriptions::stop,
                    onDeleted = transcriptions::discard,
                    audioFile = transcriptions::audioFile,
                    folders = folders.folders,
                ) as T

            modelClass.isAssignableFrom(OnboardingViewModel::class.java) ->
                OnboardingViewModel(
                    settings = settings,
                    speechChoices = speechModels.choices,
                    defaultSpeech = speechModels.default,
                    currentSpeech = speechModels.current,
                    speechReady = { speechModels.store(it).state.value is ModelState.Ready },
                    summaryFor = ::setupSummary,
                    setupStatus = setupStatus,
                    onStartDownloads = ::startSetupDownloads,
                    onFinish = ::finishOnboarding,
                    languageOptions = { buildLanguageOptions(WhisperNative.languages().toList()) },
                ) as T

            modelClass.isAssignableFrom(FoldersViewModel::class.java) ->
                FoldersViewModel(folders, repository) as T

            modelClass.isAssignableFrom(TasksViewModel::class.java) ->
                TasksViewModel(derived, repository, aiCardState) as T

            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(
                    settings = settings,
                    whisperLanguages = { WhisperNative.languages().toList() },
                    speechModels = speechModels.choices,
                    currentSpeechModel = { speechModels.current },
                    transcripts = repository,
                    derived = derived,
                    aiCardState = aiCardState,
                    notesAiModelStates = notesAiModelStates,
                    litertModelBytes = modelSelection.active.value.spec.bytes,
                    onDownloadModel = ::downloadModel,
                    onCancelDownload = ::cancelDownload,
                    onDeleteModel = ::deleteModel,
                    onSelectNotesAiModel = { modelSelection.choose(it) },
                    storageUsage = ::storageUsage,
                    onTestModelDownloadNotification = ::sendTestModelDownloadNotification,
                    onDeleteAllData = ::deleteAllData,
                    onReplayOnboarding = ::replayOnboarding,
                    onSaveBackup = ::saveBackup,
                    onRestoreBackup = ::restoreBackup,
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
