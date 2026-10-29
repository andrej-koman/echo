package dev.andrej.echo.ui.onboarding

import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingViewModelTest {

    private var downloadsStarted = 0
    private var finished = 0

    private fun viewModel() = OnboardingViewModel(
        needsDownload = flowOf(false),
        onStartDownloads = { downloadsStarted++ },
        onDownloadNow = {},
        onFinish = { finished++ },
    )

    @Test
    fun `get started kicks off downloads and moves to the first explainer`() {
        val viewModel = viewModel()

        viewModel.getStarted()

        assertEquals(OnboardingStep.VoiceToTasks, viewModel.step.value)
        assertEquals(1, downloadsStarted)
    }

    @Test
    fun `skip lands on the microphone step, not past it`() {
        val viewModel = viewModel()
        viewModel.getStarted()

        viewModel.skipToMicrophone()

        assertEquals(OnboardingStep.Microphone, viewModel.step.value)
        assertEquals(0, finished)
    }

    @Test
    fun `a granted microphone goes to the first note`() {
        val viewModel = viewModel()
        viewModel.skipToMicrophone()

        viewModel.onMicrophoneResult(granted = true)

        assertEquals(OnboardingStep.FirstNote, viewModel.step.value)
        assertEquals(0, finished)
    }

    @Test
    fun `a denied microphone finishes onboarding`() {
        val viewModel = viewModel()
        viewModel.skipToMicrophone()

        viewModel.onMicrophoneResult(granted = false)

        assertEquals(1, finished)
    }

    @Test
    fun `back steps through the flow and refuses on welcome`() {
        val viewModel = viewModel()
        viewModel.getStarted()
        viewModel.next()

        assertTrue(viewModel.back())
        assertEquals(OnboardingStep.VoiceToTasks, viewModel.step.value)
        assertTrue(viewModel.back())
        assertFalse(viewModel.back())
        assertEquals(OnboardingStep.Welcome, viewModel.step.value)
    }
}
