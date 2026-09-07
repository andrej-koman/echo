package dev.andrej.echo.ui.record

import dev.andrej.echo.ai.FakeLlmRunner
import dev.andrej.echo.ai.LlmAvailability
import dev.andrej.echo.ai.LlmRunner
import dev.andrej.echo.ai.PendingAnalysisWorker
import dev.andrej.echo.ai.TranscriptAnalyzer
import dev.andrej.echo.data.FakeSettingsStore
import dev.andrej.echo.data.JsonAnalysisQueueRepository
import dev.andrej.echo.data.JsonDerivedRepository
import dev.andrej.echo.data.JsonTranscriptRepository
import dev.andrej.echo.data.RecordingNotificationScheduler
import dev.andrej.echo.speech.Availability
import dev.andrej.echo.speech.FailureReason
import dev.andrej.echo.speech.FakeTranscriptionEngine
import dev.andrej.echo.speech.LanguageSupport
import dev.andrej.echo.speech.TranscriptionEvent
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class RecordViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // Writes run on the test scheduler so advanceUntilIdle covers them.
    private fun repository() = JsonTranscriptRepository(tempFolder.newFolder(), dispatcher)

    private fun derivedRepository() =
        JsonDerivedRepository(tempFolder.newFolder(), RecordingNotificationScheduler(), dispatcher)

    private fun viewModel(
        engine: FakeTranscriptionEngine = FakeTranscriptionEngine(),
        settings: FakeSettingsStore = FakeSettingsStore(),
        repository: JsonTranscriptRepository = repository(),
        derived: JsonDerivedRepository = derivedRepository(),
        runner: LlmRunner = FakeLlmRunner(availability = LlmAvailability.Unsupported("no model")),
        warmup: () -> Unit = {},
    ): RecordViewModel {
        val queue = JsonAnalysisQueueRepository(tempFolder.newFolder(), dispatcher)
        val worker = PendingAnalysisWorker(
            queue = queue,
            transcripts = repository,
            derived = derived,
            analyzer = TranscriptAnalyzer({ runner }, ZoneId.of("UTC"), logDebug = {}, logWarn = {}),
            scope = CoroutineScope(dispatcher),
            now = { 42L },
        )
        return RecordViewModel(
            engine = engine,
            repository = repository,
            queue = queue,
            worker = worker,
            settings = settings,
            warmup = warmup,
            clock = { 42L },
        )
    }

    private fun analyzingRunner(json: String) = FakeLlmRunner(responses = listOf(json))

    private fun engine(
        events: List<TranscriptionEvent> = emptyList(),
        availability: Availability = Availability.Available,
        installed: List<String> = listOf("en-GB"),
        supported: List<String> = listOf("en-US", "de-DE"),
    ) = FakeTranscriptionEngine(events, availability, LanguageSupport(installed, supported))

    @Test
    fun `defaults to a language the device has installed`() = runTest {
        val viewModel = viewModel(engine(installed = listOf("en-GB"), supported = listOf("en-US")))

        advanceUntilIdle()

        assertEquals("en-GB", viewModel.uiState.value.language?.tag)
        assertTrue(viewModel.uiState.value.language?.installed == true)
    }

    @Test
    fun `offers installed languages first and marks which are installed`() = runTest {
        val viewModel = viewModel(
            engine(installed = listOf("en-GB"), supported = listOf("en-US", "de-DE")),
        )

        advanceUntilIdle()

        val options = viewModel.uiState.value.languages
        assertEquals(listOf("en-GB", "en-US", "de-DE"), options.map { it.tag })
        assertEquals(listOf(true, false, false), options.map { it.installed })
    }

    @Test
    fun `unsupported languages are not offered at all`() = runTest {
        val viewModel = viewModel(
            engine(installed = listOf("en-GB"), supported = listOf("en-US")),
        )

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.languages.any { it.tag == "sl-SI" })
    }

    @Test
    fun `choosing a language that needs downloading requests the model`() = runTest {
        val engine = engine(installed = listOf("en-GB"), supported = listOf("en-US"))
        val viewModel = viewModel(engine)
        advanceUntilIdle()

        viewModel.setLanguage("en-US")
        advanceUntilIdle()

        assertEquals(listOf("en-US"), engine.downloadRequests)
        assertTrue(viewModel.uiState.value.downloadingLanguage == "en-US")
    }

    @Test
    fun `recording does not start while the model is missing`() = runTest {
        val engine = engine(installed = listOf("en-GB"), supported = listOf("en-US"))
        val viewModel = viewModel(engine)
        advanceUntilIdle()

        viewModel.setLanguage("en-US")
        viewModel.startRecording()
        advanceUntilIdle()

        assertTrue(engine.requestedLanguages.isEmpty())
        assertFalse(viewModel.uiState.value.isRecording)
    }

    @Test
    fun `a finished download makes the language usable`() = runTest {
        val engine = engine(installed = listOf("en-GB"), supported = listOf("en-US"))
        val viewModel = viewModel(engine)
        advanceUntilIdle()
        viewModel.setLanguage("en-US")
        advanceUntilIdle()

        engine.completeDownload("en-US")
        viewModel.refreshLanguages()
        advanceUntilIdle()
        viewModel.startRecording()
        advanceUntilIdle()

        assertEquals(listOf("en-US"), engine.requestedLanguages)
        assertEquals(null, viewModel.uiState.value.downloadingLanguage)
    }

    @Test
    fun `a remembered language is used when still installed`() = runTest {
        val viewModel = viewModel(
            engine = engine(installed = listOf("en-GB", "de-DE")),
            settings = FakeSettingsStore("de-DE"),
        )

        advanceUntilIdle()

        assertEquals("de-DE", viewModel.uiState.value.language?.tag)
    }

    @Test
    fun `a remembered language that is gone falls back to an installed one`() = runTest {
        val viewModel = viewModel(
            engine = engine(installed = listOf("en-GB"), supported = listOf("en-US")),
            settings = FakeSettingsStore("sl-SI"),
        )

        advanceUntilIdle()

        assertEquals("en-GB", viewModel.uiState.value.language?.tag)
    }

    @Test
    fun `chosen language is persisted`() = runTest {
        val settings = FakeSettingsStore()
        val viewModel = viewModel(
            engine = engine(installed = listOf("en-GB", "de-DE")),
            settings = settings,
        )
        advanceUntilIdle()

        viewModel.setLanguage("de-DE")
        advanceUntilIdle()

        assertEquals("de-DE", settings.languageTag)
    }

    @Test
    fun `no installed language at all is reported`() = runTest {
        val viewModel = viewModel(
            engine(installed = emptyList(), supported = listOf("en-US")),
        )

        advanceUntilIdle()

        assertEquals("en-US", viewModel.uiState.value.language?.tag)
        assertFalse(viewModel.uiState.value.language?.installed == true)
    }

    @Test
    fun `partial text replaces the previous partial`() = runTest {
        val viewModel = viewModel(
            engine(listOf(TranscriptionEvent.Partial("hel"), TranscriptionEvent.Partial("hello"))),
        )
        advanceUntilIdle()

        viewModel.startRecording()
        advanceUntilIdle()

        assertEquals("hello", viewModel.uiState.value.partialText)
        assertEquals("", viewModel.uiState.value.committedText)
    }

    @Test
    fun `final text accumulates and clears the partial`() = runTest {
        val viewModel = viewModel(
            engine(
                listOf(
                    TranscriptionEvent.Partial("hello"),
                    TranscriptionEvent.Final("hello there"),
                    TranscriptionEvent.Final("friend"),
                ),
            ),
        )
        advanceUntilIdle()

        viewModel.startRecording()
        advanceUntilIdle()

        assertEquals("hello there friend", viewModel.uiState.value.committedText)
        assertEquals("", viewModel.uiState.value.partialText)
    }

    @Test
    fun `stopping saves the transcript with the language actually used`() = runTest {
        val repository = repository()
        val viewModel = viewModel(
            engine = engine(listOf(TranscriptionEvent.Final("save me"))),
            repository = repository,
        )
        advanceUntilIdle()

        viewModel.startRecording()
        advanceUntilIdle()
        viewModel.stopRecording()
        advanceUntilIdle()

        val saved = repository.transcripts.first()
        assertEquals("save me", saved.single().text)
        assertEquals("en-GB", saved.single().language)
    }

    @Test
    fun `pending partial text is kept when stopping`() = runTest {
        val repository = repository()
        val viewModel = viewModel(
            engine = engine(listOf(TranscriptionEvent.Final("done"), TranscriptionEvent.Partial("half"))),
            repository = repository,
        )
        advanceUntilIdle()

        viewModel.startRecording()
        advanceUntilIdle()
        viewModel.stopRecording()
        advanceUntilIdle()

        assertEquals("done half", repository.transcripts.first().single().text)
    }

    @Test
    fun `blank transcript is not saved`() = runTest {
        val repository = repository()
        val viewModel = viewModel(engine = engine(), repository = repository)
        advanceUntilIdle()

        viewModel.startRecording()
        advanceUntilIdle()
        viewModel.stopRecording()
        advanceUntilIdle()

        assertTrue(repository.transcripts.first().isEmpty())
    }

    @Test
    fun `stopping resets the screen`() = runTest {
        val viewModel = viewModel(engine(listOf(TranscriptionEvent.Final("text"))))
        advanceUntilIdle()

        viewModel.startRecording()
        advanceUntilIdle()
        viewModel.stopRecording()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRecording)
        assertEquals("", state.committedText)
        assertEquals("", state.partialText)
    }

    @Test
    fun `failure surfaces an error and stops recording`() = runTest {
        val viewModel = viewModel(
            engine(listOf(TranscriptionEvent.Failed(FailureReason.LanguageUnavailable))),
        )
        advanceUntilIdle()

        viewModel.startRecording()
        advanceUntilIdle()

        assertEquals(FailureReason.LanguageUnavailable, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isRecording)
    }

    @Test
    fun `partial text that arrives after a failure is ignored`() = runTest {
        val viewModel = viewModel(
            engine(
                listOf(
                    TranscriptionEvent.Failed(FailureReason.Unknown),
                    TranscriptionEvent.Partial("too late"),
                ),
            ),
        )
        advanceUntilIdle()

        viewModel.startRecording()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.partialText)
    }

    @Test
    fun `microphone level is exposed for the meter`() = runTest {
        val viewModel = viewModel(engine(listOf(TranscriptionEvent.Level(4f))))
        advanceUntilIdle()

        viewModel.startRecording()
        advanceUntilIdle()

        assertEquals(4f, viewModel.uiState.value.level, 0.001f)
    }

    @Test
    fun `starting twice does not double up the text`() = runTest {
        val viewModel = viewModel(engine(listOf(TranscriptionEvent.Final("once"))))
        advanceUntilIdle()

        viewModel.startRecording()
        viewModel.startRecording()
        advanceUntilIdle()

        assertEquals("once", viewModel.uiState.value.committedText)
    }

    @Test
    fun `stopping holds in processing until the work is done`() = runTest {
        val viewModel = viewModel(engine(listOf(TranscriptionEvent.Final("text"))))
        advanceUntilIdle()
        viewModel.startRecording()
        advanceUntilIdle()

        viewModel.stopRecording()
        assertTrue(viewModel.uiState.value.isProcessing)

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isProcessing)
    }

    @Test
    fun `analysis fills in the title, the tasks and the reminders`() = runTest {
        val repository = repository()
        val derived = derivedRepository()
        val viewModel = viewModel(
            engine = engine(listOf(TranscriptionEvent.Final("call the plumber"))),
            repository = repository,
            derived = derived,
            runner = analyzingRunner(
                """{"title":"Plumber","summary":"Call them.",
                   "items":[{"text":"call the plumber","due":"2026-09-04T10:00"}]}""",
            ),
        )
        advanceUntilIdle()
        viewModel.startRecording()
        advanceUntilIdle()

        viewModel.stopRecording()
        advanceUntilIdle()

        val transcript = repository.transcripts.first().single()
        assertEquals("Plumber", transcript.title)
        assertEquals("Call them.", transcript.summary)
        assertEquals(42L, transcript.analyzedAt)

        val item = derived.items.first().single()
        assertEquals("call the plumber", item.text)
        assertEquals(transcript.id, item.sourceTranscriptId)
    }

    @Test
    fun `a transcript survives a model that cannot run`() = runTest {
        val repository = repository()
        val derived = derivedRepository()
        val viewModel = viewModel(
            engine = engine(listOf(TranscriptionEvent.Final("keep me"))),
            repository = repository,
            derived = derived,
            runner = FakeLlmRunner(availability = LlmAvailability.Unsupported("no model")),
        )
        advanceUntilIdle()
        viewModel.startRecording()
        advanceUntilIdle()

        viewModel.stopRecording()
        advanceUntilIdle()

        assertEquals("keep me", repository.transcripts.first().single().text)
        assertEquals(null, repository.transcripts.first().single().title)
        assertTrue(derived.items.first().isEmpty())
        assertFalse(viewModel.uiState.value.isProcessing)
    }

    @Test
    fun `a transcript survives a model that throws`() = runTest {
        val repository = repository()
        val derived = derivedRepository()
        val viewModel = viewModel(
            engine = engine(listOf(TranscriptionEvent.Final("keep me too"))),
            repository = repository,
            derived = derived,
            runner = FakeLlmRunner(throwOnGenerate = true),
        )
        advanceUntilIdle()
        viewModel.startRecording()
        advanceUntilIdle()

        viewModel.stopRecording()
        advanceUntilIdle()

        assertEquals("keep me too", repository.transcripts.first().single().text)
        assertTrue(derived.items.first().isEmpty())
        assertFalse(viewModel.uiState.value.isProcessing)
    }

    @Test
    fun `opening the screen warms the model up`() = runTest {
        var warmups = 0
        viewModel(warmup = { warmups++ })
        advanceUntilIdle()

        assertEquals(1, warmups)
    }

    @Test
    fun `discarding keeps nothing and never enters processing`() = runTest {
        val repository = repository()
        val viewModel = viewModel(
            engine = engine(listOf(TranscriptionEvent.Final("forget me"))),
            repository = repository,
        )
        advanceUntilIdle()
        viewModel.startRecording()
        advanceUntilIdle()

        viewModel.discard()
        advanceUntilIdle()

        assertTrue(repository.transcripts.first().isEmpty())
        assertFalse(viewModel.uiState.value.isRecording)
        assertFalse(viewModel.uiState.value.isProcessing)
    }

    @Test
    fun `stopping when not recording does nothing`() = runTest {
        val repository = repository()
        val viewModel = viewModel(engine = engine(), repository = repository)
        advanceUntilIdle()

        viewModel.stopRecording()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isProcessing)
        assertTrue(repository.transcripts.first().isEmpty())
    }

    @Test
    fun `the take start time is published for the timer`() = runTest {
        val viewModel = viewModel(engine())
        advanceUntilIdle()

        viewModel.startRecording()
        advanceUntilIdle()

        assertEquals(42L, viewModel.uiState.value.startedAtMillis)
    }

    @Test
    fun `unavailable recognizer is reported before recording starts`() = runTest {
        val viewModel = viewModel(engine(availability = Availability.NoRecognizer))

        advanceUntilIdle()

        assertEquals(Availability.NoRecognizer, viewModel.uiState.value.availability)
    }
}
