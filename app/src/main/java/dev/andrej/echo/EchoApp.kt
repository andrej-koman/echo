package dev.andrej.echo

import androidx.activity.compose.LocalActivity
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
import dev.andrej.echo.auth.AuthState
import dev.andrej.echo.ui.auth.AccountScreen
import dev.andrej.echo.ui.auth.AiViewModel
import dev.andrej.echo.ui.auth.AuthViewModel
import dev.andrej.echo.ui.auth.SignInScreen
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
import dev.andrej.echo.ui.record.RecordViewModel
import dev.andrej.echo.ui.tasks.EditTaskScreen
import dev.andrej.echo.ui.tasks.TasksScreen
import dev.andrej.echo.ui.tasks.TasksViewModel
import dev.andrej.echo.ui.theme.EchoTheme

private object Routes {
    const val HOME = "home"
    const val TASKS = "tasks"
    const val NOTES = "notes"
    const val CAPTURE = "capture"
    const val PROFILE = "profile"
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
    modifier: Modifier = Modifier,
    pendingOpenTranscriptId: String? = null,
    onOpenTranscriptHandled: () -> Unit = {},
) {
    val authViewModel: AuthViewModel = viewModel(factory = viewModelFactory)
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EchoTheme.colors.surfacePage),
    ) {
        when (authState) {
            AuthState.Unknown -> Unit

            AuthState.SignedOut -> SignInScreen(
                busy = authViewModel.busy,
                error = authViewModel.error,
                onSignInWithGoogle = { activity?.let(authViewModel::signIn) },
                onContinueWithoutAccount = authViewModel::continueAsGuest,
            )

            else -> SignedInApp(
                viewModelFactory = viewModelFactory,
                authState = authState,
                onSignOut = authViewModel::signOut,
                pendingOpenTranscriptId = pendingOpenTranscriptId,
                onOpenTranscriptHandled = onOpenTranscriptHandled,
            )
        }
    }
}

@Composable
private fun SignedInApp(
    viewModelFactory: ViewModelProvider.Factory,
    authState: AuthState,
    onSignOut: () -> Unit,
    pendingOpenTranscriptId: String? = null,
    onOpenTranscriptHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route

    val onTabs = route in Tabs.map { it.route }

    LaunchedEffect(pendingOpenTranscriptId) {
        if (pendingOpenTranscriptId != null) {
            navController.navigate(Routes.detail(pendingOpenTranscriptId))
            onOpenTranscriptHandled()
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
                val homeViewModel: HomeViewModel = viewModel(factory = viewModelFactory)
                HomeScreen(
                    viewModel = homeViewModel,
                    userName = (authState as? AuthState.SignedIn)?.account?.displayName,
                    onSeeTasks = { navController.selectTab(Routes.TASKS) },
                    onSeeAll = { navController.selectTab(Routes.NOTES) },
                    onOpen = { navController.navigate(Routes.detail(it)) },
                    onEditTask = { navController.navigate(Routes.editTask(it)) },
                )
            }

            composable(Routes.TASKS) {
                val tasksViewModel: TasksViewModel = viewModel(factory = viewModelFactory)
                TasksScreen(
                    viewModel = tasksViewModel,
                    onEditTask = { navController.navigate(Routes.editTask(it)) },
                    onOpenProfile = { navController.selectTab(Routes.PROFILE) },
                )
            }

            composable(Routes.NOTES) {
                val notesViewModel: NotesViewModel = viewModel(factory = viewModelFactory)
                NotesScreen(
                    viewModel = notesViewModel,
                    onOpen = { navController.navigate(Routes.detail(it)) },
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
                val aiViewModel: AiViewModel = viewModel(factory = viewModelFactory)
                val aiState by aiViewModel.state.collectAsStateWithLifecycle()
                AccountScreen(
                    state = authState,
                    aiState = aiState,
                    onSignOut = onSignOut,
                    onDownloadModel = aiViewModel.onDownloadModel,
                    onCancelDownload = aiViewModel.onCancelDownload,
                    onDeleteModel = aiViewModel.onDeleteModel,
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


