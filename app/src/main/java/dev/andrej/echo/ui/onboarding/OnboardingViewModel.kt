package dev.andrej.echo.ui.onboarding

import androidx.lifecycle.ViewModel
import dev.andrej.echo.data.SettingsStore
import dev.andrej.echo.speech.whisper.WhisperModel
import dev.andrej.echo.ui.profile.LanguageOption
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

enum class OnboardingStep { Welcome, HowItWorks, Record, Priority, Setup, FirstNote }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val uses: Set<RecordUse> = setOf(RecordUse.QuickThoughts),
    /** A Whisper language code; empty auto-detects. */
    val language: String = "",
    val priority: Priority = Priority.HelpMeChoose,
    val speech: WhisperModel,
    val detectSpeakers: Boolean,
    val microphoneBlocked: Boolean = false,
)

data class PriorityOption(val priority: Priority, val speech: WhisperModel, val ready: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModel(
    private val settings: SettingsStore,
    val speechChoices: List<WhisperModel>,
    private val defaultSpeech: WhisperModel,
    currentSpeech: WhisperModel,
    private val speechReady: (WhisperModel) -> Boolean,
    private val summaryFor: (speech: WhisperModel, detectSpeakers: Boolean) -> Flow<SetupSummary>,
    val setupStatus: Flow<SetupStatus>,
    private val onStartDownloads: (requireWifi: Boolean) -> Unit,
    private val onFinish: () -> Unit,
    val languageOptions: () -> List<LanguageOption>,
) : ViewModel() {

    private val _state = MutableStateFlow(
        OnboardingUiState(
            language = settings.transcriptionLanguage.orEmpty(),
            speech = currentSpeech,
            detectSpeakers = settings.detectSpeakers,
        ),
    )
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /** Follows the Setup screen's model and speaker choice. */
    val summary: Flow<SetupSummary> = _state
        .map { it.speech to it.detectSpeakers }
        .distinctUntilChanged()
        .flatMapLatest { (speech, speakers) -> summaryFor(speech, speakers) }

    val accurateAvailable: Boolean = accurateAvailable(speechChoices)

    private var usesTouched = false

    fun priorityOptions(): List<PriorityOption> = Priority.entries
        .filter { it != Priority.Accurate || accurateAvailable }
        .map { priority ->
            val speech = recommend(priority).speech
            PriorityOption(priority, speech, speechReady(speech))
        }

    private fun recommend(priority: Priority = _state.value.priority) =
        recommendSetup(_state.value.uses, priority, speechChoices, defaultSpeech)

    fun getStarted() = go(OnboardingStep.HowItWorks)

    fun next() {
        when (_state.value.step) {
            OnboardingStep.Record -> usesTouched = true
            OnboardingStep.Priority -> apply()
            else -> Unit
        }
        val steps = OnboardingStep.entries
        go(steps[(_state.value.step.ordinal + 1).coerceAtMost(steps.lastIndex)])
    }

    /** Leaves settings as they are; the Setup screen then shows what is currently configured. */
    fun skip() = go(OnboardingStep.Setup)

    /** False on Welcome, so the system back can leave the app. */
    fun back(): Boolean {
        if (_state.value.step == OnboardingStep.Welcome) return false
        go(OnboardingStep.entries[_state.value.step.ordinal - 1])
        return true
    }

    fun toggleUse(use: RecordUse) {
        usesTouched = true
        _state.update { state ->
            val uses = if (use in state.uses) state.uses - use else state.uses + use
            state.copy(uses = uses)
        }
    }

    fun setLanguage(code: String) = _state.update { it.copy(language = code) }

    fun setPriority(priority: Priority) = _state.update { it.copy(priority = priority) }

    fun selectSpeech(model: WhisperModel) {
        settings.speechModel = model.id
        _state.update { it.copy(speech = model) }
    }

    fun downloadOnWifi() = startDownloads(requireWifi = true)

    fun downloadWithMobileData() = startDownloads(requireWifi = false)

    fun retryDownload() = startDownloads(requireWifi = false)

    fun setUpLater() = go(OnboardingStep.FirstNote)

    private fun startDownloads(requireWifi: Boolean) {
        onStartDownloads(requireWifi)
        go(OnboardingStep.FirstNote)
    }

    fun onMicrophoneResult(granted: Boolean): Boolean {
        _state.update { it.copy(microphoneBlocked = !granted) }
        if (granted) finish()
        return granted
    }

    /** Rewinds too: the ViewModel outlives the flow, so a debug replay must not resume on the last step. */
    fun finish() {
        _state.update { it.copy(step = OnboardingStep.Welcome) }
        onFinish()
    }

    private fun apply() {
        val recommendation = recommend()
        val detectSpeakers = if (usesTouched) recommendation.detectSpeakers else settings.detectSpeakers
        val state = _state.value
        settings.transcriptionLanguage = state.language.ifEmpty { null }
        settings.speechModel = recommendation.speech.id
        settings.detectSpeakers = detectSpeakers
        _state.update { it.copy(speech = recommendation.speech, detectSpeakers = detectSpeakers) }
    }

    private fun go(step: OnboardingStep) = _state.update { it.copy(step = step) }
}
