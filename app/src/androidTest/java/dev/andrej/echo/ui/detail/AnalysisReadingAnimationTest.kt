package dev.andrej.echo.ui.detail

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.LocalReducedMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AnalysisReadingAnimationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val analyzing = mutableStateOf(false)
    private val ready = mutableStateOf(true)
    private val fullText = mutableStateOf(false)
    private val interacting = mutableStateOf(false)
    private lateinit var reading: AnalysisReading
    private lateinit var list: LazyListState
    private var paragraphs = List(24) { index ->
        "$index. " + "This passage is part of a long transcript that Echo is analyzing. ".repeat(12)
    }

    @OptIn(ExperimentalFoundationApi::class)
    private fun show(reduced: Boolean = false, initialIndex: Int = 0, stickyTabs: Boolean = false) {
        compose.runOnUiThread {
            compose.activity.setShowWhenLocked(true)
            compose.activity.setTurnScreenOn(true)
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        compose.setContent {
            EchoTheme {
                CompositionLocalProvider(LocalReducedMotion provides reduced) {
                    list = rememberLazyListState(initialIndex)
                    val currentReading = rememberAnalysisReading(
                        noteId = "test", analyzing = analyzing.value, ready = ready.value,
                        showingFullText = fullText.value, interacting = interacting.value,
                        paragraphs = paragraphs, listState = list, firstParagraphIndex = if (stickyTabs) 2 else 1,
                        onStart = { fullText.value = true },
                    )
                    reading = currentReading
                    LazyColumn(state = list, modifier = Modifier.height(480.dp).testTag("reading-list")) {
                        item(key = "title") { Box(Modifier.fillMaxWidth().height(80.dp)) }
                        if (stickyTabs) {
                            stickyHeader(key = "tabs") { Box(Modifier.fillMaxWidth().height(80.dp)) }
                        }
                        itemsIndexed(paragraphs, key = { index, _ -> "p$index" }) { index, text ->
                            FullTextLine(TextFieldValue(text), editing = false, onValueChange = {},
                                reading = currentReading, readingIndex = index)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun change(state: MutableState<Boolean>, value: Boolean) {
        compose.runOnIdle { state.value = value }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun advance(milliseconds: Long) {
        var remaining = milliseconds
        while (remaining > 0) {
            val step = minOf(remaining, 250L)
            compose.mainClock.advanceTimeBy(step)
            compose.waitForIdle()
            remaining -= step
        }
    }

    @Test fun waitsForTypingThenReturnsToBeginningAndStopsOnCompletion() {
        paragraphs = List(24) { index ->
            "$index. " + "This passage is part of a long transcript that Echo is analyzing. ".repeat(30)
        }
        ready.value = false
        show(initialIndex = 10)
        change(analyzing, true)
        advance(1000)
        compose.runOnIdle {
            assertFalse(reading.visible)
            assertFalse(fullText.value)
            assertEquals(10, list.firstVisibleItemIndex)
        }
        change(ready, true)
        advance(2500)
        compose.runOnIdle {
            assertTrue(fullText.value)
            assertTrue(reading.visible)
            assertEquals(0, reading.session.current?.segment)
            assertTrue(list.firstVisibleItemIndex <= 1)
        }
        advance(36000)
        compose.runOnIdle {
            val current = requireNotNull(reading.session.current)
            assertEquals(0, current.segment)
            assertTrue("position=$current, lines=${reading.session.layouts[0]?.lines?.size}", current.passage.top > 0f)
            assertTrue(list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0)
        }
        change(analyzing, false)
        compose.runOnIdle {
            assertFalse(reading.visible)
            assertEquals(null, reading.session.current)
        }
    }

    @Test fun dragStopsTheSweepAndDoesNotResume() {
        show()
        change(analyzing, true)
        advance(1800)
        compose.runOnIdle { assertNotNull("visible=${reading.visible}, stopped=${reading.session.stopped}, layouts=${reading.session.layouts.keys}", reading.session.current) }
        compose.onNodeWithTag("reading-list").performTouchInput { swipeUp() }
        advance(1000)
        compose.runOnIdle {
            assertTrue(reading.session.stopped)
            assertFalse(reading.visible)
            assertEquals(null, reading.session.current)
        }
        advance(6000)
        compose.runOnIdle { assertEquals(null, reading.session.current) }
    }

    @Test fun switchingTabsOrEditingStopsUntilANewAnalysisAttempt() {
        show()
        change(analyzing, true)
        advance(1800)
        change(fullText, false)
        change(fullText, true)
        compose.runOnIdle { assertTrue(reading.session.stopped) }
        change(analyzing, false)
        change(analyzing, true)
        advance(1800)
        compose.runOnIdle { assertNotNull("visible=${reading.visible}, stopped=${reading.session.stopped}, layouts=${reading.session.layouts.keys}", reading.session.current) }
        change(interacting, true)
        change(interacting, false)
        advance(4000)
        compose.runOnIdle { assertTrue(reading.session.stopped) }
    }

    @Test fun reducedMotionHighlightsVisibleTextWithoutScrollingOrAdvancing() {
        show(reduced = true, initialIndex = 10)
        change(analyzing, true)
        advance(1000)
        var initial: ReadingPosition? = null
        compose.runOnIdle {
            assertTrue(reading.visible)
            assertEquals(10, list.firstVisibleItemIndex)
            assertNotNull("visible=${reading.visible}, stopped=${reading.session.stopped}, layouts=${reading.session.layouts.keys}", reading.session.current)
            initial = reading.session.current
        }
        advance(10000)
        compose.runOnIdle {
            assertEquals(10, list.firstVisibleItemIndex)
            assertEquals(initial, reading.session.current)
        }
    }

    @Test fun reducedMotionSkipsPassagesHiddenUnderStickyTabs() {
        paragraphs = List(20) { "Short passage $it." }
        show(reduced = true, initialIndex = 2, stickyTabs = true)
        change(analyzing, true)
        advance(1000)
        compose.runOnIdle {
            val current = requireNotNull(reading.session.current)
            val info = list.layoutInfo
            val item = info.visibleItemsInfo.first { it.key == "p${current.segment}" }
            val header = info.visibleItemsInfo.first { it.key == "tabs" }
            assertTrue("The static highlight must be below the pinned tabs", item.offset + item.size > header.size)
        }
    }

    @Test fun highlightsOneWordAtATimeAndLoopsThroughEveryParagraph() {
        paragraphs = listOf("First second.", "Third fourth.")
        show()
        change(analyzing, true)
        val visited = mutableSetOf<String>()
        var previous: ReadingPosition? = null
        var loops = 0
        repeat(60) {
            advance(80)
            compose.runOnIdle {
                reading.session.current?.let { current ->
                    val word = paragraphs[current.segment].substring(current.passage.start, current.passage.end)
                    assertFalse("Highlight must cover one word: $word", word.any(Char::isWhitespace))
                    visited += word
                    if (previous?.segment == 1 && current.segment == 0) loops++
                    previous = current
                }
            }
        }
        assertEquals(setOf("First", "second.", "Third", "fourth."), visited)
        assertTrue("The sweep should restart while analysis is active", loops >= 2)
    }

    @Test fun whitespaceOnlyTranscriptDoesNotStartASweep() {
        paragraphs = listOf("  \n\t", "")
        show()
        change(analyzing, true)
        advance(3000)
        compose.runOnIdle { assertEquals(null, reading.session.current) }
    }
}
