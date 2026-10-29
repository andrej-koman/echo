package dev.andrej.echo.ui.onboarding

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OnboardingStep { Welcome, VoiceToTasks, Privacy, Microphone, FirstNote }

class OnboardingViewModel(
    /** True while the LLM is neither on the device nor downloading — step 4 offers it then. */
    val needsDownload: Flow<Boolean>,
    private val onStartDownloads: () -> Unit,
    private val onDownloadNow: () -> Unit,
    private val onFinish: () -> Unit,
) : ViewModel() {

    private val _step = MutableStateFlow(OnboardingStep.Welcome)
    val step: StateFlow<OnboardingStep> = _step.asStateFlow()

    fun getStarted() {
        onStartDownloads()
        _step.value = OnboardingStep.VoiceToTasks
    }

    fun next() {
        val steps = OnboardingStep.entries
        _step.value = steps[(_step.value.ordinal + 1).coerceAtMost(steps.lastIndex)]
    }

    fun skipToMicrophone() {
        _step.value = OnboardingStep.Microphone
    }

    /** False on Welcome, so the system back can leave the app. */
    fun back(): Boolean {
        if (_step.value == OnboardingStep.Welcome) return false
        _step.value = OnboardingStep.entries[_step.value.ordinal - 1]
        return true
    }

    fun onMicrophoneResult(granted: Boolean) {
        if (granted) _step.value = OnboardingStep.FirstNote else finish()
    }

    fun downloadNow() = onDownloadNow()

    /** Rewinds too: the ViewModel outlives the flow, so a debug replay must not resume on the last step. */
    fun finish() {
        _step.value = OnboardingStep.Welcome
        onFinish()
    }
}
