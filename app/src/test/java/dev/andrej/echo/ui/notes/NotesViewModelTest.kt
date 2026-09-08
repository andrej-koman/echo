package dev.andrej.echo.ui.notes

import dev.andrej.echo.ai.AnalysisBlock
import dev.andrej.echo.ai.FakeLlmRunner
import dev.andrej.echo.ai.LlmAvailability
import dev.andrej.echo.ai.PendingAnalysisWorker
import dev.andrej.echo.ai.TranscriptAnalyzer
import dev.andrej.echo.data.JsonAnalysisQueueRepository
import dev.andrej.echo.data.JsonDerivedRepository
import dev.andrej.echo.data.JsonTranscriptRepository
import dev.andrej.echo.data.NewTodo
import dev.andrej.echo.data.PendingAnalysis
import dev.andrej.echo.data.RecordingNotificationScheduler
import dev.andrej.echo.data.Transcript
import java.time.ZoneId
import java.util.Calendar
import java.util.concurrent.TimeUnit
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `deleting a transcript also clears its derived rows`() = runTest {
        val repository = JsonTranscriptRepository(tempFolder.newFolder(), dispatcher)
        val derived = JsonDerivedRepository(tempFolder.newFolder(), RecordingNotificationScheduler(), dispatcher)
        val queue = JsonAnalysisQueueRepository(tempFolder.newFolder(), dispatcher)
        val worker = PendingAnalysisWorker(
            queue = queue,
            transcripts = repository,
            derived = derived,
            analyzer = TranscriptAnalyzer(
                { FakeLlmRunner(availability = LlmAvailability.Unsupported("no model")) },
                ZoneId.of("UTC"),
                logDebug = {},
                logWarn = {},
            ),
            scope = CoroutineScope(dispatcher),
        )
        val saved = repository.save(text = "call the plumber", language = "en-GB", durationMs = 0)
        derived.replaceFor(saved.id, listOf(NewTodo("call the plumber", 1_000L, false)))
        val viewModel = NotesViewModel(repository, queue, worker, derived)

        viewModel.delete(saved.id)
        advanceUntilIdle()

        assertTrue(derived.items.first().isEmpty())
    }

    @Test
    fun `title stops at the first sentence`() {
        assertEquals("Oat milk, two cartons", title("Oat milk, two cartons. Sourdough as well."))
    }

    @Test
    fun `title cuts a long opening at six words`() {
        assertEquals(
            "She wants the pitch cut to…",
            title("She wants the pitch cut to eight slides and the pricing page moved"),
        )
    }

    @Test
    fun `title falls back for empty text`() {
        assertEquals("Untitled", title("   "))
    }

    @Test
    fun `clock pads seconds`() {
        assertEquals("1:05", clock(65_000))
        assertEquals("0:09", clock(9_400))
    }

    @Test
    fun `spoken total switches to hours past sixty minutes`() {
        assertEquals("14m", spokenTotal(TimeUnit.MINUTES.toMillis(14)))
        assertEquals("2h 14m", spokenTotal(TimeUnit.MINUTES.toMillis(134)))
    }

    @Test
    fun `groups are labelled relative to today and ordered newest first`() {
        val now = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 12) }.timeInMillis
        val day = TimeUnit.DAYS.toMillis(1)

        val groups = group(
            listOf(
                transcript("older", now - 3 * day),
                transcript("today", now),
                transcript("yesterday", now - day),
            ),
            now,
        )

        assertEquals(listOf("Today", "Yesterday"), groups.take(2).map { it.label })
        assertEquals(3, groups.size)
        assertEquals("today", groups[0].items.single().id)
    }

    @Test
    fun `a row picks up its pending copy from the map`() {
        val now = 1_000L
        val pending = mapOf("a" to PendingCopy("Analysis failed — tap retry", PendingSeverity.Failed))

        val groups = group(listOf(transcript("a", now)), now, pending)

        assertEquals(pending["a"], groups.single().items.single().pending)
    }

    @Test
    fun `a row with no queue entry has no pending copy`() {
        val now = 1_000L

        val groups = group(listOf(transcript("a", now)), now)

        assertNull(groups.single().items.single().pending)
    }

    @Test
    fun `waiting for a model names its size`() {
        val copy = AnalysisBlock.WaitingForModel(977_000_000).toPendingCopy()

        assertEquals("Needs ~977MB to analyze", copy?.text)
        assertEquals(PendingSeverity.Parked, copy?.severity)
    }

    @Test
    fun `a download in progress has no size to report`() {
        val copy = AnalysisBlock.WaitingForModel(0).toPendingCopy()

        assertEquals("Downloading the model…", copy?.text)
    }

    @Test
    fun `no backend is parked, not failed`() {
        val copy = AnalysisBlock.NoBackend("no AICore").toPendingCopy()

        assertEquals(PendingSeverity.Parked, copy?.severity)
    }

    @Test
    fun `generation failures and empty results both read as failed`() {
        assertEquals(PendingSeverity.Failed, AnalysisBlock.GenerationFailed("boom").toPendingCopy()?.severity)
        assertEquals(PendingSeverity.Failed, AnalysisBlock.EmptyResult.toPendingCopy()?.severity)
    }

    @Test
    fun `an empty transcript never shows a pending badge`() {
        assertNull(AnalysisBlock.EmptyTranscript.toPendingCopy())
    }

    @Test
    fun `only entries with an attempted block show up in the pending map`() {
        val entries = listOf(
            PendingAnalysis(transcriptId = "a", enqueuedAt = 0, lastBlock = AnalysisBlock.EmptyResult),
            PendingAnalysis(transcriptId = "b", enqueuedAt = 0, lastBlock = null),
        )

        val byTranscript = pendingCopyByTranscript(entries)

        assertEquals(setOf("a"), byTranscript.keys)
    }

    private fun transcript(id: String, createdAt: Long) = Transcript(
        id = id,
        text = "Something said out loud.",
        language = "en-GB",
        createdAt = createdAt,
        durationMs = 1_000,
    )
}
