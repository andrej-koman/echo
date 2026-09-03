package dev.andrej.echo.ai

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * @param bytes the published file size, used to show progress before the first byte arrives.
 */
data class ModelSpec(
    val url: String,
    val fileName: String,
    val bytes: Long,
)

sealed interface ModelState {
    data object Absent : ModelState

    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : ModelState {
        val fraction: Float = if (totalBytes > 0) {
            (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    data class Ready(val file: File) : ModelState

    data class Failed(val message: String) : ModelState
}

interface ModelDownloader {
    /**
     * Appends to [target], resuming from its current length. Must call [onProgress] with the
     * running total and the full size.
     */
    suspend fun download(url: String, target: File, onProgress: (Long, Long) -> Unit)
}

/**
 * Owns the one model file on disk. Downloads land in a `.part` sibling and are renamed only once
 * complete, so an interrupted download can resume and a half-file is never mistaken for a model.
 */
class ModelStore(
    private val directory: File,
    val spec: ModelSpec,
    private val downloader: ModelDownloader,
) {

    private val file = File(directory, spec.fileName)
    private val partial = File(directory, "${spec.fileName}.part")
    private val lock = Mutex()

    private val _state = MutableStateFlow(readFromDisk())
    val state: StateFlow<ModelState> = _state.asStateFlow()

    fun fileOrNull(): File? = file.takeIf { it.isFile && it.length() > 0 }

    suspend fun download() {
        if (_state.value is ModelState.Ready) return

        lock.withLock {
            if (_state.value is ModelState.Ready) return

            _state.value = ModelState.Downloading(partial.length(), spec.bytes)

            try {
                directory.mkdirs()
                downloader.download(spec.url, partial) { downloaded, total ->
                    _state.value = ModelState.Downloading(downloaded, total)
                }

                if (!partial.renameTo(file)) {
                    partial.delete()
                    _state.value = ModelState.Failed("could not move the model into place")
                    return
                }

                _state.value = ModelState.Ready(file)
            } catch (e: CancellationException) {
                // The part file stays so the next attempt resumes rather than starting over.
                _state.value = readFromDisk()
                throw e
            } catch (e: Exception) {
                _state.value = ModelState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    suspend fun delete() {
        lock.withLock {
            // LiteRT-LM writes a compiled weight cache next to the model, named after it. Deleting
            // only the model would strand a file the user thought they had reclaimed.
            directory.listFiles()
                ?.filter { it.name.startsWith(spec.fileName) }
                ?.forEach { it.delete() }

            _state.value = ModelState.Absent
        }
    }

    /**
     * A leftover `.part` reads as [ModelState.Absent], not [ModelState.Downloading]: nothing is
     * downloading between app launches, and showing a stalled progress bar would be a lie. The
     * bytes are still there, so the next [download] resumes instead of starting over.
     */
    private fun readFromDisk(): ModelState =
        if (file.isFile && file.length() > 0) ModelState.Ready(file) else ModelState.Absent
}

/**
 * Apache-2.0 and ungated, which matters: the obvious choice, Gemma 3 1B, sits behind a
 * HuggingFace licence gate that returns 401 to an anonymous download.
 */
val QWEN3_1_7B_INT4 = ModelSpec(
    url = "https://huggingface.co/litert-community/Qwen3-1.7B/resolve/main/" +
        "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
    fileName = "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
    bytes = 977_000_000,
)
