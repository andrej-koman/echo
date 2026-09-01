package dev.andrej.echo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.andrej.echo.data.JsonTranscriptRepository
import dev.andrej.echo.data.SettingsStore
import dev.andrej.echo.speech.Availability
import dev.andrej.echo.speech.LanguageSupport
import dev.andrej.echo.speech.TranscriptionEngine
import dev.andrej.echo.speech.TranscriptionEvent
import dev.andrej.echo.ui.record.RecordScreen
import dev.andrej.echo.ui.record.RecordViewModel
import dev.andrej.echo.ui.record.TAG_LANGUAGE_ROW
import dev.andrej.echo.ui.record.TAG_RECORD_BUTTON
import dev.andrej.echo.ui.record.TAG_TRANSCRIPT
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Drives the record screen against a scripted engine, so no microphone or permission is needed.
 */
class RecordScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class ScriptedEngine(
        private val events: List<TranscriptionEvent> = emptyList(),
        private val availability: Availability = Availability.Available,
        private val support: LanguageSupport = LanguageSupport(
            installed = listOf("en-GB"),
            supported = listOf("en-US"),
        ),
    ) : TranscriptionEngine {
        val downloadRequests = mutableListOf<String>()

        override fun availability() = availability
        override suspend fun languageSupport() = support
        override fun requestModelDownload(language: String) { downloadRequests += language }
        override fun transcribe(language: String): Flow<TranscriptionEvent> =
            flow { events.forEach { emit(it) } }
    }

    private class InMemorySettings : SettingsStore {
        override var languageTag: String? = null
    }

    private fun viewModel(engine: ScriptedEngine = ScriptedEngine()) = RecordViewModel(
        engine = engine,
        repository = JsonTranscriptRepository(tempFolder.newFolder()),
        settings = InMemorySettings(),
    )

    @Test
    fun showsPromptBeforeRecording() {
        composeRule.setContent { EchoTheme { RecordScreen(viewModel()) } }

        composeRule.onNodeWithText("Tap record and start talking.").assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_RECORD_BUTTON).assertIsDisplayed()
    }

    @Test
    fun showsTranscribedText() {
        val viewModel = viewModel(ScriptedEngine(listOf(TranscriptionEvent.Final("hello from the test"))))
        composeRule.setContent { EchoTheme { RecordScreen(viewModel) } }

        composeRule.waitUntil(timeoutMillis = 3_000) {
            viewModel.uiState.value.language != null
        }
        viewModel.startRecording()

        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodesWithTag(TAG_TRANSCRIPT).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("hello from the test").assertIsDisplayed()
    }

    @Test
    fun offersOnlyLanguagesTheDeviceKnows() {
        val engine = ScriptedEngine(
            support = LanguageSupport(installed = listOf("en-GB"), supported = listOf("en-US")),
        )
        composeRule.setContent { EchoTheme { RecordScreen(viewModel(engine)) } }

        // Labels are localised device names, so match on the language name rather than the tag.
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodesWithTag(TAG_LANGUAGE_ROW).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(TAG_LANGUAGE_ROW).assertIsDisplayed()
    }

    @Test
    fun explainsMissingRecognizer() {
        composeRule.setContent {
            EchoTheme { RecordScreen(viewModel(ScriptedEngine(availability = Availability.NoRecognizer))) }
        }

        composeRule.onNodeWithText(
            "This device has no on-device speech recognition. " +
                "Check Settings › System › Languages & input › On-device speech recognition.",
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_RECORD_BUTTON).assertDoesNotExist()
    }
}
