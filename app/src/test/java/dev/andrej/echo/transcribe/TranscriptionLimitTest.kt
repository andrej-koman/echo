package dev.andrej.echo.transcribe

import dev.andrej.echo.billing.FakeBilling
import dev.andrej.echo.data.FakeSettingsStore
import dev.andrej.echo.data.Segment
import dev.andrej.echo.data.Transcript
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TranscriptionLimitTest {
    @Test fun `free bound includes exact short and long audio while Pro keeps all samples`() {
        for (samples in listOf(0L, 16_000L, FREE_SAMPLES.toLong(), FREE_SAMPLES + 1L, FREE_SAMPLES * 5L)) {
            assertEquals(minOf(samples, FREE_SAMPLES.toLong()), transcriptionSamples(samples, false))
            assertEquals(samples, transcriptionSamples(samples, true))
        }
    }

    @Test fun `unlock observes cold start and newly capped notes without chunk update duplicates`() = runTest {
        val billing = FakeBilling(FakeSettingsStore()).apply { setPro(true) }
        val notes = MutableStateFlow(listOf(note("full"), note("capped", 120_000)))
        val queued = mutableListOf<String>()
        val job = backgroundScope.launch { observeCappedUnlocks(billing.isPro, notes, queued::add) }
        runCurrent()
        assertEquals(listOf("capped"), queued)
        notes.value = notes.value.map { it.copy(text = "updated chunk") } + note("new", 120_000)
        runCurrent()
        assertEquals(listOf("capped", "new"), queued)
        billing.setPro(false)
        notes.value = notes.value + note("later", 120_000)
        runCurrent()
        assertEquals(2, queued.size)
        billing.setPro(true)
        runCurrent()
        assertEquals(listOf("capped", "new", "capped", "new", "later"), queued)
        job.cancel()
    }

    @Test fun `unlock seeds database segments but prefers a later durable chunk`() {
        val stored = listOf(Segment(0, 1000, "first"))
        val note = note("capped", 120_000)
        val seed = resumeChunk(null, note, stored)!!
        assertEquals(FREE_SAMPLES.toLong(), seed.nextSample)
        assertEquals(stored, seed.segments)
        val later = TranscriptionCheckpoint.Chunk(FREE_SAMPLES + 50L, "sl", stored + Segment(120_000, 121_000, "next"))
        assertEquals(later, resumeChunk(later, note, stored))
        assertEquals(seed, resumeChunk(later.copy(nextSample = 10), note, stored))
        assertNull(resumeChunk(null, note.copy(transcribedUntilMs = null), stored))
    }

    @Test fun `stopped first unlock chunk resumes without keeping partial suffix text`() {
        val prefix = Segment(0, 1000, "edited prefix", 1)
        val partial = Segment(120_000, 121_000, "partial suffix")
        val checkpoint = TranscriptionCheckpoint.Chunk(FREE_SAMPLES.toLong(), "en", listOf(prefix))

        for (chunk in listOf(null, checkpoint, checkpoint.copy(nextSample = FREE_SAMPLES - 1L))) {
            val resumed = resumeChunk(chunk, note("capped", 120_000), listOf(prefix, partial))!!

            assertEquals(FREE_SAMPLES.toLong(), resumed.nextSample)
            assertEquals(listOf(prefix), resumed.segments)
        }
    }

    @Test fun `unlock retry preserves suffix after splitting a saved prefix segment`() {
        val original = Segment(0, 1000, "original prefix", 0)
        val suffix = Segment(120_000, 121_000, "completed suffix")
        val checkpoint = TranscriptionCheckpoint.Chunk(FREE_SAMPLES + 16_000L, "en", listOf(original, suffix))
        val edited = listOf(Segment(0, 500, "original", 0), Segment(500, 1000, "prefix", 1))

        val resumed = resumeChunk(checkpoint, note("capped", 120_000), edited + suffix)!!

        assertEquals(checkpoint.nextSample, resumed.nextSample)
        assertEquals(edited + suffix, resumed.segments)
    }

    @Test fun `unlock retry does not restore deleted prefix segments`() {
        val deleted = Segment(0, 1000, "deleted prefix")
        val suffix = Segment(120_000, 121_000, "completed suffix")
        val checkpoint = TranscriptionCheckpoint.Chunk(FREE_SAMPLES + 16_000L, "en", listOf(deleted, suffix))

        val resumed = resumeChunk(checkpoint, note("capped", 120_000), listOf(suffix))!!

        assertEquals(checkpoint.nextSample, resumed.nextSample)
        assertEquals(listOf(suffix), resumed.segments)
    }

    private fun note(id: String, cap: Long? = null) = Transcript(id, "", "en", 0, 300_000, transcribedUntilMs = cap)
}
