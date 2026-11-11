package dev.andrej.echo.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class HapticsTest {
    @Test fun `scrub ticks per ten-second mark and marks each bound once`() {
        val duration = 60_000L
        assertEquals(ScrubFeedback.None, scrubFeedback(11_000, 19_000, duration))
        assertEquals(ScrubFeedback.Tick, scrubFeedback(19_000, 21_000, duration))
        assertEquals(ScrubFeedback.Tick, scrubFeedback(21_000, 19_000, duration))
        assertEquals(ScrubFeedback.Bound, scrubFeedback(3_000, 0, duration))
        assertEquals(ScrubFeedback.None, scrubFeedback(0, 0, duration))
        assertEquals(ScrubFeedback.Bound, scrubFeedback(55_000, 60_000, duration))
        assertEquals(ScrubFeedback.None, scrubFeedback(60_000, 60_000, duration))
        assertEquals(ScrubFeedback.Bound, scrubFeedback(0, 60_000, duration))
    }

    @Test fun `celebration falls back from primitives to waveform to a predefined click`() {
        assertEquals(CelebrationPlan.Primitives, celebrationPlan(sdk = 34, primitivesSupported = true, amplitudeControl = true))
        assertEquals(CelebrationPlan.Waveform, celebrationPlan(sdk = 34, primitivesSupported = false, amplitudeControl = true))
        assertEquals(CelebrationPlan.Waveform, celebrationPlan(sdk = 29, primitivesSupported = true, amplitudeControl = true))
        assertEquals(CelebrationPlan.Predefined, celebrationPlan(sdk = 34, primitivesSupported = false, amplitudeControl = false))
    }
}
