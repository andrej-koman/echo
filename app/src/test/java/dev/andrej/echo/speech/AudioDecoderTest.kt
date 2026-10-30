package dev.andrej.echo.speech

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDecoderTest {

    @Test
    fun `downmix averages each frame`() {
        assertArrayEquals(floatArrayOf(0.5f, -0.25f), downmix(floatArrayOf(1f, 0f, -0.5f, 0f), 2), 1e-6f)
    }

    @Test
    fun `resampling 48k to 16k keeps every third sample of a ramp`() {
        val input = FloatArray(48) { it.toFloat() }
        val out = Resampler(48_000).process(input)
        assertEquals(16, out.size)
        assertArrayEquals(FloatArray(16) { it * 3f }, out, 1e-4f)
    }

    @Test
    fun `resampling across buffer boundaries matches one pass`() {
        val input = FloatArray(44_100) { kotlin.math.sin(it / 20.0).toFloat() }
        val whole = Resampler(44_100).process(input)

        val streamed = Resampler(44_100)
        val pieces = listOf(0..999, 1000..1336, 1337..44_099).map { streamed.process(input.sliceArray(it)) }
        val joined = pieces.reduce { a, b -> a + b }

        assertTrue(kotlin.math.abs(whole.size - joined.size) <= 1)
        val n = minOf(whole.size, joined.size)
        assertArrayEquals(whole.copyOf(n), joined.copyOf(n), 1e-4f)
    }

    @Test
    fun `16k input passes through untouched`() {
        val input = floatArrayOf(0.1f, 0.2f)
        assertArrayEquals(input, Resampler(16_000).process(input), 0f)
    }

    @Test
    fun `pcm16 round trips`() {
        val samples = floatArrayOf(0f, 0.5f, -0.5f, 1f, -1f)
        assertArrayEquals(samples, samples.toPcm16().pcm16ToFloats(), 1e-4f)
    }
}
