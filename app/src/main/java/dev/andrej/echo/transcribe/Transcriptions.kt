package dev.andrej.echo.transcribe

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.andrej.echo.ai.ModelState
import dev.andrej.echo.ai.ModelSlot
import dev.andrej.echo.ai.ModelStore
import dev.andrej.echo.ai.PendingAnalysisWorker
import dev.andrej.echo.data.AnalysisQueueRepository
import dev.andrej.echo.data.SettingsStore
import dev.andrej.echo.data.TranscriptRepository
import dev.andrej.echo.speech.AudioDecoder
import dev.andrej.echo.speech.AudioEncoder
import dev.andrej.echo.speech.SAMPLE_RATE
import dev.andrej.echo.speech.pcm16ToFloats
import dev.andrej.echo.data.DictionaryRepository
import dev.andrej.echo.data.Segment
import dev.andrej.echo.speech.speakers.Diarizer
import dev.andrej.echo.speech.speakers.assignSpeakers
import dev.andrej.echo.speech.whisper.WhisperTranscriber
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The note being transcribed and its segments so far, ahead of the per-chunk database write. */
data class LiveTranscript(val id: String, val segments: List<Segment>)

sealed interface TranscriptionStatus {
    data object Queued : TranscriptionStatus
    data class DownloadingModel(val fraction: Float) : TranscriptionStatus
    data class Transcribing(val fraction: Float) : TranscriptionStatus
    data class FindingSpeakers(val fraction: Float) : TranscriptionStatus
    data object Failed : TranscriptionStatus
}

/**
 * Each transcript's audio lives in `recordings/<id>/`, transcribed by [TranscriptionWorker], which
 * calls back into [run]. An import is copied to `source` and kept: a content URI's read grant
 * does not outlive the app that shared it, and export and playback will need the original. A
 * recording arrives as raw `audio.pcm` and is encoded to `source` once transcribed.
 */
class Transcriptions(
    private val context: Context,
    private val repository: TranscriptRepository,
    private val modelStore: ModelStore,
    private val settings: SettingsStore,
    private val dictionary: DictionaryRepository,
    private val analysisQueue: AnalysisQueueRepository,
    private val analysis: PendingAnalysisWorker,
    private val modelSlot: ModelSlot,
    private val diarizer: Diarizer,
    private val scope: CoroutineScope,
) {
    private val workManager = WorkManager.getInstance(context)
    private val recordingsDir = File(context.filesDir, "recordings")
    private val decoder = AudioDecoder()
    private val encoder = AudioEncoder()

    // One job at a time; a second waits for the first.
    private val lock = Mutex()

    /** Copying a large file takes a while, and the row must not read as failed meanwhile. */
    private val copying = MutableStateFlow(emptySet<String>())

    private val _live = MutableStateFlow<LiveTranscript?>(null)
    val live: StateFlow<LiveTranscript?> = _live

    val status: Flow<Map<String, TranscriptionStatus>> =
        combine(workManager.getWorkInfosByTagFlow(TAG), copying) { infos, copyingIds ->
            buildMap {
                copyingIds.forEach { put(it, TranscriptionStatus.Queued) }
                // A retried job's failed predecessor can linger; the live one wins.
                infos.sortedBy { it.state != WorkInfo.State.FAILED }.forEach { info ->
                    val id = info.tags.firstOrNull { it.startsWith(ID_TAG_PREFIX) }?.removePrefix(ID_TAG_PREFIX)
                    val status = info.toStatus()
                    if (id != null && status != null) put(id, status)
                }
            }
        }

    fun import(uris: List<Uri>) {
        scope.launch { uris.forEach { importOne(it) } }
    }

    private suspend fun importOne(uri: Uri) {
        val name = displayName(uri)
        val transcript = repository.save(
            text = "",
            language = "",
            durationMs = 0,
            title = name?.substringBeforeLast('.')?.ifBlank { null },
        )
        copying.update { it + transcript.id }
        try {
            withContext(Dispatchers.IO) {
                val dir = dirFor(transcript.id).apply { mkdirs() }
                context.contentResolver.openInputStream(uri)!!.use { input ->
                    File(dir, SOURCE).outputStream().use { input.copyTo(it) }
                }
            }
            enqueue(transcript.id)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "import of $uri failed: ${e.message}")
            repository.delete(transcript.id)
            dirFor(transcript.id).deleteRecursively()
        } finally {
            copying.update { it - transcript.id }
        }
    }

    /** Where a recording writes its samples. */
    fun recordingFile(id: String): File = File(dirFor(id), PCM)

    /** A recording has one only after transcription, when its PCM is encoded. */
    fun audioFile(id: String): File? = File(dirFor(id), SOURCE).takeIf { it.isFile }

    /**
     * A take whose process died before Stop leaves `audio.pcm` with no transcript. Raw PCM needs
     * no finishing, so it is filed as it stands. Call only before any recording can have started.
     */
    fun recoverInterruptedRecordings() {
        scope.launch {
            val known = repository.transcripts.first().map { it.id }.toSet()
            val orphans = withContext(Dispatchers.IO) {
                recordingsDir.listFiles().orEmpty().filter { it.name !in known && File(it, PCM).isFile }
            }
            orphans.forEach { dir ->
                val pcm = File(dir, PCM)
                if (pcm.length() == 0L) {
                    dir.deleteRecursively()
                    return@forEach
                }
                val durationMs = pcm.length() / 2 * 1000 / SAMPLE_RATE
                repository.save(
                    id = dir.name,
                    text = "",
                    language = "",
                    durationMs = durationMs,
                    createdAt = pcm.lastModified() - durationMs,
                )
                enqueue(dir.name)
            }
        }
    }

    /** Queues [id] for transcription, or re-queues one that failed. */
    fun transcribe(id: String) {
        val dir = dirFor(id)
        if (File(dir, SOURCE).isFile || File(dir, PCM).isFile) enqueue(id)
    }

    fun discard(id: String) {
        workManager.cancelUniqueWork(workName(id))
        scope.launch(Dispatchers.IO) { dirFor(id).deleteRecursively() }
    }

    fun discardAll() {
        workManager.cancelAllWorkByTag(TAG)
        scope.launch(Dispatchers.IO) { recordingsDir.deleteRecursively() }
    }

    private fun enqueue(id: String) {
        val request = OneTimeWorkRequestBuilder<TranscriptionWorker>()
            .setInputData(workDataOf(TranscriptionWorker.KEY_ID to id))
            .addTag(TAG)
            .addTag(ID_TAG_PREFIX + id)
            .apply {
                if (modelStore.state.value !is ModelState.Ready) {
                    val network = if (settings.wifiOnlyDownload) NetworkType.UNMETERED else NetworkType.CONNECTED
                    setConstraints(Constraints(requiredNetworkType = network))
                }
            }
            .build()
        workManager.enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
    }

    internal suspend fun run(id: String, report: (TranscriptionStatus) -> Unit) = lock.withLock {
        try {
            transcribeLocked(id, report)
        } finally {
            _live.value = null
        }
    }

    private suspend fun transcribeLocked(id: String, report: (TranscriptionStatus) -> Unit) {
        val dir = dirFor(id)
        val source = File(dir, SOURCE)
        val pcm = File(dir, PCM)
        if (!source.isFile && !pcm.isFile) throw IOException("audio is gone")

        if (modelStore.state.value !is ModelState.Ready) {
            val progress = scope.launch {
                modelStore.state.collect { state ->
                    if (state is ModelState.Downloading) report(TranscriptionStatus.DownloadingModel(state.fraction))
                }
            }
            try {
                modelStore.download()
            } finally {
                progress.cancel()
            }
        }
        val model = modelStore.fileOrNull() ?: throw IOException("speech model download failed")

        val recorded = !source.isFile
        val decodeShare = if (recorded) 0f else DECODE_SHARE
        val durationMs = if (recorded) {
            pcm.length() / 2 * 1000 / SAMPLE_RATE
        } else {
            withContext(Dispatchers.IO) {
                decoder.decode(source, pcm) { report(TranscriptionStatus.Transcribing(it * DECODE_SHARE)) }
            }
        }

        val segments = mutableListOf<Segment>()
        var language: String? = settings.transcriptionLanguage
        val prompt = whisperPrompt(dictionary.all().map { it.term })
        // The LLM is closed first: both models at once had the low-memory killer take the app.
        modelSlot.whisper {
            WhisperTranscriber.load(model).use { whisper ->
                RandomAccessFile(pcm, "r").use { file ->
                    val total = file.length() / 2
                    var start = 0L
                    while (start < total) {
                        val samples = file.readFloats(start, minOf(CHUNK_SAMPLES.toLong(), total - start).toInt())
                        val length = if (start + samples.size < total) quietestCut(samples) else samples.size
                        val chunkStart = start
                        val offsetMs = start * 1000 / SAMPLE_RATE
                        val heard = mutableListOf<Segment>()
                        val result = whisper.transcribe(
                            samples.copyOf(length),
                            language,
                            prompt,
                            onSegment = { segment ->
                                heard += listOf(segment).spoken()
                                    .map { it.copy(startMs = it.startMs + offsetMs, endMs = it.endMs + offsetMs) }
                                _live.value = LiveTranscript(id, segments + heard)
                            },
                        ) { percent ->
                            val done = chunkStart + length * percent / 100f
                            report(TranscriptionStatus.Transcribing(decodeShare + (1 - decodeShare) * done / total))
                        }
                        // Detected once on the first chunk, then held so a quiet stretch can't flip it.
                        language = language ?: result.language
                        segments += result.segments.spoken()
                            .map { it.copy(startMs = it.startMs + offsetMs, endMs = it.endMs + offsetMs) }
                        repository.setTranscription(id, transcriptText(segments), language.orEmpty(), durationMs, segments)
                        start += length
                    }
                }
            }
            if (settings.detectSpeakers) findSpeakers(id, pcm, segments, language.orEmpty(), durationMs, report)
        }
        if (recorded) {
            // Encoded beside, then renamed: a half-written `source` would be taken for an import.
            val partial = File(dir, "$SOURCE.part")
            withContext(Dispatchers.IO) { encoder.encode(pcm, partial) }
            partial.renameTo(source)
        }
        pcm.delete()

        val exists = repository.transcripts.first().any { it.id == id }
        if (exists && settings.autoAnalyze && transcriptText(segments).isNotBlank()) {
            analysisQueue.enqueue(id)
            analysis.requestDrainAndAwait()
        }
    }

    /** Speakers are a bonus on top of the transcript: failing to find them never fails the job. */
    private suspend fun findSpeakers(
        id: String,
        pcm: File,
        segments: List<Segment>,
        language: String,
        durationMs: Long,
        report: (TranscriptionStatus) -> Unit,
    ) {
        if (segments.isEmpty()) return
        try {
            report(TranscriptionStatus.FindingSpeakers(0f))
            if (!diarizer.ready) diarizer.download { report(TranscriptionStatus.FindingSpeakers(0f)) }
            val turns = diarizer.diarize(pcm) { report(TranscriptionStatus.FindingSpeakers(it / 100f)) }
            val (assigned, count) = assignSpeakers(segments, turns)
            if (count > 0 && repository.transcripts.first().any { it.id == id }) {
                repository.setSpeakers(id, assigned, count)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(LOG_TAG, "no speakers for $id: ${e.message}")
        }
    }

    private fun RandomAccessFile.readFloats(startSample: Long, count: Int): FloatArray {
        seek(startSample * 2)
        val bytes = ByteArray(count * 2)
        readFully(bytes)
        return bytes.pcm16ToFloats()
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun dirFor(id: String) = File(recordingsDir, id)

    private fun WorkInfo.toStatus(): TranscriptionStatus? = when (state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> TranscriptionStatus.Queued
        WorkInfo.State.RUNNING -> progress.toStatus() ?: TranscriptionStatus.Queued
        WorkInfo.State.FAILED -> TranscriptionStatus.Failed
        WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED -> null
    }

    companion object {
        private const val LOG_TAG = "Transcriptions"
        private const val TAG = "transcription"
        private const val ID_TAG_PREFIX = "transcript:"
        private const val SOURCE = "source"
        private const val PCM = "audio.pcm"
        private const val DECODE_SHARE = 0.05f
        private const val KEY_PHASE = "phase"
        private const val KEY_FRACTION = "fraction"

        private fun workName(id: String) = "transcribe-$id"

        internal fun TranscriptionStatus.toData(): Data = when (this) {
            is TranscriptionStatus.DownloadingModel -> workDataOf(KEY_PHASE to "download", KEY_FRACTION to fraction)
            is TranscriptionStatus.Transcribing -> workDataOf(KEY_PHASE to "transcribe", KEY_FRACTION to fraction)
            is TranscriptionStatus.FindingSpeakers -> workDataOf(KEY_PHASE to "speakers", KEY_FRACTION to fraction)
            else -> Data.EMPTY
        }

        private fun Data.toStatus(): TranscriptionStatus? {
            val fraction = getFloat(KEY_FRACTION, 0f)
            return when (getString(KEY_PHASE)) {
                "download" -> TranscriptionStatus.DownloadingModel(fraction)
                "transcribe" -> TranscriptionStatus.Transcribing(fraction)
                "speakers" -> TranscriptionStatus.FindingSpeakers(fraction)
                else -> null
            }
        }
    }
}
