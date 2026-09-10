package dev.andrej.echo.ui.profile

import dev.andrej.echo.ai.AiCardState
import dev.andrej.echo.data.FakeSettingsStore
import dev.andrej.echo.data.JsonDerivedRepository
import dev.andrej.echo.data.JsonTranscriptRepository
import dev.andrej.echo.data.RecordingNotificationScheduler
import dev.andrej.echo.speech.FakeTranscriptionEngine
import dev.andrej.echo.speech.LanguageSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        engine: FakeTranscriptionEngine = FakeTranscriptionEngine(),
        settings: FakeSettingsStore = FakeSettingsStore(),
    ) = SettingsViewModel(
        settings = settings,
        engine = engine,
        transcripts = JsonTranscriptRepository(tempFolder.newFolder(), dispatcher),
        derived = JsonDerivedRepository(tempFolder.newFolder(), RecordingNotificationScheduler(), dispatcher),
        aiCardState = flowOf(AiCardState.Checking),
        onSetLlmBackend = {},
        litertModelBytes = 977_000_000L,
        onDownloadModel = {},
        onCancelDownload = {},
        onDeleteModel = {},
        storageUsedBytes = { 0L },
    )

    @Test
    fun `choosing a language that needs downloading requests the model`() = runTest {
        val engine = FakeTranscriptionEngine(
            support = LanguageSupport(installed = listOf("en-GB"), supported = listOf("en-US")),
        )
        val viewModel = viewModel(engine)
        advanceUntilIdle()

        viewModel.setLanguage("en-US")
        advanceUntilIdle()

        assertEquals(listOf("en-US"), engine.downloadRequests)
    }

    @Test
    fun `chosen language is persisted and reflected in state`() = runTest {
        val settings = FakeSettingsStore()
        val viewModel = viewModel(
            engine = FakeTranscriptionEngine(
                support = LanguageSupport(installed = listOf("en-GB", "de-DE"), supported = emptyList()),
            ),
            settings = settings,
        )
        advanceUntilIdle()

        viewModel.setLanguage("de-DE")
        advanceUntilIdle()

        assertEquals("de-DE", settings.languageTag)
        assertEquals("de-DE", viewModel.state.value.languageTag)
    }

    @Test
    fun `ring-before minutes are persisted`() = runTest {
        val settings = FakeSettingsStore()
        val viewModel = viewModel(settings = settings)
        advanceUntilIdle()

        viewModel.setReminderLeadMinutes(30)

        assertEquals(30, settings.reminderLeadMinutes)
        assertEquals(30, viewModel.state.value.reminderLeadMinutes)
    }

    @Test
    fun `morning reminder time is persisted as hour and minute`() = runTest {
        val settings = FakeSettingsStore()
        val viewModel = viewModel(settings = settings)
        advanceUntilIdle()

        viewModel.setReminderMorningTime(14, 30)

        assertEquals(14, settings.reminderMorningHour)
        assertEquals(30, settings.reminderMorningMinute)
        assertTrue(viewModel.state.value.reminderMorningHour == 14 && viewModel.state.value.reminderMorningMinute == 30)
    }
}
