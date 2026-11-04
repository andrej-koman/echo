package dev.andrej.echo.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import dev.andrej.echo.ui.theme.EchoTheme
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CalendarGestureTest {
    @get:Rule val compose = createComposeRule()

    @Test fun handleOnlyChangesCalendarOnDirectionalDrag() {
        val day = LocalDate.of(2026, 11, 4)
        val state = buildHistoryState(emptyList(), emptyList(), YearMonth.from(day), day, day)
        var isExpanded = false
        compose.setContent {
            EchoTheme {
                var expanded by remember { mutableStateOf(false) }
                CalendarPanel(state, expanded, { expanded = !expanded; isExpanded = expanded }, {}, {}, {}, {})
            }
        }
        val handle = compose.onNodeWithContentDescription("Calendar drag handle")
        handle.performTouchInput { click() }
        compose.runOnIdle { assertEquals(false, isExpanded) }
        handle.performTouchInput { swipe(start = center, end = center + Offset(0f, 220f), durationMillis = 500) }
        compose.runOnIdle { assertEquals(true, isExpanded) }
        handle.performTouchInput { click() }
        compose.runOnIdle { assertEquals(true, isExpanded) }
        handle.performTouchInput { swipe(start = center, end = center - Offset(0f, 220f), durationMillis = 500) }
        compose.runOnIdle { assertEquals(false, isExpanded) }
    }
}
