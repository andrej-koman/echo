package dev.andrej.echo.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.andrej.echo.ai.AiCardState
import dev.andrej.echo.ai.NotesAiModel
import dev.andrej.echo.ai.NotesAiModelStatus
import dev.andrej.echo.data.DerivedRepository
import dev.andrej.echo.data.ImportDay
import dev.andrej.echo.data.SettingsStore
import dev.andrej.echo.data.StorageUsage
import dev.andrej.echo.data.TranscriptRepository
import dev.andrej.echo.speech.whisper.WhisperModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** [code] is a Whisper language code such as `sl`; empty means auto-detect. */
data class LanguageOption(
    val code: String,
    val label: String,
    val flag: String,
    val dividerBefore: Boolean = false,
)

data class SettingsUiState(
    val languageCode: String = "",
    val languageLabel: String = "",
    val languages: List<LanguageOption> = emptyList(),
    val speechModel: WhisperModel? = null,
    val speechModels: List<WhisperModel> = emptyList(),
    val importDay: ImportDay? = null,
    val litertModelBytes: Long = 0,
    val autoAnalyze: Boolean = true,
    val detectSpeakers: Boolean = true,
    val wifiOnlyDownload: Boolean = false,
    val storage: StorageUsage = StorageUsage(emptyList()),
    val notesCount: Int = 0,
    val tasksCount: Int = 0,
    val notesAiModels: List<NotesAiModelStatus> = emptyList(),
) {
    val storageBytes: Long get() = storage.totalBytes
}

class SettingsViewModel(
    private val settings: SettingsStore,
    private val whisperLanguages: () -> List<String>,
    private val speechModels: List<WhisperModel> = emptyList(),
    private val currentSpeechModel: () -> WhisperModel? = { null },
    transcripts: TranscriptRepository,
    derived: DerivedRepository,
    val aiCardState: Flow<AiCardState>,
    notesAiModelStates: Flow<List<NotesAiModelStatus>> = kotlinx.coroutines.flow.flowOf(emptyList()),
    private val litertModelBytes: Long,
    val onDownloadModel: (NotesAiModel) -> Unit,
    val onCancelDownload: (String?) -> Unit,
    val onDeleteModel: (String) -> Unit,
    val onSelectNotesAiModel: (NotesAiModel) -> Unit = {},
    private val storageUsage: () -> StorageUsage,
    val onTestModelDownloadNotification: () -> Unit = {},
    private val onDeleteAllData: suspend () -> Unit = {},
    val onReplayOnboarding: () -> Unit = {},
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(fromSettings(notesCount = 0, tasksCount = 0))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refreshStorage()
        viewModelScope.launch {
            combine(transcripts.transcripts, derived.items) { t, d -> t.size to d.size }
                .collect { (notesCount, tasksCount) ->
                    _state.value = fromSettings(notesCount, tasksCount)
                        .copy(
                            languages = _state.value.languages,
                            storage = _state.value.storage,
                            notesAiModels = _state.value.notesAiModels,
                        )
                }
        }
        refreshLanguages()
        viewModelScope.launch {
            notesAiModelStates.collect { models ->
                _state.value = _state.value.copy(notesAiModels = models)
            }
        }
    }

    private fun fromSettings(notesCount: Int, tasksCount: Int) = SettingsUiState(
        languageCode = settings.transcriptionLanguage.orEmpty(),
        languageLabel = settings.transcriptionLanguage?.let(::languageLabel) ?: AUTO_DETECT_VALUE,
        speechModel = currentSpeechModel(),
        speechModels = speechModels,
        importDay = settings.importDay,
        litertModelBytes = litertModelBytes,
        autoAnalyze = settings.autoAnalyze,
        detectSpeakers = settings.detectSpeakers,
        wifiOnlyDownload = settings.wifiOnlyDownload,
        notesCount = notesCount,
        tasksCount = tasksCount,
    )

    fun refreshStorage() {
        viewModelScope.launch {
            val usage = withContext(computeDispatcher) { storageUsage() }
            _state.value = _state.value.copy(storage = usage)
        }
    }

    private fun refreshLanguages() {
        viewModelScope.launch {
            val languages = withContext(computeDispatcher) {
                buildLanguageOptions(whisperLanguages())
            }
            _state.value = _state.value.copy(languages = languages)
        }
    }

    /** Empty [code] means auto-detect. */
    fun setLanguage(code: String) {
        settings.transcriptionLanguage = code.ifEmpty { null }
        _state.value = _state.value.copy(
            languageCode = code,
            languageLabel = if (code.isEmpty()) AUTO_DETECT_VALUE else languageLabel(code),
        )
    }

    /** Takes effect from the next transcription, which downloads it if needed. */
    fun setSpeechModel(model: WhisperModel) {
        settings.speechModel = model.id
        _state.value = _state.value.copy(speechModel = model)
    }

    /** Null asks on every import. */
    fun setImportDay(day: ImportDay?) {
        settings.importDay = day
        _state.value = _state.value.copy(importDay = day)
    }

    fun setDetectSpeakers(value: Boolean) {
        settings.detectSpeakers = value
        _state.value = _state.value.copy(detectSpeakers = value)
    }

    fun setAutoAnalyze(value: Boolean) {
        settings.autoAnalyze = value
        _state.value = _state.value.copy(autoAnalyze = value)
    }

    fun setWifiOnlyDownload(value: Boolean) {
        settings.wifiOnlyDownload = value
        _state.value = _state.value.copy(wifiOnlyDownload = value)
    }


    fun deleteAllData() {
        viewModelScope.launch {
            onDeleteAllData()
            refreshStorage()
        }
    }
}

private const val AUTO_DETECT_LABEL = "Auto-detect"
private const val AUTO_DETECT_VALUE = "🌐 Auto-detect"

internal fun buildLanguageOptions(codes: List<String>, locale: java.util.Locale = java.util.Locale.getDefault()): List<LanguageOption> {
    val uniqueCodes = codes.distinct()
    val featuredLabels = linkedMapOf("en" to "English", "es" to "Spanish", "de" to "German")
    val featured = featuredLabels.mapNotNull { (code, label) ->
        if (code in uniqueCodes) LanguageOption(code, label, languageFlag(code)) else null
    }
    val remaining = uniqueCodes
        .filterNot { it in featuredLabels }
        .map { code -> LanguageOption(code, displayLanguage(code, locale), languageFlag(code)) }
        .sortedBy { it.label.lowercase(locale) }
        .mapIndexed { index, option -> option.copy(dividerBefore = index == 0) }

    return listOf(LanguageOption("", AUTO_DETECT_LABEL, "🌐")) + featured + remaining
}

internal fun languageLabel(code: String): String = "${languageFlag(code)} ${displayLanguage(code)}"

private val LANGUAGE_COUNTRIES = mapOf(
    "af" to "ZA", "am" to "ET", "ar" to "SA", "as" to "IN", "az" to "AZ", "ba" to "RU",
    "be" to "BY", "bg" to "BG", "bn" to "BD", "bo" to "CN", "br" to "FR", "bs" to "BA",
    "ca" to "ES", "cs" to "CZ", "cy" to "GB", "da" to "DK", "de" to "DE", "el" to "GR",
    "en" to "GB", "es" to "ES", "et" to "EE", "eu" to "ES", "fa" to "IR", "fi" to "FI",
    "fo" to "FO", "fr" to "FR", "gl" to "ES", "gu" to "IN", "ha" to "NG", "haw" to "US",
    "he" to "IL", "hi" to "IN", "hr" to "HR", "ht" to "HT", "hu" to "HU", "hy" to "AM",
    "id" to "ID", "is" to "IS", "it" to "IT", "ja" to "JP", "jw" to "ID", "ka" to "GE",
    "kk" to "KZ", "km" to "KH", "kn" to "IN", "ko" to "KR", "la" to "VA", "lb" to "LU",
    "ln" to "CD", "lo" to "LA", "lt" to "LT", "lv" to "LV", "mg" to "MG", "mi" to "NZ",
    "mk" to "MK", "ml" to "IN", "mn" to "MN", "mr" to "IN", "ms" to "MY", "mt" to "MT",
    "my" to "MM", "ne" to "NP", "nl" to "NL", "nn" to "NO", "no" to "NO", "oc" to "FR",
    "pa" to "IN", "pl" to "PL", "ps" to "AF", "pt" to "PT", "ro" to "RO", "ru" to "RU",
    "sa" to "IN", "sd" to "PK", "si" to "LK", "sk" to "SK", "sl" to "SI", "sn" to "ZW",
    "so" to "SO", "sq" to "AL", "sr" to "RS", "su" to "ID", "sv" to "SE", "sw" to "TZ",
    "ta" to "IN", "te" to "IN", "tg" to "TJ", "th" to "TH", "tk" to "TM", "tl" to "PH",
    "tr" to "TR", "tt" to "RU", "uk" to "UA", "ur" to "PK", "uz" to "UZ", "vi" to "VN",
    "yi" to "IL", "yo" to "NG", "yue" to "HK", "zh" to "CN",
)

internal fun languageFlag(code: String): String {
    val country = LANGUAGE_COUNTRIES[code.lowercase()]
        ?: java.util.Locale.forLanguageTag(code).country.takeIf { it.length == 2 }
        ?: return "🏳️"
    return country.uppercase().map { char -> String(Character.toChars(0x1F1E6 + char.code - 'A'.code)) }
        .joinToString("")
}

internal fun displayLanguage(code: String, locale: java.util.Locale = java.util.Locale.getDefault()): String =
    java.util.Locale.forLanguageTag(code).getDisplayName(locale)
        .ifBlank { code }
        .replaceFirstChar { it.titlecase() }

internal fun formatBytes(bytes: Long): String {
    return when {
        bytes == 0L -> "0 MB"
        bytes < 100_000 -> "< 0.1 MB"
        bytes < 1_000_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        else -> "%.2f GB".format(bytes / 1_000_000_000.0)
    }
}
