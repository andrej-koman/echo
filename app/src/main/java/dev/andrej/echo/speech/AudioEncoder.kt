package dev.andrej.echo.speech

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.InputStream

/** 16 kHz mono PCM16 → AAC in an `.m4a` container, the form a recording is kept in. */
class AudioEncoder {

    fun encode(pcm: File, target: File) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = MediaMuxer(target.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            pcm.inputStream().buffered().use { input ->
                val info = MediaCodec.BufferInfo()
                var chunk = ByteArray(0)
                var samples = 0L
                var inputDone = false
                while (true) {
                    if (!inputDone) {
                        val index = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (index >= 0) {
                            val buffer = codec.getInputBuffer(index)!!
                            buffer.clear()
                            if (chunk.size < buffer.remaining()) chunk = ByteArray(buffer.remaining() and 1.inv())
                            val read = input.readUpTo(chunk, minOf(chunk.size, buffer.remaining() and 1.inv()))
                            val presentationUs = samples * 1_000_000 / SAMPLE_RATE
                            if (read == 0) {
                                codec.queueInputBuffer(index, 0, 0, presentationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                buffer.put(chunk, 0, read)
                                codec.queueInputBuffer(index, 0, read, presentationUs, 0)
                                samples += read / 2
                            }
                        }
                    }

                    val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                    } else if (index >= 0) {
                        val buffer = codec.getOutputBuffer(index)!!
                        val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (!config && info.size > 0 && track >= 0) {
                            buffer.position(info.offset).limit(info.offset + info.size)
                            muxer.writeSampleData(track, buffer, info)
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            codec.stop()
            if (track >= 0) muxer.stop()
        } finally {
            codec.release()
            muxer.release()
        }
    }

    /** Fills [buffer] up to [length] bytes, short only at end of stream, so PCM frames stay whole. */
    private fun InputStream.readUpTo(buffer: ByteArray, length: Int): Int {
        var total = 0
        while (total < length) {
            val read = read(buffer, total, length - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    private companion object {
        const val BIT_RATE = 32_000
        const val TIMEOUT_US = 10_000L
    }
}
