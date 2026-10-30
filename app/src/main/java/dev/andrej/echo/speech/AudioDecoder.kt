package dev.andrej.echo.speech

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

const val SAMPLE_RATE = 16_000

/** Any audio or video file MediaExtractor can open, to 16 kHz mono PCM16 little-endian. */
class AudioDecoder {

    /** Returns the decoded length in milliseconds. */
    suspend fun decode(source: File, target: File, onProgress: (Float) -> Unit): Long {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(source.path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("no audio track")
            extractor.selectTrack(track)

            val format = extractor.getTrackFormat(track)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()

            var written = 0L
            target.outputStream().buffered().use { out ->
                var outputFormat = codec.outputFormat
                var resampler: Resampler? = null
                val info = MediaCodec.BufferInfo()
                var inputDone = false

                while (true) {
                    currentCoroutineContext().ensureActive()

                    if (!inputDone) {
                        val index = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (index >= 0) {
                            val size = extractor.readSampleData(codec.getInputBuffer(index)!!, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        outputFormat = codec.outputFormat
                        resampler = null
                    } else if (index >= 0) {
                        val buffer = codec.getOutputBuffer(index)!!
                        buffer.position(info.offset).limit(info.offset + info.size)

                        val rate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val mono = downmix(buffer.toFloats(outputFormat), outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                        val samples = (resampler ?: Resampler(rate).also { resampler = it }).process(mono)
                        out.write(samples.toPcm16())
                        written += samples.size
                        codec.releaseOutputBuffer(index, false)

                        if (durationUs > 0) onProgress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            return written * 1000 / SAMPLE_RATE
        } finally {
            codec?.release()
            extractor.release()
        }
    }

    private fun ByteBuffer.toFloats(format: MediaFormat): FloatArray {
        val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            format.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
            AudioFormat.ENCODING_PCM_16BIT
        }
        val ordered = order(ByteOrder.nativeOrder())
        return if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            FloatArray(remaining() / 4).also { ordered.asFloatBuffer().get(it) }
        } else {
            val shorts = ShortArray(remaining() / 2).also { ordered.asShortBuffer().get(it) }
            FloatArray(shorts.size) { shorts[it] / 32768f }
        }
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
    }
}

internal fun downmix(interleaved: FloatArray, channels: Int): FloatArray {
    if (channels <= 1) return interleaved
    return FloatArray(interleaved.size / channels) { frame ->
        var sum = 0f
        for (c in 0 until channels) sum += interleaved[frame * channels + c]
        sum / channels
    }
}

/** Streaming linear-interpolation resampler; carries its phase and last sample across buffers. */
internal class Resampler(private val inRate: Int, private val outRate: Int = SAMPLE_RATE) {
    private val step = inRate.toDouble() / outRate
    private var position = 0.0
    private var last = 0f

    fun process(input: FloatArray): FloatArray {
        if (inRate == outRate) return input

        val out = FloatArray(ceil((input.size + 1) / step).toInt() + 1)
        var count = 0
        while (true) {
            val i = floor(position).toInt()
            if (i + 1 >= input.size) break
            val a = if (i < 0) last else input[i]
            val b = input[i + 1]
            out[count++] = (a + (b - a) * (position - i)).toFloat()
            position += step
        }
        position -= input.size
        if (input.isNotEmpty()) last = input.last()
        return out.copyOf(count)
    }
}

internal fun FloatArray.toPcm16(): ByteArray {
    val bytes = ByteBuffer.allocate(size * 2).order(ByteOrder.LITTLE_ENDIAN)
    for (sample in this) bytes.putShort((sample.coerceIn(-1f, 1f) * 32767).toInt().toShort())
    return bytes.array()
}

internal fun ByteArray.pcm16ToFloats(): FloatArray {
    val shorts = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
    return FloatArray(size / 2) { shorts.get(it) / 32768f }
}
