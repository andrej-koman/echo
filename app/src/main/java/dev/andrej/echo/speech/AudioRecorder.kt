package dev.andrej.echo.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.log10
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

fun interface Recorder {
    /**
     * Appends 16 kHz mono PCM16 to [target] until cancelled. [onBuffer] gets the loudness of each
     * buffer (0f..1f) and the samples in the file so far.
     */
    suspend fun record(target: File, onBuffer: (level: Float, totalSamples: Long) -> Unit)
}

/**
 * Raw PCM, not WAV: it is exactly what Whisper reads, and a file with no header to finish is
 * still whole if the process dies mid-take.
 */
class AudioRecorder : Recorder {

    @SuppressLint("MissingPermission")
    override suspend fun record(target: File, onBuffer: (Float, Long) -> Unit) = withContext(Dispatchers.IO) {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val record = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, ENCODING, maxOf(minBuffer, BUFFER_SAMPLES * 4))
        try {
            if (record.state != AudioRecord.STATE_INITIALIZED) throw IOException("microphone unavailable")
            record.startRecording()
            target.parentFile?.mkdirs()
            var total = target.length() / 2
            FileOutputStream(target, true).use { out ->
                val samples = ShortArray(BUFFER_SAMPLES)
                val bytes = ByteArray(BUFFER_SAMPLES * 2)
                while (isActive) {
                    val read = record.read(samples, 0, samples.size)
                    if (read < 0) throw IOException("AudioRecord.read failed ($read)")
                    for (i in 0 until read) {
                        bytes[2 * i] = samples[i].toInt().toByte()
                        bytes[2 * i + 1] = (samples[i].toInt() shr 8).toByte()
                    }
                    out.write(bytes, 0, read * 2)
                    total += read
                    onBuffer(loudness(samples, read), total)
                }
            }
        } finally {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
            record.release()
        }
    }

    private companion object {
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val BUFFER_SAMPLES = SAMPLE_RATE / 10
    }
}

/** RMS in dBFS, mapped so room tone sits near 0f and close speech near 1f. */
internal fun loudness(samples: ShortArray, count: Int): Float {
    if (count == 0) return 0f
    var sum = 0.0
    for (i in 0 until count) {
        val s = samples[i] / 32768.0
        sum += s * s
    }
    val rms = sqrt(sum / count)
    if (rms <= 0.0) return 0f
    val db = 20 * log10(rms)
    return ((db - QUIET_DBFS) / (LOUD_DBFS - QUIET_DBFS)).toFloat().coerceIn(0f, 1f)
}

private const val QUIET_DBFS = -55.0
private const val LOUD_DBFS = -15.0
