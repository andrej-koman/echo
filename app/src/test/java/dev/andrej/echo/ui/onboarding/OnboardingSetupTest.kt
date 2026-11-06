package dev.andrej.echo.ui.onboarding

import dev.andrej.echo.ai.ModelState
import dev.andrej.echo.ai.NotesAiModel
import dev.andrej.echo.speech.whisper.WHISPER_BASE
import dev.andrej.echo.speech.whisper.WHISPER_LARGE_V3_TURBO
import dev.andrej.echo.speech.whisper.WHISPER_MEDIUM
import dev.andrej.echo.speech.whisper.WHISPER_SMALL
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingSetupTest {

    private val big = listOf(WHISPER_LARGE_V3_TURBO, WHISPER_MEDIUM, WHISPER_SMALL, WHISPER_BASE)
    private val small = listOf(WHISPER_SMALL, WHISPER_BASE)
    private val ready = ModelState.Ready(File("x"))

    @Test
    fun `faster picks small`() {
        val r = recommendSetup(setOf(RecordUse.QuickThoughts), Priority.Faster, big, WHISPER_LARGE_V3_TURBO)
        assertEquals(WHISPER_SMALL, r.speech)
    }

    @Test
    fun `accurate picks large on big phones and falls back to the default on small ones`() {
        assertEquals(
            WHISPER_LARGE_V3_TURBO,
            recommendSetup(emptySet(), Priority.Accurate, big, WHISPER_LARGE_V3_TURBO).speech,
        )
        assertEquals(WHISPER_SMALL, recommendSetup(emptySet(), Priority.Accurate, small, WHISPER_SMALL).speech)
        assertFalse(accurateAvailable(small))
        assertTrue(accurateAvailable(big))
    }

    @Test
    fun `help me choose follows the phone default`() {
        assertEquals(WHISPER_LARGE_V3_TURBO, recommendSetup(emptySet(), Priority.HelpMeChoose, big, WHISPER_LARGE_V3_TURBO).speech)
        assertEquals(WHISPER_SMALL, recommendSetup(emptySet(), Priority.HelpMeChoose, small, WHISPER_SMALL).speech)
    }

    @Test
    fun `only meetings turn speaker detection on`() {
        fun detect(vararg uses: RecordUse) =
            recommendSetup(uses.toSet(), Priority.HelpMeChoose, big, WHISPER_SMALL).detectSpeakers
        assertTrue(detect(RecordUse.Meetings, RecordUse.Lectures))
        assertFalse(detect(RecordUse.Lectures, RecordUse.QuickThoughts))
    }

    private fun summary(speech: ModelState, notes: ModelState, speakers: Boolean = true) = buildSetupSummary(
        speech = WHISPER_SMALL,
        vadBytes = 1_000,
        speakerBytes = 40_000,
        notesAi = NotesAiModel.Fast,
        detectSpeakers = speakers,
        states = SetupModelStates(speech, speech, listOf(ModelState.Absent), notes),
        freeBytes = 5_000_000_000L,
    )

    @Test
    fun `total excludes models already ready`() {
        val s = summary(speech = ready, notes = ModelState.Absent)
        assertEquals(40_000 + NotesAiModel.Fast.spec.bytes, s.totalDownloadBytes)
    }

    @Test
    fun `speaker row is absent when detection is off`() {
        val s = summary(speech = ModelState.Absent, notes = ready, speakers = false)
        assertEquals(2, s.rows.size)
        assertEquals(WHISPER_SMALL.spec.bytes + 1_000, s.totalDownloadBytes)
    }

    @Test
    fun `status is done when everything is ready`() {
        assertEquals(SetupStatus.Done, computeSetupStatus(listOf(ready), SetupWork.None))
    }

    @Test
    fun `queued work with nothing downloading waits for wifi`() {
        assertEquals(SetupStatus.WaitingForWifi, computeSetupStatus(listOf(ModelState.Absent), SetupWork.Queued))
    }

    @Test
    fun `failed work or a failed model offers retry`() {
        assertEquals(SetupStatus.Failed, computeSetupStatus(listOf(ModelState.Absent), SetupWork.Failed))
        assertEquals(SetupStatus.Failed, computeSetupStatus(listOf(ModelState.Failed("x")), SetupWork.None))
    }

    @Test
    fun `running work reports combined progress`() {
        val status = computeSetupStatus(
            listOf(ModelState.Downloading(50, 100), ready),
            SetupWork.Running,
        )
        assertEquals(SetupStatus.Downloading(0.5f), status)
    }

    @Test
    fun `nothing requested is idle`() {
        assertEquals(SetupStatus.Idle, computeSetupStatus(listOf(ModelState.Absent), SetupWork.None))
    }
}
