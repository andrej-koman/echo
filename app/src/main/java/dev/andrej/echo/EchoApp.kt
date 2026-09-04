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
import dev.andrej.echo.ui.detail.TranscriptDetailScreen
import dev.andrej.echo.ui.home.HomeScreen
import dev.andrej.echo.ui.home.HomeViewModel
import dev.andrej.echo.ui.record.RecordViewModel
import dev.andrej.echo.ui.tasks.TasksScreen
import dev.andrej.echo.ui.tasks.TasksViewModel
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.transcripts.NotesScreen
import dev.andrej.echo.ui.transcripts.TranscriptsScreen
import dev.andrej.echo.ui.transcripts.TranscriptsViewModel

private object Routes {
    const val HOME = "home"
    const val TASKS = "tasks"
    const val NOTES = "notes"
    const val TRANSCRIPTS = "transcripts"
    const val CAPTURE = "capture"
    const val ACCOUNT = "account"
    const val DETAIL = "detail/{id}"

    fun detail(id: String) = "detail/$id"
}

private val Tabs = listOf(
    TabItem(Routes.HOME, "Home", R.drawable.ic_home),
    TabItem(Routes.TASKS, "Tasks", R.drawable.ic_list_checks),
    TabItem(Routes.NOTES, "Notes", R.drawable.ic_file_text),
    TabItem(Routes.TRANSCRIPTS, "Transcripts", R.drawable.ic_audio_waveform),
)

@Composable
fun EchoApp(
    viewModelFactory: ViewModelProvider.Factory,
    modifier: Modifier = Modifier,
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
            )
        }
    }
}

@Composable
private fun SignedInApp(
    viewModelFactory: ViewModelProvider.Factory,
    authState: AuthState,
    onSignOut: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route

    val onTabs = route in Tabs.map { it.route }

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
                    onSeeTasks = { navController.selectTab(Routes.TASKS) },
                    onOpenAccount = { navController.navigate(Routes.ACCOUNT) },
                    onSeeAll = { navController.selectTab(Routes.TRANSCRIPTS) },
                    onOpen = { navController.navigate(Routes.detail(it)) },
                )
            }

            composable(Routes.TASKS) {
                val tasksViewModel: TasksViewModel = viewModel(factory = viewModelFactory)
                TasksScreen(
                    viewModel = tasksViewModel,
                    onOpenSource = { navController.navigate(Routes.detail(it)) },
                    onOpenAccount = { navController.navigate(Routes.ACCOUNT) },
                )
            }

            composable(Routes.NOTES) {
                val transcriptsViewModel: TranscriptsViewModel = viewModel(factory = viewModelFactory)
                NotesScreen(
                    viewModel = transcriptsViewModel,
                    onOpen = { navController.navigate(Routes.detail(it)) },
                    onOpenAccount = { navController.navigate(Routes.ACCOUNT) },
                )
            }

            composable(Routes.TRANSCRIPTS) {
                val transcriptsViewModel: TranscriptsViewModel = viewModel(factory = viewModelFactory)
                TranscriptsScreen(
                    viewModel = transcriptsViewModel,
                    onOpen = { navController.navigate(Routes.detail(it)) },
                    onOpenAccount = { navController.navigate(Routes.ACCOUNT) },
                )
            }

            composable(Routes.CAPTURE) {
                val recordViewModel: RecordViewModel = viewModel(factory = viewModelFactory)
                CaptureScreen(
                    viewModel = recordViewModel,
                    onDone = { navController.popBackStack() },
                )
            }

            composable(Routes.ACCOUNT) {
                val aiViewModel: AiViewModel = viewModel(factory = viewModelFactory)
                val aiState by aiViewModel.state.collectAsStateWithLifecycle()
                AccountScreen(
                    state = authState,
                    aiState = aiState,
                    onSignOut = onSignOut,
                    onBack = { navController.popBackStack() },
                    onDownloadModel = aiViewModel.onDownloadModel,
                    onCancelDownload = aiViewModel.onCancelDownload,
                    onDeleteModel = aiViewModel.onDeleteModel,
                )
            }

            composable(Routes.DETAIL) { entry ->
                val transcriptsViewModel: TranscriptsViewModel = viewModel(factory = viewModelFactory)
                TranscriptDetailScreen(
                    viewModel = transcriptsViewModel,
                    transcriptId = entry.arguments?.getString("id").orEmpty(),
                    onDeleted = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
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


