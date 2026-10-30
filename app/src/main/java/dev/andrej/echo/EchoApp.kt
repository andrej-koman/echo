package dev.andrej.echo

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.andrej.echo.ai.AiCardState
import dev.andrej.echo.ui.capture.CaptureScreen
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.RecordButton
import dev.andrej.echo.ui.components.RecordButtonSize
import dev.andrej.echo.ui.components.RecordButtonState
import dev.andrej.echo.ui.components.TabBar
import dev.andrej.echo.ui.components.TabItem
import dev.andrej.echo.ui.detail.NoteDetailScreen
import dev.andrej.echo.ui.home.HomeScreen
import dev.andrej.echo.ui.home.HomeViewModel
import dev.andrej.echo.ui.notes.NotesScreen
import dev.andrej.echo.ui.notes.NotesViewModel
import dev.andrej.echo.ui.onboarding.OnboardingScreen
import dev.andrej.echo.ui.onboarding.OnboardingViewModel
import dev.andrej.echo.ui.profile.HistoryScreen
import dev.andrej.echo.ui.profile.HistoryViewModel
import dev.andrej.echo.ui.profile.ProfileScreen
import dev.andrej.echo.ui.profile.ProfileViewModel
import dev.andrej.echo.ui.profile.SettingsScreen
import dev.andrej.echo.ui.profile.SettingsViewModel
import dev.andrej.echo.ui.record.RecordViewModel
import dev.andrej.echo.ui.tasks.EditTaskScreen
import dev.andrej.echo.ui.tasks.TasksScreen
import dev.andrej.echo.ui.tasks.TasksViewModel
import dev.andrej.echo.ui.theme.EchoTheme
import kotlinx.coroutines.flow.StateFlow

private object Routes {
    const val HOME = "home"
    const val TASKS = "tasks"
    const val NOTES = "notes"
    const val CAPTURE = "capture"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val DETAIL = "detail/{id}"
    const val EDIT_TASK = "tasks/{id}/edit"

    fun detail(id: String) = "detail/$id"
    fun editTask(id: String) = "tasks/$id/edit"
}

private val Tabs = listOf(
    TabItem(Routes.HOME, "Home", R.drawable.ic_home),
    TabItem(Routes.TASKS, "Tasks", R.drawable.ic_list_checks),
    TabItem(Routes.NOTES, "Notes", R.drawable.ic_file_text),
    TabItem(Routes.PROFILE, "Profile", R.drawable.ic_user),
)

@Composable
fun EchoApp(
    viewModelFactory: ViewModelProvider.Factory,
    onboardingDone: StateFlow<Boolean>,
    modifier: Modifier = Modifier,
    pendingOpenCapture: Boolean = false,
    onOpenCaptureHandled: () -> Unit = {},
) {
    val done by onboardingDone.collectAsStateWithLifecycle()
    var captureAfterOnboarding by remember { mutableStateOf(false) }

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
            )
        } else {
            MainApp(
                viewModelFactory = viewModelFactory,
                pendingOpenCapture = pendingOpenCapture || captureAfterOnboarding,
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
    pendingOpenCapture: Boolean = false,
    onOpenCaptureHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route

    val onTabs = route in Tabs.map { it.route }

    val homeViewModel: HomeViewModel = viewModel(factory = viewModelFactory)
    val tasksViewModel: TasksViewModel = viewModel(factory = viewModelFactory)
    val notesViewModel: NotesViewModel = viewModel(factory = viewModelFactory)
    val profileViewModel: ProfileViewModel = viewModel(factory = viewModelFactory)

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            notesViewModel.import(uris)
            navController.selectTab(Routes.NOTES)
        }
    }
    val openImport = { importPicker.launch(arrayOf("audio/*", "video/*")) }

    LaunchedEffect(pendingOpenCapture) {
        if (pendingOpenCapture) {
            navController.navigate(Routes.CAPTURE)
            onOpenCaptureHandled()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = homeViewModel,
                    onSeeTasks = { navController.selectTab(Routes.TASKS) },
                    onSeeAll = { navController.selectTab(Routes.NOTES) },
                    onOpen = { navController.navigate(Routes.detail(it)) },
                    onEditTask = { navController.navigate(Routes.editTask(it)) },
                    onImport = openImport,
                )
            }

            composable(Routes.TASKS) {
                TasksScreen(
                    viewModel = tasksViewModel,
                    onEditTask = { navController.navigate(Routes.editTask(it)) },
                    onOpenProfile = { navController.selectTab(Routes.PROFILE) },
                )
            }

            composable(Routes.NOTES) {
                NotesScreen(
                    viewModel = notesViewModel,
                    onOpen = { navController.navigate(Routes.detail(it)) },
                    onImport = openImport,
                )
            }

            composable(Routes.CAPTURE) {
                val recordViewModel: RecordViewModel = viewModel(factory = viewModelFactory)
                CaptureScreen(
                    viewModel = recordViewModel,
                    onDone = { navController.popBackStack() },
                )
            }

            composable(Routes.PROFILE) {
                val profileState by profileViewModel.state.collectAsStateWithLifecycle()
                ProfileScreen(
                    state = profileState,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
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
                    onBack = { navController.popBackStack() },
                    onLanguageChange = settingsViewModel::setLanguage,
                    onAutoAnalyzeChange = settingsViewModel::setAutoAnalyze,
                    onWifiOnlyDownloadChange = settingsViewModel::setWifiOnlyDownload,
                    onSnoozeMinutesChange = settingsViewModel::setSnoozeMinutes,
                    onDeleteAllData = settingsViewModel::deleteAllData,
                    onDownloadModel = settingsViewModel.onDownloadModel,
                    onCancelDownload = settingsViewModel.onCancelDownload,
                    onDeleteModel = settingsViewModel.onDeleteModel,
                    onTestModelDownloadNotification = settingsViewModel.onTestModelDownloadNotification,
                    onReplayOnboarding = settingsViewModel.onReplayOnboarding,
                )
            }

            composable(Routes.HISTORY) {
                val historyViewModel: HistoryViewModel = viewModel(factory = viewModelFactory)
                val historyState by historyViewModel.state.collectAsStateWithLifecycle()
                HistoryScreen(
                    state = historyState,
                    onBack = { navController.popBackStack() },
                    onPrevMonth = historyViewModel::prevMonth,
                    onNextMonth = historyViewModel::nextMonth,
                    onPickDay = historyViewModel::pick,
                )
            }

            composable(Routes.DETAIL) { entry ->
                val notesViewModel: NotesViewModel = viewModel(factory = viewModelFactory)
                NoteDetailScreen(
                    viewModel = notesViewModel,
                    noteId = entry.arguments?.getString("id").orEmpty(),
                    onDeleted = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                    onEditTask = { navController.navigate(Routes.editTask(it)) },
                )
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

        if (onTabs) {
            BottomBar(
                selected = route.orEmpty(),
                navController = navController,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * Tabs live directly above Home, never stacked on each other. Saving and restoring tab state
 * keys the saved stack to Home, so navigating home would land on the last tab instead.
 */
private fun NavHostController.selectTab(route: String) {
    if (route == Routes.HOME) {
        popBackStack(Routes.HOME, inclusive = false)
    } else {
        navigate(route) {
            popUpTo(Routes.HOME)
            launchSingleTop = true
        }
    }
}

@Composable
private fun BottomBar(
    selected: String,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
            TabBar(
                items = Tabs,
                selected = selected,
                centerGap = true,
                onSelect = { route ->
                    if (route != selected) navController.selectTab(route)
                },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EchoTheme.colors.surfaceBar)
                    .navigationBarsPadding(),
            )
        }

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
            RecordButton(
                state = RecordButtonState.Idle,
                onClick = { navController.navigate(Routes.CAPTURE) },
                size = RecordButtonSize.Sm,
            )
        }
    }
}


