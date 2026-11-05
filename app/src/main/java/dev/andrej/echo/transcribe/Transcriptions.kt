package dev.andrej.echo.transcribe

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.media.MediaMetadataRetriever
import android.provider.DocumentsContract
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
import dev.andrej.echo.data.ImportDay
import dev.andrej.echo.data.SettingsStore
import dev.andrej.echo.data.TranscriptRepository
import dev.andrej.echo.speech.AudioDecoder
import dev.andrej.echo.speech.AudioEncoder
import dev.andrej.echo.speech.SAMPLE_RATE
import dev.andrej.echo.speech.pcm16ToFloats
import dev.andrej.echo.data.DictionaryRepository
import dev.andrej.echo.data.Segment
import dev.andrej.echo.speech.speakers.Diarizer
import dev.andrej.echo.speech.speakers.SpeakerTurn
import dev.andrej.echo.speech.speakers.assignSpeakers
import dev.andrej.echo.speech.whisper.SpeechModels
import dev.andrej.echo.speech.whisper.WhisperTranscriber
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ImportFile(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long?,
    val durationMs: Long?,
    val recordedAt: Long?,
)

/** The note being transcribed and its segments so far, ahead of the per-chunk database write. */
data class LiveTranscript(val id: String, val segments: List<Segment>)

sealed interface TranscriptionStatus {
    data object Queued : TranscriptionStatus
    data class DownloadingModel(val fraction: Float) : TranscriptionStatus
    data class Transcribing(val fraction: Float) : TranscriptionStatus
    data class FindingSpeakers(val fraction: Float) : TranscriptionStatus
    data class Analyzing(val fraction: Float = 0f, val model: String = "") : TranscriptionStatus
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
    private val speechModels: SpeechModels,
    private val vadStore: ModelStore,
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
    private val interruptedImports = MutableStateFlow(emptySet<String>())
    private val copying = MutableStateFlow(emptySet<String>())

    private val _live = MutableStateFlow<LiveTranscript?>(null)
    val live: StateFlow<LiveTranscript?> = _live

    val status: Flow<Map<String, TranscriptionStatus>> =
        combine(workManager.getWorkInfosByTagFlow(TAG), copying, interruptedImports) { infos, copyingIds, failedIds ->
            buildMap {
                failedIds.forEach { put(it, TranscriptionStatus.Failed) }
                copyingIds.forEach { put(it, TranscriptionStatus.Queued) }
                // A retried job's failed predecessor can linger; the live one wins.
                infos.sortedBy { it.state != WorkInfo.State.FAILED }.forEach { info ->
                    val id = info.tags.firstOrNull { it.startsWith(ID_TAG_PREFIX) }?.removePrefix(ID_TAG_PREFIX)
                    val status = info.toStatus()
                    if (id != null && status != null) put(id, status)
                }
            }
        }

    private val _offered = MutableStateFlow(emptyList<ImportFile>())

    /** Picked or shared files waiting for the user to say which day they belong to. */
    val offered: StateFlow<List<ImportFile>> = _offered

    /** Every file waits for the sheet, which asks at least for its title. */
    fun offer(uris: List<Uri>) {
        scope.launch { uris.forEach { uri -> describe(uri).let { file -> _offered.update { it + file } } } }
    }

    private val _started = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** A note to open as its import starts, so the user watches it fill in. */
    val started: SharedFlow<String> = _started

    /** A blank [title] leaves the note to be named after its file, then by analysis. */
    fun accept(file: ImportFile, createdAt: Long, title: String?, always: ImportDay?) {
        if (always != null) settings.importDay = always
        _offered.update { it - file }
        // With several files, the sheet asks about the next; only the last one opens.
        val open = _offered.value.isEmpty()
        scope.launch { importOne(file, createdAt, title?.trim()?.ifBlank { null }, open) }
    }

    fun decline(file: ImportFile) = _offered.update { it - file }

    private suspend fun describe(uri: Uri): ImportFile = withContext(Dispatchers.IO) {
        var name: String? = null
        var size: Long? = null
        var modified: Long? = null
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    fun long(column: String) = cursor.getColumnIndex(column).takeIf { it >= 0 && !cursor.isNull(it) }
                        ?.let(cursor::getLong)
                    name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(cursor::getString)
                    size = long(OpenableColumns.SIZE)
                    modified = long(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                }
            }
        }
        val retriever = MediaMetadataRetriever()
        val (durationMs, tagged) = try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() to
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)?.let(::parseMediaDate)
        } catch (e: Exception) {
            null to null
        } finally {
            retriever.release()
        }
        ImportFile(uri, name ?: "Audio", size, durationMs, tagged ?: modified?.takeIf { it > 0 })
    }

    private suspend fun importOne(file: ImportFile, createdAt: Long, title: String?, open: Boolean) {
        val id = UUID.randomUUID().toString()
        val importDir = dirFor(id).apply { mkdirs() }
        File(importDir, PENDING).writeText("import")
        val transcript = repository.save(
            id = id,
            text = "",
            language = "",
            durationMs = file.durationMs ?: 0,
            createdAt = createdAt,
            title = title ?: file.name.substringBeforeLast('.').ifBlank { null },
            titleByUser = title != null,
        )
        copying.update { it + transcript.id }
        if (open) _started.tryEmit(transcript.id)
        try {
            withContext(Dispatchers.IO) {
                val dir = dirFor(transcript.id).apply { mkdirs() }
                context.contentResolver.openInputStream(file.uri)!!.use { input ->
                    val partial = File(dir, "$SOURCE.part")
                    java.io.FileOutputStream(partial).use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                    if (!partial.renameTo(File(dir, SOURCE))) throw IOException("could not finalize import")
                }
            }
            enqueue(transcript.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(LOG_TAG, "import of ${file.uri} failed: ${e.message}")
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
     * Reconciles raw takes and saved notes whose process died before WorkManager accepted them.
     * Incomplete imports remain visibly failed because their URI grant may no longer exist.
     * Call only before any recording can have started.
     */
    fun recoverInterruptedRecordings() {
        scope.launch {
            val notes = repository.transcripts.first().associateBy { it.id }
            val directories = withContext(Dispatchers.IO) { recordingsDir.listFiles().orEmpty().toList() }
            directories.forEach { dir ->
                val note = notes[dir.name]
                val pending = File(dir, PENDING).isFile
                val unfinished = pending || (note?.text?.isBlank() == true && !File(dir, SPEECH_COMPLETE).isFile)
                when (recoveryAction(dir, note != null, unfinished)) {
                    RecoveryAction.ENQUEUE -> {
                        if (note == null) {
                            val pcm = File(dir, PCM)
                            val durationMs = pcm.length() / 2 * 1000 / SAMPLE_RATE
                            repository.save(id = dir.name, text = "", language = "", durationMs = durationMs,
                                createdAt = pcm.lastModified() - durationMs)
                        }
                        enqueue(dir.name, restart = false)
                    }
                    RecoveryAction.FAILED -> interruptedImports.update { it + dir.name }
                    RecoveryAction.NONE -> {
                        val pcm = File(dir, PCM)
                        if (note == null && pcm.isFile && pcm.length() == 0L) dir.deleteRecursively()
                    }
                }
            }
            // Covers older versions' save-before-directory window too.
            notes.values.filter { it.text.isBlank() && !dirFor(it.id).exists() }.forEach { note ->
                interruptedImports.update { it + note.id }
            }
        }
    }

    /** Queues [id] for transcription, or re-queues one that failed. */
    fun transcribe(id: String) {
        val dir = dirFor(id)
        if (File(dir, SOURCE).isFile || File(dir, PCM).isFile) enqueue(id)
    }

    fun discard(id: String) {
        analysis.cancel(id)
        interruptedImports.update { it - id }
        workManager.cancelUniqueWork(workName(id))
        scope.launch(Dispatchers.IO) { dirFor(id).deleteRecursively() }
    }

    fun discardAll() {
        workManager.cancelAllWorkByTag(TAG)
        scope.launch { repository.transcripts.first().forEach { analysis.cancel(it.id) } }
        interruptedImports.value = emptySet()
        scope.launch(Dispatchers.IO) { recordingsDir.deleteRecursively() }
    }

    private fun enqueue(id: String, restart: Boolean = true) {
        val dir = dirFor(id).apply { mkdirs() }
        File(dir, PENDING).writeText("queued")
        if (restart) TranscriptionCheckpoint(File(dir, SPEECH_COMPLETE)).reset()
        interruptedImports.update { it - id }
        stops.update { it - id }
        analysis.allow(id)
        val request = OneTimeWorkRequestBuilder<TranscriptionWorker>()
            .setInputData(workDataOf(TranscriptionWorker.KEY_ID to id))
            .addTag(TAG)
            .addTag(ID_TAG_PREFIX + id)
            .apply {
                if (speechModels.store().state.value !is ModelState.Ready) {
                    val network = if (settings.wifiOnlyDownload) NetworkType.UNMETERED else NetworkType.CONNECTED
                    setConstraints(Constraints(requiredNetworkType = network))
                }
            }
            .build()
        workManager.enqueueUniqueWork(workName(id), if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
    }

    internal suspend fun run(id: String, report: (TranscriptionStatus) -> Unit) = lock.withLock {
        try {
            val completed = runUntilStopped(stops.map { id in it }) { transcribeLocked(id, report) }
            if (!completed) {
                val transcript = repository.transcripts.first().firstOrNull { it.id == id }
                val heard = _live.value?.takeIf { it.id == id }?.segments
                if (transcript != null && heard != null) {
                    repository.setTranscription(id, transcriptText(heard), transcript.language, transcript.durationMs, heard)
                }
                File(dirFor(id), SPEECH_COMPLETE).createNewFile()
                File(dirFor(id), PENDING).delete()
                try {
                    finishAudio(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Keep the raw recording; an encoding failure must not restart a stopped job.
                    Log.w(LOG_TAG, "$id: could not finalize stopped audio: ${e.message}")
                }
            }
        } finally {
            _live.value = null
            stops.update { it - id }
        }
    }

    private val stops = MutableStateFlow(emptySet<String>())

    /** Stops this note's processing, retaining its audio and text heard so far. */
    fun stop(id: String) {
        stops.update { it + id }
        analysis.cancel(id)
    }

    private suspend fun transcribeLocked(id: String, report: (TranscriptionStatus) -> Unit) {
        val began = SystemClock.elapsedRealtime()
        fun elapsed() = SystemClock.elapsedRealtime() - began
        val dir = dirFor(id)
        val source = File(dir, SOURCE)
        val pcm = File(dir, PCM)
        if (!source.isFile && !pcm.isFile) throw IOException("audio is gone")

        val checkpoint = TranscriptionCheckpoint(File(dir, SPEECH_COMPLETE))
        checkpoint.run(transcribe = {
        val speechModel = speechModels.current
        val modelStore = speechModels.store(speechModel)
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
        // Only a speed-up: without it Whisper reads the silence too.
        val vad = vadStore.fileOrNull() ?: try {
            vadStore.download()
            vadStore.fileOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(LOG_TAG, "no VAD model: ${e.message}")
            null
        }
        Log.i(LOG_TAG, "$id: model ready at ${elapsed()} ms")

        val recorded = !source.isFile
        val decodeShare = if (recorded) 0f else DECODE_SHARE
        val durationMs = if (recorded) {
            pcm.length() / 2 * 1000 / SAMPLE_RATE
        } else {
            withContext(Dispatchers.IO) {
                decoder.decode(source, pcm) { report(TranscriptionStatus.Transcribing(it * DECODE_SHARE)) }
            }
        }

        Log.i(LOG_TAG, "$id: ${durationMs / 1000} s of audio decoded at ${elapsed()} ms")
        val resumed = checkpoint.chunk()
        val segments = resumed?.segments?.toMutableList() ?: mutableListOf()
        if (resumed != null) {
            repository.setTranscription(id, transcriptText(segments), resumed.language, durationMs, segments)
            _live.value = LiveTranscript(id, segments.toList())
        }
        var firstSegmentLogged = false
        var language: String? = resumed?.language ?: settings.transcriptionLanguage
        val prompt = whisperPrompt(dictionary.all().map { it.term })
        // The LLM is closed first: both models at once had the low-memory killer take the app.
        modelSlot.whisper { coroutineScope {
            // On the little cores Whisper leaves idle, so it costs Whisper almost nothing.
            val whisperDone = AtomicBoolean(false)
            val speakerPercent = AtomicInteger(0)
            val speakers = if (settings.detectSpeakers) {
                async {
                    findSpeakers(id, pcm) { percent ->
                        speakerPercent.set(percent)
                        if (whisperDone.get()) report(TranscriptionStatus.FindingSpeakers(percent / 100f))
                    }
                }
            } else {
                null
            }
            val whisperRun = async { WhisperTranscriber.load(model).use { whisper ->
                Log.i(LOG_TAG, "$id: ${speechModel.name} loaded at ${elapsed()} ms")
                speechModels.deleteAllBut(speechModel)
                RandomAccessFile(pcm, "r").use { file ->
                    val total = file.length() / 2
                    var start = resumed?.nextSample ?: 0L
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
                            vad,
                            onSegment = { segment ->
                                if (!firstSegmentLogged) {
                                    firstSegmentLogged = true
                                    Log.i(LOG_TAG, "$id: first segment at ${elapsed()} ms")
                                }
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
                        start += length
                        checkpoint.saveChunk(start, language.orEmpty(), segments)
                        repository.setTranscription(id, transcriptText(segments), language.orEmpty(), durationMs, segments)
                        Log.i(LOG_TAG, "$id: chunk to ${start / SAMPLE_RATE} s done at ${elapsed()} ms")
                    }
                }
            } }
            whisperRun.await()
            // Partial text is only needed while Whisper runs. Later stops must not overwrite
            // completed segments or their speaker assignments with the live preview.
            _live.value = null
            if (speakers != null) {
                whisperDone.set(true)
                if (!speakers.isCompleted) {
                    report(TranscriptionStatus.FindingSpeakers(speakerPercent.get() / 100f))
                    Log.i(LOG_TAG, "$id: waiting for speakers at ${elapsed()} ms")
                }
                val turns = speakers.await()
                Log.i(LOG_TAG, "$id: speakers ready at ${elapsed()} ms")
                val (assigned, count) = assignSpeakers(segments, turns)
                if (count > 0 && repository.transcripts.first().any { it.id == id }) {
                    repository.setSpeakers(id, assigned, count)
                }
            }
        } }
        Log.i(LOG_TAG, "$id: transcribed at ${elapsed()} ms")
        }, finalize = {
        finishAudio(id)

        val transcript = repository.transcripts.first().firstOrNull { it.id == id }
        if (transcript != null && id !in stops.value && settings.autoAnalyze && transcript.text.isNotBlank()) {
            analysisQueue.enqueue(id)
            // Stop can arrive while the queue write is suspended.
            if (id in stops.value) analysisQueue.remove(id)
            else coroutineScope {
                report(TranscriptionStatus.Analyzing())
                val progress = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    analysis.progress.collect { running ->
                        running[id]?.let { current ->
                            report(TranscriptionStatus.Analyzing(current.fraction, current.model))
                        }
                    }
                }
                try {
                    analysis.requestDrainAndAwait(id)
                } finally {
                    progress.cancel()
                }
            }
        }
        })
        File(dir, PENDING).delete()
    }

    /** Finalize playback after completion or a user stop, once native readers have returned. */
    private suspend fun finishAudio(id: String) {
        val dir = dirFor(id)
        val pcm = File(dir, PCM)
        if (!pcm.isFile) return
        val source = File(dir, SOURCE)
        if (!source.isFile) {
            // Keep PCM if encoding or the rename fails, so the original audio is never lost.
            val partial = File(dir, "$SOURCE.part")
            withContext(Dispatchers.IO) { encoder.encode(pcm, partial) }
            if (!partial.renameTo(source)) throw IOException("could not finalize recording")
        }
        pcm.delete()
    }

    /** Speakers are a bonus on top of the transcript: failing to find them never fails the job. */
    private suspend fun findSpeakers(id: String, pcm: File, onProgress: (Int) -> Unit): List<SpeakerTurn> =
        try {
            if (!diarizer.ready) diarizer.download {}
            diarizer.diarize(pcm, onProgress)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(LOG_TAG, "no speakers for $id: ${e.message}")
            emptyList()
        }

    private fun RandomAccessFile.readFloats(startSample: Long, count: Int): FloatArray {
        seek(startSample * 2)
        val bytes = ByteArray(count * 2)
        readFully(bytes)
        return bytes.pcm16ToFloats()
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
        private const val PENDING = "transcription-pending"
        private const val SPEECH_COMPLETE = "speech-complete"
        private const val DECODE_SHARE = 0.05f
        private const val KEY_PHASE = "phase"
        private const val KEY_FRACTION = "fraction"

        private fun workName(id: String) = "transcribe-$id"

        internal fun TranscriptionStatus.toData(): Data = when (this) {
            is TranscriptionStatus.DownloadingModel -> workDataOf(KEY_PHASE to "download", KEY_FRACTION to fraction)
            is TranscriptionStatus.Transcribing -> workDataOf(KEY_PHASE to "transcribe", KEY_FRACTION to fraction)
            is TranscriptionStatus.FindingSpeakers -> workDataOf(KEY_PHASE to "speakers", KEY_FRACTION to fraction)
            is TranscriptionStatus.Analyzing -> workDataOf(KEY_PHASE to "analyze", KEY_FRACTION to fraction, "model" to model)
            else -> Data.EMPTY
        }

        private fun Data.toStatus(): TranscriptionStatus? {
            val fraction = getFloat(KEY_FRACTION, 0f)
            return when (getString(KEY_PHASE)) {
                "download" -> TranscriptionStatus.DownloadingModel(fraction)
                "transcribe" -> TranscriptionStatus.Transcribing(fraction)
                "speakers" -> TranscriptionStatus.FindingSpeakers(fraction)
                "analyze" -> TranscriptionStatus.Analyzing(fraction, getString("model").orEmpty())
                else -> null
            }
        }
    }
}

/**
 * Android's METADATA_KEY_DATE, e.g. `20231028T103100.000Z`. An MP4 with no date says 1904, its
 * epoch, so anything before 1971 counts as none.
 */
internal fun parseMediaDate(value: String, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long? {
    val match = Regex("""^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})""").find(value.trim()) ?: return null
    val (y, mo, d, h, mi, s) = match.destructured
    if (y.toInt() < 1971) return null
    val time = runCatching { java.time.LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), s.toInt()) }
        .getOrNull() ?: return null
    return time.atZone(if (value.trim().endsWith("Z")) java.time.ZoneOffset.UTC else zone).toInstant().toEpochMilli()
}
