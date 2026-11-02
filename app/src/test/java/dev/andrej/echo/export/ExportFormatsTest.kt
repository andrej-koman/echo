package dev.andrej.echo.export

import dev.andrej.echo.data.Segment
import dev.andrej.echo.data.Speaker
import dev.andrej.echo.data.TodoItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportFormatsTest {

    private val note = ExportNote(
        title = "Weekly sync",
        date = "2 Nov 2026, 09:42",
        text = "Okay, let's start. Onboarding is done. Good.",
        segments = listOf(
            Segment(0, 14_000, " Okay, let's start.", speaker = 0),
            Segment(15_000, 31_000, " Onboarding is done.", speaker = 1),
            Segment(32_000, 48_500, " Good.", speaker = 1),
        ),
        speakers = listOf(Speaker(0, "Ana"), Speaker(1, null)),
        summary = "A quick sync.",
        tasks = listOf(
            TodoItem(id = "1", sourceTranscriptId = "t", text = "Copy pass", dueAt = 0, hasTime = false, done = true, createdAt = 0),
        ),
    )

    @Test
    fun `text joins one speaker's lines into a paragraph`() {
        assertEquals(
            "Ana: Okay, let's start.\n\nSpeaker 2: Onboarding is done. Good.\n",
            exportText(note, ExportOptions(speakerNames = true)),
        )
    }

    @Test
    fun `text without names or times is just the paragraphs`() {
        assertEquals(
            "Okay, let's start.\n\nOnboarding is done. Good.\n",
            exportText(note, ExportOptions(speakerNames = false)),
        )
    }

    @Test
    fun `timestamps put every line on its own`() {
        assertEquals(
            "[0:00] Okay, let's start.\n\n[0:15] Onboarding is done.\n\n[0:32] Good.\n",
            exportText(note, ExportOptions(speakerNames = false, timestamps = true)),
        )
    }

    @Test
    fun `a note without segments exports its text`() {
        assertEquals("Plain.\n", exportText(note.copy(segments = emptyList(), text = " Plain. "), ExportOptions()))
    }

    @Test
    fun `markdown carries summary, tasks and transcript`() {
        assertEquals(
            """
            # Weekly sync

            _2 Nov 2026, 09:42_

            ## Summary

            A quick sync.

            ## Tasks

            - [x] Copy pass

            ## Transcript

            **Ana:** Okay, let's start.

            **Speaker 2:** Onboarding is done. Good.

            """.trimIndent(),
            exportMarkdown(note, ExportOptions()),
        )
    }

    @Test
    fun `srt numbers and times every segment`() {
        assertEquals(
            "1\n00:00:00,000 --> 00:00:14,000\nAna: Okay, let's start.\n" +
                "\n2\n00:00:15,000 --> 00:00:31,000\nSpeaker 2: Onboarding is done.\n" +
                "\n3\n00:00:32,000 --> 00:00:48,500\nSpeaker 2: Good.\n",
            exportSrt(note, ExportOptions()),
        )
    }

    @Test
    fun `srt time spans hours`() {
        assertEquals("01:02:03,004", srtTime(3_723_004))
    }

    @Test
    fun `file names lose characters other apps reject`() {
        assertEquals("Q3 plan pricing", fileName("Q3 plan: pricing?"))
        assertEquals("Echo note", fileName(" / "))
    }
}
