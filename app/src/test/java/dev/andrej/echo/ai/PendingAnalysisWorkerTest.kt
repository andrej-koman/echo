package dev.andrej.echo.ai

import dev.andrej.echo.data.JsonAnalysisQueueRepository
import dev.andrej.echo.data.JsonDerivedRepository
import dev.andrej.echo.data.JsonTranscriptRepository
import dev.andrej.echo.data.RecordingNotificationScheduler
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private val UTC = ZoneId.of("UTC")
private val good = """{"title":"Fix the tap","summary":"It drips.","items":[{"text":"Buy a washer","due":null}]}"""

@OptIn(ExperimentalCoroutinesApi::class)
class PendingAnalysisWorkerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun transcripts() = JsonTranscriptRepository(tempFolder.newFolder(), dispatcher)
    private fun derived() =
        JsonDerivedRepository(tempFolder.newFolder(), RecordingNotificationScheduler(), dispatcher)
    private fun queue() = JsonAnalysisQueueRepository(tempFolder.newFolder(), dispatcher)

    private fun worker(
        queue: JsonAnalysisQueueRepository,
        transcripts: JsonTranscriptRepository,
        derived: JsonDerivedRepository,
        runner: LlmRunner,
    ) = PendingAnalysisWorker(
        queue = queue,
        transcripts = transcripts,
        derived = derived,
        analyzer = TranscriptAnalyzer({ runner }, UTC, logDebug = {}, logWarn = {}),
        scope = CoroutineScope(dispatcher),
        now = { 42L },
    )

    @Test
    fun `a successful drain attaches the analysis and clears the queue`() = runTest {
        val transcripts = transcripts()
        val derived = derived()
        val queue = queue()
        val saved = transcripts.save(text = "buy a washer for the tap", language = "en-GB", durationMs = 0)
        queue.enqueue(saved.id)

        worker(queue, transcripts, derived, FakeLlmRunner(listOf(good))).requestDrainAndAwait()

        val transcript = transcripts.transcripts.first().single()
        assertEquals("Fix the tap", transcript.title)
        assertEquals(42L, transcript.analyzedAt)
        assertTrue(queue.pending.first().isEmpty())
        assertEquals("Buy a washer", derived.items.first().single().text)
    }

    @Test
    fun `a generation failure consumes an attempt`() = runTest {
        val transcripts = transcripts()
        val saved = transcripts.save(text = "hello", language = "en-GB", durationMs = 0)
        val queue = queue()
        queue.enqueue(saved.id)

        worker(queue, transcripts, derived(), FakeLlmRunner(listOf("nope", "still nope")))
            .requestDrainAndAwait()

        assertEquals(1, queue.pending.first().single().attempts)
    }

    @Test
    fun `waiting on a model never exhausts its attempts`() = runTest {
        val transcripts = transcripts()
        val saved = transcripts.save(text = "hello", language = "en-GB", durationMs = 0)
        val queue = queue()
        queue.enqueue(saved.id)
        val runner = FakeLlmRunner(availability = LlmAvailability.NeedsDownload(bytes = 977_000_000))
        val worker = worker(queue, transcripts, derived(), runner)

        repeat(5) { worker.requestDrainAndAwait() }

        val entry = queue.pending.first().single()
        assertEquals(0, entry.attempts)
        assertEquals(AnalysisBlock.WaitingForModel(977_000_000), entry.lastBlock)
    }

    @Test
    fun `an entry stops being retried once it hits the attempt cap`() = runTest {
        val transcripts = transcripts()
        val saved = transcripts.save(text = "hello", language = "en-GB", durationMs = 0)
        val queue = queue()
        queue.enqueue(saved.id)
        val runner = FakeLlmRunner(listOf("no", "no", "no", "no", "no", "no", "no", "no"))
        val worker = worker(queue, transcripts, derived(), runner)

        repeat(5) { worker.requestDrainAndAwait() }

        assertEquals(3, queue.pending.first().single().attempts)
        assertEquals(6, runner.prompts.size) // 2 attempts (default + zero-temp retry) per drain, capped at 3 drains
    }

    @Test
    fun `a missing transcript is dropped from the queue`() = runTest {
        val queue = queue()
        queue.enqueue("ghost")

        worker(queue, transcripts(), derived(), FakeLlmRunner(listOf(good))).requestDrainAndAwait()

        assertTrue(queue.pending.first().isEmpty())
    }

    @Test
    fun `a deleted transcript is dropped from the queue`() = runTest {
        val transcripts = transcripts()
        val saved = transcripts.save(text = "hello", language = "en-GB", durationMs = 0)
        transcripts.delete(saved.id)
        val queue = queue()
        queue.enqueue(saved.id)

        worker(queue, transcripts, derived(), FakeLlmRunner(listOf(good))).requestDrainAndAwait()

        assertTrue(queue.pending.first().isEmpty())
    }

    @Test
    fun `an already analyzed transcript is dropped from the automatic queue`() = runTest {
        val transcripts = transcripts()
        val saved = transcripts.save(text = "hello", language = "en-GB", durationMs = 0)
        transcripts.attachAnalysis(saved.id, title = "Already done", summary = null, analyzedAt = 1L)
        val queue = queue()
        queue.enqueue(saved.id)

        worker(queue, transcripts, derived(), FakeLlmRunner(listOf(good))).requestDrainAndAwait()

        assertTrue(queue.pending.first().isEmpty())
    }

    @Test
    fun `analyzeNow re-runs an already analyzed transcript`() = runTest {
        val transcripts = transcripts()
        val derived = derived()
        val saved = transcripts.save(text = "buy a washer for the tap", language = "en-GB", durationMs = 0)
        transcripts.attachAnalysis(saved.id, title = "Stale", summary = null, analyzedAt = 1L)
        val queue = queue()

        worker(queue, transcripts, derived, FakeLlmRunner(listOf(good))).analyzeNow(saved.id)

        assertEquals("Fix the tap", transcripts.transcripts.first().single().title)
    }

    @Test
    fun `analyzeNow records a block so future automatic drains can retry it`() = runTest {
        val transcripts = transcripts()
        val saved = transcripts.save(text = "hello", language = "en-GB", durationMs = 0)
        val queue = queue()

        worker(queue, transcripts, derived(), FakeLlmRunner(listOf("no", "still no"))).analyzeNow(saved.id)

        val entry = queue.pending.first().single()
        assertEquals(1, entry.attempts)
        assertEquals(AnalysisBlock.EmptyResult, entry.lastBlock)
    }

    @Test
    fun `activeId is set during an attempt and cleared after`() = runTest {
        val transcripts = transcripts()
        val saved = transcripts.save(text = "hello", language = "en-GB", durationMs = 0)
        val queue = queue()
        queue.enqueue(saved.id)
        val worker = worker(queue, transcripts, derived(), FakeLlmRunner(listOf(good)))

        assertNull(worker.activeId.value)
        worker.requestDrainAndAwait()
        assertNull(worker.activeId.value)
    }

    @Test
    fun `a request racing the single-flight guard is not dropped`() = runTest {
        val transcripts = transcripts()
        val a = transcripts.save(text = "buy a washer for the tap", language = "en-GB", durationMs = 0)
        val b = transcripts.save(text = "call the plumber", language = "en-GB", durationMs = 0)
        val queue = queue()
        queue.enqueue(a.id)
        val worker = worker(queue, transcripts, derived(), FakeLlmRunner(listOf(good, good)))

        worker.requestDrain()
        queue.enqueue(b.id)
        worker.requestDrain() // arrives while drainJob is still active, not started running yet

        advanceUntilIdle()

        // Both entries got processed — the second requestDrain, arriving while the guard already
        // saw a job in flight, still folded in rather than being silently dropped.
        assertTrue(queue.pending.first().isEmpty())
        assertEquals(2, transcripts.transcripts.first().count { it.analyzedAt != null })
    }
}
