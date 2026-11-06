package dev.andrej.echo.ui.onboarding

import dev.andrej.echo.data.FakeSettingsStore
import dev.andrej.echo.speech.whisper.WHISPER_BASE
import dev.andrej.echo.speech.whisper.WHISPER_LARGE_V3_TURBO
import dev.andrej.echo.speech.whisper.WHISPER_MEDIUM
import dev.andrej.echo.speech.whisper.WHISPER_SMALL
import dev.andrej.echo.speech.whisper.WhisperModel
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingViewModelTest {

    private val settings = FakeSettingsStore()
    private val downloads = mutableListOf<Boolean>()
    private var finished = 0
    private val big = listOf(WHISPER_LARGE_V3_TURBO, WHISPER_MEDIUM, WHISPER_SMALL, WHISPER_BASE)
    private val small = listOf(WHISPER_SMALL, WHISPER_BASE)

    private fun viewModel(choices: List<WhisperModel> = big) = OnboardingViewModel(
        settings = settings,
        speechChoices = choices,
        defaultSpeech = choices.first(),
        currentSpeech = choices.first(),
        speechReady = { false },
        summaryFor = { _, _ -> emptyFlow() },
        setupStatus = emptyFlow(),
        onStartDownloads = { downloads += it },
        onFinish = { finished++ },
        languageOptions = { emptyList() },
    )

    @Test
    fun `get started moves on without downloading`() {
        val viewModel = viewModel()

        viewModel.getStarted()

        assertEquals(OnboardingStep.HowItWorks, viewModel.state.value.step)
        assertTrue(downloads.isEmpty())
    }

    @Test
    fun `steps run in order`() {
        val viewModel = viewModel()
        val seen = mutableListOf(viewModel.state.value.step)

        repeat(5) { viewModel.next(); seen += viewModel.state.value.step }

        assertEquals(OnboardingStep.entries, seen)
    }

    @Test
    fun `back keeps choices and refuses on welcome`() {
        val viewModel = viewModel()
        viewModel.getStarted()
        viewModel.next()
        viewModel.toggleUse(RecordUse.Meetings)
        viewModel.setPriority(Priority.Faster)
        viewModel.next()

        assertTrue(viewModel.back())

        assertEquals(OnboardingStep.Record, viewModel.state.value.step)
        assertTrue(RecordUse.Meetings in viewModel.state.value.uses)
        assertEquals(Priority.Faster, viewModel.state.value.priority)
        assertTrue(viewModel.back())
        assertTrue(viewModel.back())
        assertFalse(viewModel.back())
    }

    @Test
    fun `skip jumps to setup and leaves settings alone`() {
        settings.speechModel = WHISPER_BASE.id
        settings.detectSpeakers = true
        settings.transcriptionLanguage = "sl"
        val viewModel = viewModel()
        viewModel.getStarted()
        viewModel.next()

        viewModel.skip()

        assertEquals(OnboardingStep.Setup, viewModel.state.value.step)
        assertEquals(WHISPER_BASE.id, settings.speechModel)
        assertTrue(settings.detectSpeakers)
        assertEquals("sl", settings.transcriptionLanguage)
    }

    @Test
    fun `continuing from priority applies the choices`() {
        val viewModel = viewModel()
        viewModel.getStarted()
        viewModel.next()
        viewModel.toggleUse(RecordUse.QuickThoughts)
        viewModel.toggleUse(RecordUse.Meetings)
        viewModel.setLanguage("sl")
        viewModel.next()
        viewModel.setPriority(Priority.Faster)

        viewModel.next()

        assertEquals(OnboardingStep.Setup, viewModel.state.value.step)
        assertEquals(WHISPER_SMALL.id, settings.speechModel)
        assertTrue(settings.detectSpeakers)
        assertEquals("sl", settings.transcriptionLanguage)
        assertEquals(WHISPER_SMALL, viewModel.state.value.speech)
    }

    @Test
    fun `untouched uses keep the existing speaker setting`() {
        settings.detectSpeakers = true
        val viewModel = viewModel()
        viewModel.skip()
        viewModel.back()
        viewModel.back()
        viewModel.getStarted()
        viewModel.skip()
        viewModel.back() // Priority via Setup
        viewModel.next()

        assertTrue(settings.detectSpeakers)
    }

    @Test
    fun `auto-detect clears the stored language`() {
        settings.transcriptionLanguage = "sl"
        val viewModel = viewModel()
        viewModel.setLanguage("")
        viewModel.skip()
        viewModel.back()
        viewModel.next()

        assertNull(settings.transcriptionLanguage)
    }

    @Test
    fun `accurate is hidden where the large model does not fit`() {
        assertTrue(viewModel(big).priorityOptions().any { it.priority == Priority.Accurate })
        assertFalse(viewModel(small).priorityOptions().any { it.priority == Priority.Accurate })
    }

    @Test
    fun `comparing models selects one for real`() {
        val viewModel = viewModel()

        viewModel.selectSpeech(WHISPER_BASE)

        assertEquals(WHISPER_BASE, viewModel.state.value.speech)
        assertEquals(WHISPER_BASE.id, settings.speechModel)
    }

    @Test
    fun `downloads start only from the explicit buttons`() {
        val viewModel = viewModel()
        viewModel.skip()

        viewModel.downloadOnWifi()
        assertEquals(OnboardingStep.FirstNote, viewModel.state.value.step)
        viewModel.back()
        viewModel.downloadWithMobileData()

        assertEquals(listOf(true, false), downloads)
    }

    @Test
    fun `set up later does not download`() {
        val viewModel = viewModel()
        viewModel.skip()

        viewModel.setUpLater()

        assertEquals(OnboardingStep.FirstNote, viewModel.state.value.step)
        assertTrue(downloads.isEmpty())
    }

    @Test
    fun `a denied microphone stays on the first note`() {
        val viewModel = viewModel()
        viewModel.skip()
        viewModel.setUpLater()

        assertFalse(viewModel.onMicrophoneResult(granted = false))

        assertEquals(OnboardingStep.FirstNote, viewModel.state.value.step)
        assertTrue(viewModel.state.value.microphoneBlocked)
        assertEquals(0, finished)
    }

    @Test
    fun `a granted microphone finishes and rewinds`() {
        val viewModel = viewModel()
        viewModel.skip()
        viewModel.setUpLater()

        assertTrue(viewModel.onMicrophoneResult(granted = true))

        assertEquals(1, finished)
        assertEquals(OnboardingStep.Welcome, viewModel.state.value.step)
    }
}
