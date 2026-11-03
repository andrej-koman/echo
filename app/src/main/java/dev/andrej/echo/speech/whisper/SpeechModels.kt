package dev.andrej.echo.speech.whisper

import dev.andrej.echo.ai.HttpModelDownloader
import dev.andrej.echo.ai.ModelStore
import dev.andrej.echo.data.SettingsStore
import java.io.File

/** The Whisper model the user picked in Settings, or the one for this phone's memory. */
class SpeechModels(directory: File, totalMemoryBytes: Long, private val settings: SettingsStore) {

    val choices = whisperChoicesFor(totalMemoryBytes)
    private val default = whisperModelFor(totalMemoryBytes)
    private val stores = choices.associateWith { ModelStore(directory, it.spec, HttpModelDownloader()) }

    val current: WhisperModel get() = choices.find { it.id == settings.speechModel } ?: default

    fun store(model: WhisperModel = current): ModelStore = stores.getValue(model)

    /** A switch leaves the old model on disk until the new one is in use, then frees its space. */
    suspend fun deleteAllBut(model: WhisperModel) {
        stores.filterKeys { it != model }.values.forEach { it.delete() }
    }
}
