package dev.andrej.echo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import dev.andrej.echo.ui.theme.EchoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SheetGestureTest {
    @get:Rule val compose = createComposeRule()

    private fun show(onDismiss: () -> Unit) {
        compose.setContent {
            EchoTheme {
                // A sheet must cover the window even from a constrained caller.
                Box(Modifier.height(200.dp)) {
                    Sheet(visible = true, onDismiss = onDismiss) { Text("Sheet content") }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun outsideTapDismisses() {
        var dismissed = 0
        show { dismissed++ }
        compose.onNodeWithContentDescription("Dismiss sheet").performTouchInput { click(Offset(10f, 10f)) }
        compose.runOnIdle { assertEquals(1, dismissed) }
    }

    @Test fun handleTapDoesNotDismiss() {
        var dismissed = 0
        show { dismissed++ }
        compose.onNodeWithContentDescription("Sheet drag handle").performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, dismissed) }
    }

    @Test fun downwardHandleDragDismisses() {
        var dismissed = 0
        show { dismissed++ }
        compose.onNodeWithContentDescription("Sheet drag handle").performTouchInput {
            swipe(start = center, end = center + Offset(0f, 300f), durationMillis = 500)
        }
        compose.runOnIdle { assertEquals(1, dismissed) }
    }

    @Test fun tappingContentDoesNotDismiss() {
        var dismissed = 0
        show { dismissed++ }
        compose.onNodeWithText("Sheet content").performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, dismissed) }
    }
}
