package dev.andrej.echo.ui.record

import dev.andrej.echo.RecordingController
import dev.andrej.echo.data.SqlTranscriptRepository
import dev.andrej.echo.data.testDatabase
import dev.andrej.echo.speech.Interruptions
import dev.andrej.echo.speech.Recorder
import dev.andrej.echo.speech.SAMPLE_RATE
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class RecordingControllerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // RecordViewModel is a ViewModel, which reaches for Main.
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Each call writes [perCall] seconds at [level], then waits to be cancelled (or fails). */
    private class FakeRecorder(
        private val perCall: Int = 1,
        private val fail: Boolean = false,
        private val level: Float = 0.5f,
    ) : Recorder {
        var calls = 0

        override suspend fun record(target: File, onBuffer: (Float, Long) -> Unit) {
            calls++
            if (fail) throw IOException("mic busy")
            target.appendBytes(ByteArray(perCall * SAMPLE_RATE * 2))
            onBuffer(level, target.length() / 2)
            awaitCancellation()
        }
    }

    private class FakeInterruptions : Interruptions {
        var onInterrupted: () -> Unit = {}
        var onOver: () -> Unit = {}
        var active = false

        override fun start(onInterrupted: () -> Unit, onOver: () -> Unit) {
            this.onInterrupted = onInterrupted
            this.onOver = onOver
            active = true
        }

        override fun stop() {
            active = false
        }
    }

    // In place, not on Dispatchers.IO: advanceUntilIdle() can't wait for a real thread.
    private val repository = SqlTranscriptRepository(testDatabase(), Dispatchers.Unconfined)
    private val recorded = mutableListOf<String>()
    private val discarded = mutableListOf<String>()

    private fun TestScope.controller(
        recorder: Recorder = FakeRecorder(),
        interruptions: Interruptions = FakeInterruptions(),
    ) = RecordingController(
        recorder = recorder,
        interruptions = interruptions,
        repository = repository,
        audioFile = { File(tempFolder.root, "$it.pcm") },
        onRecorded = { recorded += it },
        onDiscarded = { discarded += it },
        scope = CoroutineScope(coroutineContext),
        clock = { 42L },
    )

    @Test
    fun `recording reports how much audio is in the file`() = runTest {
        val controller = controller(FakeRecorder(perCall = 3))

        controller.startRecording()
        advanceUntilIdle()

        val state = controller.state.value
        assertTrue(state.isRecording)
        assertEquals(3_000L, state.elapsedMs)
        assertEquals(0.5f, state.level)
        controller.discard()
    }

    @Test
    fun `stopping files the take and hands it to transcription`() = runTest {
        val controller = controller(FakeRecorder(perCall = 2))
        controller.startRecording()
        advanceUntilIdle()

        controller.stopRecording()
        advanceUntilIdle()

        val saved = repository.transcripts.first().single()
        assertEquals(listOf(saved.id), recorded)
        assertEquals(2_000L, saved.durationMs)
        assertEquals(42L, saved.createdAt)
        assertEquals("", saved.text)
        assertFalse(controller.state.value.isRecording)
    }

    @Test
    fun `a take with no audio is thrown away`() = runTest {
        val controller = controller(FakeRecorder(perCall = 0))
        controller.startRecording()
        advanceUntilIdle()

        controller.stopRecording()
        advanceUntilIdle()

        assertTrue(repository.transcripts.first().isEmpty())
        assertTrue(recorded.isEmpty())
        assertEquals(1, discarded.size)
    }

    @Test
    fun `a take where nothing was said is thrown away`() = runTest {
        val controller = controller(FakeRecorder(perCall = 5, level = 0.1f))
        controller.startRecording()
        advanceUntilIdle()

        controller.stopRecording()
        advanceUntilIdle()

        assertTrue(repository.transcripts.first().isEmpty())
        assertTrue(recorded.isEmpty())
        assertEquals(1, discarded.size)
    }

    @Test
    fun `discarding keeps nothing`() = runTest {
        val interruptions = FakeInterruptions()
        val controller = controller(interruptions = interruptions)
        controller.startRecording()
        advanceUntilIdle()

        controller.discard()
        advanceUntilIdle()

        assertTrue(repository.transcripts.first().isEmpty())
        assertTrue(recorded.isEmpty())
        assertEquals(1, discarded.size)
        assertFalse(interruptions.active)
    }

    @Test
    fun `pause and resume append to the same take`() = runTest {
        val recorder = FakeRecorder(perCall = 1)
        val controller = controller(recorder)
        controller.startRecording()
        advanceUntilIdle()

        controller.pause()
        advanceUntilIdle()
        assertTrue(controller.state.value.isPaused)

        controller.resume()
        advanceUntilIdle()
        controller.stopRecording()
        advanceUntilIdle()

        assertEquals(2, recorder.calls)
        assertEquals(2_000L, repository.transcripts.first().single().durationMs)
    }

    @Test
    fun `a call pauses the take and its end resumes it`() = runTest {
        val interruptions = FakeInterruptions()
        val controller = controller(interruptions = interruptions)
        controller.startRecording()
        advanceUntilIdle()

        interruptions.onInterrupted()
        assertTrue(controller.state.value.isPaused)
        assertTrue(controller.state.value.interrupted)

        interruptions.onOver()
        assertFalse(controller.state.value.isPaused)
        controller.discard()
    }

    @Test
    fun `the end of a call does not undo a pause the user made`() = runTest {
        val interruptions = FakeInterruptions()
        val controller = controller(interruptions = interruptions)
        controller.startRecording()
        advanceUntilIdle()

        controller.pause()
        interruptions.onOver()

        assertTrue(controller.state.value.isPaused)
        controller.discard()
    }

    @Test
    fun `a busy microphone pauses the take instead of losing it`() = runTest {
        val controller = controller(FakeRecorder(fail = true))
        controller.startRecording()
        advanceUntilIdle()

        val state = controller.state.value
        assertTrue(state.isRecording)
        assertTrue(state.isPaused)
        assertTrue(state.micFailed)
        controller.discard()
    }

    @Test
    fun `starting twice keeps one take`() = runTest {
        val recorder = FakeRecorder()
        val controller = controller(recorder)

        controller.startRecording()
        controller.startRecording()
        advanceUntilIdle()

        assertEquals(1, recorder.calls)
        controller.discard()
    }

    @Test
    fun `the view model warms the model up and starts the foreground service`() = runTest {
        var warmed = false
        var started = false
        val viewModel = RecordViewModel(controller(), onStarted = { started = true }, warmup = { warmed = true })

        assertFalse(warmed)
        assertFalse(started)

        viewModel.startRecording()
        advanceUntilIdle()

        assertTrue(warmed)
        assertTrue(started)
        viewModel.discard()
    }
    @Test fun `free recording prompts once after two minutes and next take prompts again`() = runTest {
        val controller = controller(FakeRecorder(perCall = 121))
        val billing = dev.andrej.echo.billing.FakeBilling(dev.andrej.echo.data.FakeSettingsStore())
        val vm = RecordViewModel(controller, isPro = billing.isPro)
        vm.startRecording()
        advanceUntilIdle()
        assertTrue(vm.upgradeRequested.value)
        vm.consumeUpgrade()
        controller.pause()
        advanceUntilIdle()
        controller.resume()
        advanceUntilIdle()
        assertFalse(vm.upgradeRequested.value)
        vm.discard()
        advanceUntilIdle()
        vm.startRecording()
        advanceUntilIdle()
        assertTrue(vm.upgradeRequested.value)
        vm.discard()
    }

    @Test fun `pro recording passes two minutes without prompting`() = runTest {
        val billing = dev.andrej.echo.billing.FakeBilling(dev.andrej.echo.data.FakeSettingsStore())
        billing.setPro(true)
        val vm = RecordViewModel(controller(FakeRecorder(perCall = 121)), isPro = billing.isPro)
        vm.startRecording()
        advanceUntilIdle()
        assertFalse(vm.upgradeRequested.value)
        vm.discard()
    }

}
