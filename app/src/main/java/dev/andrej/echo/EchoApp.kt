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
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.andrej.echo.auth.Account
import dev.andrej.echo.auth.AuthState
import dev.andrej.echo.ui.auth.AccountScreen
import dev.andrej.echo.ui.auth.AuthViewModel
import dev.andrej.echo.ui.auth.SignInScreen
import dev.andrej.echo.ui.capture.CaptureScreen
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.components.EmptyState
import dev.andrej.echo.ui.components.Mascot
import dev.andrej.echo.ui.components.RecordButton
import dev.andrej.echo.ui.components.RecordButtonSize
import dev.andrej.echo.ui.components.RecordButtonState
import dev.andrej.echo.ui.components.TabBar
import dev.andrej.echo.ui.components.TabItem
import dev.andrej.echo.ui.detail.TranscriptDetailScreen
import dev.andrej.echo.ui.home.HomeScreen
import dev.andrej.echo.ui.record.RecordViewModel
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.transcripts.TranscriptsScreen
import dev.andrej.echo.ui.transcripts.TranscriptsViewModel

private object Routes {
    const val HOME = "home"
    const val TASKS = "tasks"
    const val NOTES = "notes"
    const val REMINDERS = "reminders"
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
    TabItem(Routes.REMINDERS, "Reminders", R.drawable.ic_bell),
    TabItem(Routes.TRANSCRIPTS, "Transcripts", R.drawable.ic_audio_waveform),
)

private val StubTabs = Tabs.drop(1).dropLast(1)

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
                val transcriptsViewModel: TranscriptsViewModel = viewModel(factory = viewModelFactory)
                HomeScreen(
                    viewModel = transcriptsViewModel,
                    userName = (authState as? AuthState.SignedIn)?.account?.firstName(),
                    onStartRecording = { navController.navigate(Routes.CAPTURE) },
                    onOpenAccount = { navController.navigate(Routes.ACCOUNT) },
                    onSeeAll = { navController.navigate(Routes.TRANSCRIPTS) },
                    onOpen = { navController.navigate(Routes.detail(it)) },
                )
            }

            StubTabs.forEach { tab ->
                composable(tab.route) {
                    StubTab(tab = tab, onOpenAccount = { navController.navigate(Routes.ACCOUNT) })
                }
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
                AccountScreen(
                    state = authState,
                    onSignOut = onSignOut,
                    onBack = { navController.popBackStack() },
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
                onSelect = { route ->
                    if (route != selected) {
                        navController.navigate(route) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EchoTheme.colors.surfaceBar)
                    .navigationBarsPadding(),
            )
        }

        RecordButton(
            state = RecordButtonState.Idle,
            onClick = { navController.navigate(Routes.CAPTURE) },
            size = RecordButtonSize.Md,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 62.dp),
        )
    }
}

@Composable
private fun StubTab(tab: TabItem, onOpenAccount: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        EchoTopBar(
            title = tab.label,
            leading = { Mascot(size = 30.dp) },
            trailing = {
                EchoIconButton(
                    iconRes = R.drawable.ic_user,
                    contentDescription = "Account",
                    onClick = onOpenAccount,
                )
            },
        )
        Box(modifier = Modifier.fillMaxSize().padding(bottom = 150.dp)) {
            EmptyState(
                title = "Nothing here yet",
                body = "Tap the button below and say something. " +
                    "Echo files it into ${tab.label.lowercase()} for you.",
            )
        }
    }
}

private fun Account.firstName(): String? =
    displayName?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() }
