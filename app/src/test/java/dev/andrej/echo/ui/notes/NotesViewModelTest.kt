package dev.andrej.echo.ui.notes

import dev.andrej.echo.ai.AnalysisBlock
import dev.andrej.echo.ai.FakeLlmRunner
import dev.andrej.echo.ai.LlmAvailability
import dev.andrej.echo.ai.PendingAnalysisWorker
import dev.andrej.echo.ai.TranscriptAnalyzer
import dev.andrej.echo.data.SqlAnalysisQueueRepository
import dev.andrej.echo.data.SqlDerivedRepository
import dev.andrej.echo.data.SqlTranscriptRepository
import dev.andrej.echo.data.testDatabase
import dev.andrej.echo.data.NewTodo
import dev.andrej.echo.data.PendingAnalysis
import dev.andrej.echo.data.Transcript
import dev.andrej.echo.data.Folder
import dev.andrej.echo.transcribe.TranscriptionStatus
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
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `deleting a transcript also clears its derived rows`() = runTest {
        val repository = SqlTranscriptRepository(testDatabase(), dispatcher)
        val derived = SqlDerivedRepository(testDatabase(), dispatcher)
        val queue = SqlAnalysisQueueRepository(testDatabase(), dispatcher)
        val worker = PendingAnalysisWorker(
            queue = queue,
            transcripts = repository,
            derived = derived,
            analyzer = TranscriptAnalyzer(
                { FakeLlmRunner(availability = LlmAvailability.Unsupported("no model")) },
                zone = ZoneId.of("UTC"),
                logDebug = {},
                logWarn = {},
            ),
            scope = CoroutineScope(dispatcher),
        )
        val saved = repository.save(text = "call the plumber", language = "en-GB", durationMs = 0)
        derived.replaceFor(saved.id, listOf(NewTodo("call the plumber", 1_000L, false, true)))
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
    fun `rows are newest first with a relative date`() {
        val now = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 12) }.timeInMillis
        val day = TimeUnit.DAYS.toMillis(1)

        val rows = rows(
            listOf(
                transcript("older", now - 3 * day),
                transcript("today", now),
                transcript("yesterday", now - day),
            ),
            now,
        )

        assertEquals(listOf("today", "yesterday", "older"), rows.map { it.id })
        assertTrue(rows[0].date.startsWith("Today, "))
        assertTrue(rows[1].date.startsWith("Yesterday, "))
    }

    @Test
    fun `a row carries its folder`() {
        val folder = Folder("f", "Work", 2, "Work")

        val row = rows(listOf(transcript("a", 1_000L).copy(folderId = "f")), 1_000L, folders = listOf(folder)).single()

        assertEquals(folder, row.folder)
    }

    @Test
    fun `a row picks up its pending copy`() {
        val work = Work(queued = mapOf("a" to PendingAnalysis("a", 0, lastBlock = AnalysisBlock.EmptyResult)))

        val row = rows(listOf(transcript("a", 1_000L)), 1_000L, work).single()

        assertEquals(PendingSeverity.Failed, row.pending?.severity)
    }

    @Test
    fun `a row with nothing in flight has no pending copy`() {
        assertNull(rows(listOf(transcript("a", 1_000L)), 1_000L).single().pending)
    }

    @Test
    fun `transcription progress drives the stage`() {
        val t = transcript("a", 0)

        assertEquals(NoteStage.Waiting, stage(t, Work(statuses = mapOf("a" to TranscriptionStatus.Queued))))
        assertEquals(
            NoteStage.Transcribing(0.4f),
            stage(t, Work(statuses = mapOf("a" to TranscriptionStatus.Transcribing(0.4f)))),
        )
        assertEquals(
            NoteStage.Analyzing,
            stage(t, Work(statuses = mapOf("a" to TranscriptionStatus.FindingSpeakers(0.5f)))),
        )
        assertEquals(NoteStage.Failed, stage(t, Work(statuses = mapOf("a" to TranscriptionStatus.Failed))))
    }

    @Test
    fun `analysis queue drives the stage once transcribed`() {
        val t = transcript("a", 0)
        fun queued(block: AnalysisBlock?) = Work(queued = mapOf("a" to PendingAnalysis("a", 0, lastBlock = block)))

        assertEquals(NoteStage.Analyzing, stage(t, Work(analyzingId = "a")))
        assertEquals(NoteStage.Analyzing, stage(t, queued(null)))
        assertEquals(NoteStage.Waiting, stage(t, queued(AnalysisBlock.WaitingForModel(1))))
        assertEquals(NoteStage.Failed, stage(t, queued(AnalysisBlock.GenerationFailed("x"))))
    }

    @Test
    fun `a note is done only once analyzed`() {
        assertEquals(NoteStage.Waiting, stage(transcript("a", 0), Work()))
        assertEquals(NoteStage.Done, stage(transcript("a", 0).copy(analyzedAt = 1), Work()))
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
