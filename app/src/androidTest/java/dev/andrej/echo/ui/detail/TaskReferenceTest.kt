package dev.andrej.echo.ui.detail

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.andrej.echo.ai.PendingAnalysisWorker
import dev.andrej.echo.ai.TranscriptAnalyzer
import dev.andrej.echo.data.*
import dev.andrej.echo.data.db.EchoDatabase
import dev.andrej.echo.ui.notes.NotesViewModel
import dev.andrej.echo.ui.theme.EchoTheme
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TaskReferenceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sourceLinksOpenTheirTaskFromBothTranscriptViews() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val driver = AndroidSqliteDriver(EchoDatabase.Schema, instrumentation.targetContext, null)
        val database = EchoDatabase(driver)
        val repository = SqlTranscriptRepository(database)
        val derived = SqlDerivedRepository(database)
        val queue = SqlAnalysisQueueRepository(database)
        val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = NotesViewModel(repository, queue, PendingAnalysisWorker(
            queue, repository, derived, TranscriptAnalyzer({ error("No model needed") }), workerScope,
        ), derived)
        try {
            val lines = listOf(
                Segment(0, 1000, "Tomorrow at 10 a.m.", 0),
                Segment(1000, 3000, "I have to go to the dentist. Unrelated words.", 0),
                Segment(3000, 4000, "Then call Alex.", 0),
            )
            repository.save("reference", lines.joinToString(" ") { it.text }, "en", 3000)
            repository.setTranscription("reference", lines.joinToString(" ") { it.text }, "en", 3000, lines)
            repository.setSpeakers("reference", lines, 1)
            val filler = (1..25).map { NewTodo("Other task $it.", 0, false, false) }
            derived.applyAnalysis("reference", filler + listOf(
                NewTodo("Go to the dentist.", 0, false, false, 1000,
                    "Tomorrow at 10 a.m. I have to go to the dentist."),
                NewTodo("Call Alex.", 0, false, false, 3000, "Then call Alex."),
            ), "Reference test", "Test", 0)
            compose.setContent { EchoTheme { NoteDetailScreen("reference", viewModel, {}, {}, {}) } }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("By speaker").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Tasks").performClick()
            val row = compose.onNode(hasText("Other task 1.") and hasClickAction())
            val bounds = row.getUnclippedBoundsInRoot()
            assertTrue("Task rows should retain their compact height", bounds.bottom - bounds.top <= 72.dp)
            val fullText = lines.joinToString(" ") { it.text }
            for (tab in listOf("Full text", "By speaker")) {
                compose.onNodeWithText(tab).performClick()
                val phrase = compose.onNodeWithText(fullText)
                val annotated = phrase.fetchSemanticsNode().config[SemanticsProperties.Text].single()
                assertEquals(2, annotated.getLinkAnnotations(0, annotated.length).size)
                val backgrounds = annotated.spanStyles.map { it.item.background }.filter { it != Color.Unspecified }
                assertEquals(2, backgrounds.distinct().size)
                val result = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                phrase.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(result) }
                val position = result.single().getBoundingBox(annotated.indexOf("dentist")).center
                phrase.performTouchInput { click(Offset(position.x, position.y)) }
                compose.onNodeWithText("Go to the dentist.").assertIsDisplayed()
            }
            compose.onNodeWithText("By speaker").performClick()
            val phrase = compose.onNodeWithText(fullText)
            val layout = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            phrase.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layout) }
            val point = layout.single().getBoundingBox(fullText.indexOf("Unrelated")).center
            phrase.performTouchInput { longClick(point) }
            compose.onNodeWithText("1 line selected").performScrollTo().assertIsDisplayed()
        } finally {
            workerScope.cancel()
            driver.close()
        }
    }
}
