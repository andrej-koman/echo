package dev.andrej.echo.ui.detail

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.andrej.echo.ai.PendingAnalysisWorker
import dev.andrej.echo.ai.TranscriptAnalyzer
import dev.andrej.echo.data.Segment
import dev.andrej.echo.data.SqlAnalysisQueueRepository
import dev.andrej.echo.data.SqlDerivedRepository
import dev.andrej.echo.data.SqlTranscriptRepository
import dev.andrej.echo.data.db.EchoDatabase
import dev.andrej.echo.ui.notes.NotesViewModel
import dev.andrej.echo.ui.theme.EchoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FullTextEditingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editActionSavesFullTextWithoutSpeakerLabels() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val driver = AndroidSqliteDriver(EchoDatabase.Schema, instrumentation.targetContext, null)
        val database = EchoDatabase(driver)
        val repository = SqlTranscriptRepository(database)
        val queue = SqlAnalysisQueueRepository(database)
        val derived = SqlDerivedRepository(database)
        val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = ViewModelStore()
        try {
            val id = "speakerless-edit-test"
            repository.save(id, "Original transcript", "en", 1000)
            repository.setTranscription(id, "Original transcript", "en", 1000, listOf(
                Segment(0, 1000, "Original transcript", speaker = null),
            ))
            val worker = PendingAnalysisWorker(
                queue, repository, derived,
                TranscriptAnalyzer({ error("Editing must not invoke analysis") }), workerScope,
            )
            val viewModel = NotesViewModel(repository, queue, worker, derived)
            instrumentation.runOnMainSync {
                ViewModelProvider(store, object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
                })[NotesViewModel::class.java]
            }
            compose.setContent {
                EchoTheme {
                    NoteDetailScreen(id, viewModel, onDeleted = {}, onBack = {}, onEditTask = {})
                }
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription("Edit").fetchSemanticsNodes().size == 1
            }
            compose.onNodeWithText("By speaker").assertDoesNotExist()
            compose.onNodeWithText("Original transcript").assertIsDisplayed()
            compose.onNodeWithContentDescription("Edit").performClick()
            compose.onNode(hasSetTextAction()).performTextReplacement("Corrected transcript")
            compose.onNodeWithContentDescription("Edit").performClick()
            compose.waitUntil(10_000) {
                runBlocking { repository.segments(id).single().text == "Corrected transcript" }
            }
            assertEquals("Corrected transcript", repository.transcripts.first().single().text)
            compose.onNode(hasSetTextAction()).assertDoesNotExist()
            compose.onNodeWithText("Corrected transcript").assertIsDisplayed()
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            workerScope.cancel()
            driver.close()
        }
    }
}
