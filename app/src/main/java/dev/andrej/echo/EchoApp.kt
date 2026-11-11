package dev.andrej.echo

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import dev.andrej.echo.billing.Billing
import dev.andrej.echo.ui.billing.PaywallScreen
import dev.andrej.echo.ui.billing.PaywallViewModel
import dev.andrej.echo.ui.billing.UnlockNote
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import dev.andrej.echo.ui.components.LocalBarCenter
import dev.andrej.echo.ui.components.LocalBottomBarHeight
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.andrej.echo.ai.AiCardState
import dev.andrej.echo.data.ThemeMode
import dev.andrej.echo.ui.capture.RecordingRow
import dev.andrej.echo.ui.capture.TAG_RECORD_BUTTON
import dev.andrej.echo.ui.record.RecordUiState
import dev.andrej.echo.ui.components.RecordButton
import dev.andrej.echo.ui.components.RecordButtonSize
import dev.andrej.echo.ui.components.RecordButtonState
import dev.andrej.echo.ui.components.LocalOpenSheets
import dev.andrej.echo.ui.components.TabBar
import dev.andrej.echo.ui.components.TabItem
import dev.andrej.echo.ui.detail.NoteDetailScreen
import dev.andrej.echo.ui.folders.FolderScreen
import dev.andrej.echo.ui.folders.FoldersScreen
import dev.andrej.echo.ui.folders.FoldersViewModel
import dev.andrej.echo.ui.notes.NotesScreen
import dev.andrej.echo.ui.notes.NotesViewModel
import dev.andrej.echo.ui.onboarding.OnboardingScreen
import dev.andrej.echo.ui.onboarding.OnboardingViewModel
import dev.andrej.echo.ui.profile.DictionaryScreen
import dev.andrej.echo.ui.profile.DictionaryViewModel
import dev.andrej.echo.ui.profile.HistoryScreen
import dev.andrej.echo.ui.profile.HistoryViewModel
import dev.andrej.echo.ui.profile.SettingsScreen
import dev.andrej.echo.ui.profile.SettingsViewModel
import dev.andrej.echo.ui.profile.StorageScreen
import dev.andrej.echo.ui.record.RecordViewModel
import dev.andrej.echo.ui.tasks.EditTaskScreen
import dev.andrej.echo.ui.tasks.TasksViewModel
import dev.andrej.echo.ui.theme.EchoTheme
import kotlinx.coroutines.flow.StateFlow

private object Routes {
    const val PAYWALL = "paywall?note={note}"
    const val PAYWALL_BASE = "paywall"
    const val NOTES = "notes"
    const val FOLDERS = "folders"
    const val CALENDAR = "calendar"
    const val SETTINGS = "settings"
    const val STORAGE = "storage"
    const val DICTIONARY = "dictionary"
    const val DETAIL = "detail/{id}?tab={tab}"
    const val FOLDER = "folders/{id}"
    const val EDIT_TASK = "tasks/{id}/edit"

    fun paywall(note: String? = null) = if (note == null) PAYWALL_BASE else "$PAYWALL_BASE?note=$note"
    fun detail(id: String, tasks: Boolean = false) = if (tasks) "detail/$id?tab=tasks" else "detail/$id"
    fun folder(id: String) = "folders/$id"
    fun editTask(id: String) = "tasks/$id/edit"
}

private val Tabs = listOf(
    TabItem(Routes.NOTES, "Notes", R.drawable.ic_home),
    TabItem(Routes.FOLDERS, "Folders", R.drawable.ic_folder),
    TabItem(Routes.CALENDAR, "Calendar", R.drawable.ic_calendar),
    TabItem(Routes.SETTINGS, "Settings", R.drawable.ic_settings),
)

@Composable
fun EchoApp(
    viewModelFactory: ViewModelProvider.Factory,
    billing: Billing,
    onboardingDone: StateFlow<Boolean>,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    pendingOpenCapture: Boolean = false,
    onOpenCaptureHandled: () -> Unit = {},
    pendingOpenTask: String? = null,
    onOpenTaskHandled: () -> Unit = {},
    pendingOpenNote: OpenNote? = null,
    onOpenNoteHandled: () -> Unit = {},
    pendingOpenCalendar: Boolean = false,
    onOpenCalendarHandled: () -> Unit = {},
) {
    val done by onboardingDone.collectAsStateWithLifecycle()
    var captureAfterOnboarding by remember { mutableStateOf(false) }
    var importAfterOnboarding by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EchoTheme.colors.surfacePage),
    ) {
        if (!done) {
            val onboardingViewModel: OnboardingViewModel = viewModel(factory = viewModelFactory)
            OnboardingScreen(
                viewModel = onboardingViewModel,
                onRecordFirstNote = { captureAfterOnboarding = true },
                onImportFirst = { importAfterOnboarding = true },
            )
        } else {
            MainApp(
                viewModelFactory = viewModelFactory,
                billing = billing,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                pendingOpenCapture = pendingOpenCapture || captureAfterOnboarding,
                pendingOpenImport = importAfterOnboarding,
                onOpenImportHandled = { importAfterOnboarding = false },
                pendingOpenTask = pendingOpenTask,
                onOpenTaskHandled = onOpenTaskHandled,
                pendingOpenNote = pendingOpenNote,
                onOpenNoteHandled = onOpenNoteHandled,
                pendingOpenCalendar = pendingOpenCalendar,
                onOpenCalendarHandled = onOpenCalendarHandled,
                onOpenCaptureHandled = {
                    captureAfterOnboarding = false
                    onOpenCaptureHandled()
                },
            )
        }
    }
}

@Composable
private fun MainApp(
    viewModelFactory: ViewModelProvider.Factory,
    billing: Billing,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    pendingOpenCapture: Boolean = false,
    onOpenCaptureHandled: () -> Unit = {},
    pendingOpenImport: Boolean = false,
    onOpenImportHandled: () -> Unit = {},
    pendingOpenTask: String? = null,
    onOpenTaskHandled: () -> Unit = {},
    pendingOpenNote: OpenNote? = null,
    onOpenNoteHandled: () -> Unit = {},
    pendingOpenCalendar: Boolean = false,
    onOpenCalendarHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route

    val isPro by billing.isPro.collectAsStateWithLifecycle()
    val billingFactory = remember(billing) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PaywallViewModel(billing) as T
        }
    }
    val paywallViewModel: PaywallViewModel = viewModel(factory = billingFactory)
    val billingState by paywallViewModel.state.collectAsStateWithLifecycle()
    val activePlan by billing.activePlan.collectAsStateWithLifecycle()
    val renewsAt by billing.renewsAt.collectAsStateWithLifecycle()
    val onUpgrade = { note: String? -> navController.navigate(Routes.paywall(note)) { launchSingleTop = true } }

    val onTabs = route in Tabs.map { it.route }
    val onNote = route == Routes.DETAIL
    // Keep the source tab selected while a note or another child destination is open.
    var selectedTab by rememberSaveable { mutableStateOf(Routes.NOTES) }
    LaunchedEffect(route) {
        if (route in Tabs.map { it.route }) selectedTab = route.orEmpty()
    }
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(0.dp) }

    val recordViewModel: RecordViewModel = viewModel(factory = viewModelFactory)
    val recording by recordViewModel.uiState.collectAsStateWithLifecycle()
    val upgradeRequested by recordViewModel.upgradeRequested.collectAsStateWithLifecycle()
    LaunchedEffect(upgradeRequested) {
        if (upgradeRequested) {
            onUpgrade(null)
            recordViewModel.consumeUpgrade()
        }
    }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true) recordViewModel.startRecording()
    }
    val startRecording = {
        if (!recording.isRecording) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                recordViewModel.startRecording()
            } else {
                permissionLauncher.launch(
                    if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
                    else arrayOf(Manifest.permission.RECORD_AUDIO),
                )
            }
        }
    }
    var recordingRowHeight by remember { mutableStateOf(0.dp) }

    val foldersViewModel: FoldersViewModel = viewModel(factory = viewModelFactory)
    val notesViewModel: NotesViewModel = viewModel(factory = viewModelFactory)

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            notesViewModel.import(uris)
            navController.selectTab(Routes.NOTES)
        }
    }
    val openImport = { importPicker.launch(arrayOf("audio/*", "video/*")) }
    // A file shared from another app lands on Notes, where its import sheet is.
    val offeredImport by notesViewModel.offeredImport.collectAsStateWithLifecycle()
    LaunchedEffect(offeredImport != null) {
        if (offeredImport != null && route != Routes.NOTES) navController.selectTab(Routes.NOTES)
    }
    LaunchedEffect(Unit) {
        notesViewModel.importStarted.collect { id -> navController.navigate(Routes.detail(id)) }
    }

    LaunchedEffect(pendingOpenImport) {
        if (pendingOpenImport) {
            openImport()
            onOpenImportHandled()
        }
    }

    LaunchedEffect(pendingOpenCapture) {
        if (pendingOpenCapture) {
            startRecording()
            onOpenCaptureHandled()
        }
    }

    LaunchedEffect(pendingOpenTask) {
        pendingOpenTask?.let {
            navController.navigate(Routes.editTask(it)) { launchSingleTop = true }
            onOpenTaskHandled()
        }
    }

    LaunchedEffect(pendingOpenNote) {
        pendingOpenNote?.let {
            navController.navigate(Routes.detail(it.id, it.tasks)) { launchSingleTop = true }
            onOpenNoteHandled()
        }
    }

    LaunchedEffect(pendingOpenCalendar) {
        if (pendingOpenCalendar) {
            navController.selectTab(Routes.CALENDAR)
            onOpenCalendarHandled()
        }
    }

    val openSheets = remember { mutableIntStateOf(0) }
    val barCenter = remember { mutableStateOf<(@Composable () -> Unit)?>(null) }

    CompositionLocalProvider(LocalOpenSheets provides openSheets, LocalBarCenter provides barCenter) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Routes.NOTES,
                modifier = Modifier.fillMaxSize().padding(
                    bottom = when {
                        !recording.isRecording -> 0.dp
                        onTabs || onNote -> recordingRowHeight
                        else -> barHeight
                    },
                ),
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                composable(
                    Routes.PAYWALL,
                    arguments = listOf(navArgument("note") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { entry ->
                    val noteArg = entry.arguments?.getString("note")
                    val transcripts by notesViewModel.transcripts.collectAsStateWithLifecycle()
                    val statuses by notesViewModel.transcription.collectAsStateWithLifecycle()
                    // Captured while the note is still capped: Pro clears the cap the moment it activates.
                    var captured by remember(entry) { mutableStateOf<UnlockNote?>(null) }
                    LaunchedEffect(transcripts, isPro) {
                        if (captured == null && !isPro) {
                            val capped = transcripts.filter { it.transcribedUntilMs != null }
                            (capped.firstOrNull { it.id == noteArg } ?: capped.maxByOrNull { it.createdAt })?.let {
                                captured = UnlockNote(it.id, it.title?.takeIf(String::isNotBlank) ?: "Voice note", it.transcribedUntilMs!!, it.durationMs)
                            }
                        }
                    }
                    PaywallScreen(
                        viewModel = paywallViewModel,
                        onBack = { navController.popBackStack() },
                        unlockNote = captured?.copy(status = statuses[captured?.id]),
                        renewsAt = renewsAt,
                        onOpenNote = { id ->
                            val previous = navController.previousBackStackEntry
                            if (previous?.destination?.route == Routes.DETAIL && previous.arguments?.getString("id") == id) {
                                navController.popBackStack()
                            } else {
                                navController.navigate(Routes.detail(id)) { popUpTo(Routes.PAYWALL) { inclusive = true } }
                            }
                        },
                    )
                }

                composable(Routes.NOTES) {
                    NotesScreen(
                        viewModel = notesViewModel,
                        onOpen = { navController.navigate(Routes.detail(it)) },
                        onImport = openImport,
                        isPro = isPro,
                    )
                }

                composable(Routes.FOLDERS) {
                    FoldersScreen(
                        viewModel = foldersViewModel,
                        onOpenAllNotes = { notesViewModel.search(""); navController.selectTab(Routes.NOTES) },
                        onOpen = { navController.navigate(Routes.folder(it)) },
                    )
                }

                composable(Routes.FOLDER) { entry ->
                    FolderScreen(
                        folderId = entry.arguments?.getString("id").orEmpty(),
                        foldersViewModel = foldersViewModel,
                        notesViewModel = notesViewModel,
                        onOpen = { navController.navigate(Routes.detail(it)) },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Routes.CALENDAR) {
                    val historyViewModel: HistoryViewModel = viewModel(factory = viewModelFactory)
                    val historyState by historyViewModel.state.collectAsStateWithLifecycle()
                    HistoryScreen(
                        state = historyState,
                        onPrevious = historyViewModel::previous,
                        onNext = historyViewModel::next,
                        onToday = historyViewModel::goToday,
                        notesViewModel = notesViewModel,
                        onPickDay = historyViewModel::pick,
                        onOpen = { navController.navigate(Routes.detail(it)) },
                        onToggleTask = historyViewModel::toggleTask,
                        onEditTask = { navController.navigate(Routes.editTask(it)) },
                    )
                }

                composable(Routes.SETTINGS) {
                    val settingsViewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
                    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
                    val aiState by settingsViewModel.aiCardState.collectAsStateWithLifecycle(
                        initialValue = AiCardState.Checking,
                    )
                    SettingsScreen(
                        state = settingsState,
                        aiState = aiState,
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        onLanguageChange = settingsViewModel::setLanguage,
                        onSpeechModelChange = settingsViewModel::setSpeechModel,
                        onImportDayChange = settingsViewModel::setImportDay,
                        onAutoAnalyzeChange = settingsViewModel::setAutoAnalyze,
                        onDetectSpeakersChange = settingsViewModel::setDetectSpeakers,
                        onIdleNotificationChange = settingsViewModel::setIdleNotification,
                        onWifiOnlyDownloadChange = settingsViewModel::setWifiOnlyDownload,
                        onDeleteAllData = settingsViewModel::deleteAllData,
                        onDownloadModel = settingsViewModel.onDownloadModel,
                        onCancelDownload = settingsViewModel.onCancelDownload,
                        onDeleteModel = settingsViewModel.onDeleteModel,
                        onSelectNotesAiModel = { settingsViewModel.onSelectNotesAiModel(it) },
                        onTestModelDownloadNotification = settingsViewModel.onTestModelDownloadNotification,
                        onReplayOnboarding = settingsViewModel.onReplayOnboarding,
                        onOpenDictionary = { navController.navigate(Routes.DICTIONARY) },
                        onOpenStorage = { navController.navigate(Routes.STORAGE) },
                        onSaveBackup = settingsViewModel::saveBackup,
                        onRestoreBackup = settingsViewModel::restoreBackup,
                        onBackupStatusSeen = settingsViewModel::clearBackupStatus,
                        isPro = isPro,
                        activePlan = activePlan,
                        renewsAt = renewsAt,
                        onUpgrade = { onUpgrade(null) },
                        onManageSubscription = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?package=${context.packageName}&sku=echo_pro")))
                        },
                        onRestorePurchases = paywallViewModel::restore,
                        restoringPurchases = billingState.busy,
                        billingMessage = billingState.message,
                    )
                }

                composable(Routes.STORAGE) { entry ->
                    val settingsEntry = remember(entry) { navController.getBackStackEntry(Routes.SETTINGS) }
                    val settingsViewModel: SettingsViewModel = viewModel(
                        viewModelStoreOwner = settingsEntry,
                        factory = viewModelFactory,
                    )
                    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
                    LaunchedEffect(settingsViewModel) { settingsViewModel.refreshStorage() }
                    StorageScreen(
                        usage = settingsState.storage,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Routes.DICTIONARY) {
                    val dictionaryViewModel: DictionaryViewModel = viewModel(factory = viewModelFactory)
                    val terms by dictionaryViewModel.terms.collectAsStateWithLifecycle()
                    DictionaryScreen(
                        terms = terms,
                        onSave = dictionaryViewModel::save,
                        onDelete = dictionaryViewModel::delete,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    Routes.DETAIL,
                    arguments = listOf(navArgument("tab") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { entry ->
                    val notesViewModel: NotesViewModel = viewModel(factory = viewModelFactory)
                    CompositionLocalProvider(LocalBottomBarHeight provides (barHeight - if (recording.isRecording) recordingRowHeight else 0.dp).coerceAtLeast(0.dp)) {
                    NoteDetailScreen(
                        viewModel = notesViewModel,
                        noteId = entry.arguments?.getString("id").orEmpty(),
                        openTasks = entry.arguments?.getString("tab") == "tasks",
                        onDeleted = { navController.popBackStack() },
                        onBack = { navController.popBackStack() },
                        onEditTask = { navController.navigate(Routes.editTask(it)) },
                        recording = recording.isRecording,
                        isPro = isPro,
                        onUpgrade = { onUpgrade(entry.arguments?.getString("id")) },
                    )
                    }
                }

                composable(Routes.EDIT_TASK) { entry ->
                    val tasksViewModel: TasksViewModel = viewModel(factory = viewModelFactory)
                    EditTaskScreen(
                        viewModel = tasksViewModel,
                        taskId = entry.arguments?.getString("id").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onOpenSource = { navController.navigate(Routes.detail(it)) },
                    )
                }
            }

            if (route?.startsWith(Routes.PAYWALL_BASE) != true && (onTabs || onNote || recording.isRecording)) {
                AnimatedVisibility(
                    visible = openSheets.intValue == 0,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    BottomBar(
                        selected = selectedTab,
                        showRecord = !onNote || recording.isRecording,
                        recording = recording,
                        onRecord = { if (recording.isRecording) recordViewModel.stopRecording() else startRecording() },
                        onPause = recordViewModel::pause,
                        onResume = recordViewModel::resume,
                        onRecordingRowHeight = { recordingRowHeight = it },
                        onSelect = { tab -> if (tab != route) navController.selectTab(tab) },
                        modifier = Modifier.onSizeChanged { barHeight = with(density) { it.height.toDp() } },
                    )
                }
            }
        }
    }
}

/**
 * Tabs live directly above Notes, never stacked on each other. Saving and restoring tab state
 * keys the saved stack to Notes, so navigating there would land on the last tab instead.
 */
private fun NavHostController.selectTab(route: String) {
    if (route == Routes.NOTES) {
        popBackStack(Routes.NOTES, inclusive = false)
    } else {
        navigate(route) {
            popUpTo(Routes.NOTES)
            launchSingleTop = true
        }
    }
}

@Composable
private fun BottomBar(
    selected: String,
    showRecord: Boolean,
    recording: RecordUiState,
    onRecord: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRecordingRowHeight: (androidx.compose.ui.unit.Dp) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        Column(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
            if (recording.isRecording) {
                RecordingRow(
                    state = recording,
                    onPause = onPause,
                    onResume = onResume,
                    modifier = Modifier.onSizeChanged { onRecordingRowHeight(with(density) { it.height.toDp() }) },
                )
            }
            TabBar(
                items = Tabs,
                selected = selected,
                centerGap = true,
                onSelect = onSelect,
                hairline = showRecord,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EchoTheme.colors.surfaceBar)
                    .navigationBarsPadding(),
            )
        }

        val center = LocalBarCenter.current.value
        if (showRecord || center != null) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .offset(y = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(EchoTheme.colors.surfaceBar),
            )
            if (showRecord) {
                RecordButton(
                    state = if (recording.isRecording) RecordButtonState.Recording else RecordButtonState.Idle,
                    onClick = onRecord,
                    size = RecordButtonSize.Sm,
                    modifier = Modifier.testTag(TAG_RECORD_BUTTON),
                )
            } else {
                // Match the record button’s outer box to keep center controls at the same height;
                // the same box puts the slot's disc at the record button's height.
                Box(
                    modifier = Modifier.size(RecordButtonSize.Sm.diameter * 1.55f),
                    contentAlignment = Alignment.Center,
                ) { center?.invoke() }
            }
        }
        }
    }
}


