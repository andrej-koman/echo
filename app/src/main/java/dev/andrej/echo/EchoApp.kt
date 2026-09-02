package dev.andrej.echo

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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
import dev.andrej.echo.ui.history.HistoryScreen
import dev.andrej.echo.ui.history.HistoryViewModel
import dev.andrej.echo.ui.record.RecordViewModel
import dev.andrej.echo.ui.theme.EchoTheme

private object Routes {
    const val TASKS = "tasks"
    const val NOTES = "notes"
    const val REMINDERS = "reminders"
    const val CAPTURE = "capture"
    const val HISTORY = "history"
    const val DETAIL = "detail/{id}"

    fun detail(id: String) = "detail/$id"
}

private val Tabs = listOf(
    TabItem(Routes.TASKS, "Tasks", R.drawable.ic_list_checks),
    TabItem(Routes.NOTES, "Notes", R.drawable.ic_file_text),
    TabItem(Routes.REMINDERS, "Reminders", R.drawable.ic_bell),
)

@Composable
fun EchoApp(
    viewModelFactory: ViewModelProvider.Factory,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route

    val onTabs = route in Tabs.map { it.route }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EchoTheme.colors.surfacePage),
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.TASKS,
            modifier = Modifier.fillMaxSize(),
        ) {
            Tabs.forEach { tab ->
                composable(tab.route) {
                    StubTab(tab = tab, onOpenHistory = { navController.navigate(Routes.HISTORY) })
                }
            }

            composable(Routes.CAPTURE) {
                val recordViewModel: RecordViewModel = viewModel(factory = viewModelFactory)
                CaptureScreen(
                    viewModel = recordViewModel,
                    onDone = { navController.popBackStack() },
                )
            }

            composable(Routes.HISTORY) {
                val historyViewModel: HistoryViewModel = viewModel(factory = viewModelFactory)
                HistoryScreen(
                    viewModel = historyViewModel,
                    onOpen = { navController.navigate(Routes.detail(it)) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.DETAIL) { entry ->
                val historyViewModel: HistoryViewModel = viewModel(factory = viewModelFactory)
                TranscriptDetailScreen(
                    viewModel = historyViewModel,
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
                            popUpTo(Routes.TASKS) { saveState = true }
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
private fun StubTab(tab: TabItem, onOpenHistory: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        EchoTopBar(
            title = tab.label,
            leading = { Mascot(size = 30.dp) },
            trailing = {
                EchoIconButton(
                    iconRes = R.drawable.ic_settings,
                    contentDescription = "History",
                    onClick = onOpenHistory,
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
