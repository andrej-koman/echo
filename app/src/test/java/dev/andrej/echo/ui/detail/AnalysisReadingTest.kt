package dev.andrej.echo.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisReadingTest {
    @Test fun emptyLayoutHasNoPassages() {
        assertTrue(readingPassages(emptyList()).isEmpty())
    }

    @Test fun longParagraphAdvancesInSmallPassagesWithoutLosingItsLastLine() {
        val lines = List(8) { ReadingLine(it * 10, (it + 1) * 10, it * 24f, (it + 1) * 24f) }
        val passages = readingPassages(lines)
        assertEquals(listOf(0 until 30, 30 until 60, 60 until 80), passages.map { it.start until it.end })
        assertEquals(listOf(0f, 72f, 144f), passages.map { it.top })
        assertEquals(listOf(72f, 144f, 192f), passages.map { it.bottom })
    }

    @Test fun shortParagraphStaysOnePassage() {
        val lines = listOf(ReadingLine(0, 12, 0f, 24f), ReadingLine(12, 20, 24f, 48f))
        assertEquals(listOf(ReadingPassage(0, 20, 0f, 48f)), readingPassages(lines))
    }

    @Test fun blankLinesDoNotCreateAnEmptyHighlight() {
        assertTrue(readingPassages(listOf(ReadingLine(0, 0, 0f, 24f))).isEmpty())
    }
}
