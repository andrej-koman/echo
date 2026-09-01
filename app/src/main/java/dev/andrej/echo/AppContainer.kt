package dev.andrej.echo

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.andrej.echo.data.JsonTranscriptRepository
import dev.andrej.echo.data.SettingsStore
import dev.andrej.echo.data.SharedPreferencesSettingsStore
import dev.andrej.echo.data.TranscriptRepository
import dev.andrej.echo.speech.AndroidSpeechEngine
import dev.andrej.echo.speech.TranscriptionEngine
import dev.andrej.echo.ui.history.HistoryViewModel
import dev.andrej.echo.ui.record.RecordViewModel

/**
 * Builds the app's few singletons and hands out ViewModels.
 *
 * Deliberately hand-written rather than a DI framework: at this size a dependency graph you
 * can read top to bottom is worth more than code generation.
 */
class AppContainer(context: Context) {

    private val applicationContext = context.applicationContext

    val repository: TranscriptRepository =
        JsonTranscriptRepository(applicationContext.filesDir)

    val settings: SettingsStore =
        SharedPreferencesSettingsStore(applicationContext)

    val engine: TranscriptionEngine =
        AndroidSpeechEngine(applicationContext)

    val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
            modelClass.isAssignableFrom(RecordViewModel::class.java) ->
                RecordViewModel(engine, repository, settings) as T

            modelClass.isAssignableFrom(HistoryViewModel::class.java) ->
                HistoryViewModel(repository) as T

            else -> error("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
