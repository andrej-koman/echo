package dev.andrej.echo

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.andrej.echo.ui.detail.TranscriptDetailScreen
import dev.andrej.echo.ui.history.HistoryScreen
import dev.andrej.echo.ui.history.HistoryViewModel
import dev.andrej.echo.ui.record.RecordScreen
import dev.andrej.echo.ui.record.RecordViewModel

private object Routes {
    const val RECORD = "record"
    const val HISTORY = "history"
    const val DETAIL = "detail/{id}"

    fun detail(id: String) = "detail/$id"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EchoApp(
    viewModelFactory: ViewModelProvider.Factory,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val currentRoute by navController.currentBackStackEntryAsState()
    val route = currentRoute?.destination?.route

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (route == Routes.RECORD) "Echo" else "History") },
                actions = {
                    if (route == Routes.RECORD) {
                        IconButton(onClick = { navController.navigate(Routes.HISTORY) }) {
                            Text("☰")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.RECORD,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.RECORD) {
                val viewModel: RecordViewModel = viewModel(factory = viewModelFactory)
                RecordScreen(viewModel = viewModel)
            }

            composable(Routes.HISTORY) {
                val viewModel: HistoryViewModel = viewModel(factory = viewModelFactory)
                HistoryScreen(
                    viewModel = viewModel,
                    onOpen = { id -> navController.navigate(Routes.detail(id)) },
                )
            }

            composable(Routes.DETAIL) { backStackEntry ->
                val viewModel: HistoryViewModel = viewModel(factory = viewModelFactory)
                TranscriptDetailScreen(
                    transcriptId = backStackEntry.arguments?.getString("id").orEmpty(),
                    viewModel = viewModel,
                    onDeleted = { navController.popBackStack() },
                )
            }
        }
    }
}
