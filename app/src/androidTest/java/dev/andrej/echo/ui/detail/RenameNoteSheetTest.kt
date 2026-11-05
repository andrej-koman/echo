package dev.andrej.echo.ui.detail

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import dev.andrej.echo.ui.theme.EchoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class RenameNoteSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun savesTrimmedTitleAndRejectsBlankInput() {
        var saved: String? = null
        compose.setContent {
            EchoTheme {
                RenameNoteSheet(true, "Existing title", onSave = { saved = it }, onDismiss = {})
            }
        }

        compose.onNodeWithContentDescription("Note title").assertTextEquals("Existing title")
        compose.onNodeWithContentDescription("Note title").performTextReplacement("   ")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithContentDescription("Note title").performTextReplacement("  Kitchen repairs  ")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals("Kitchen repairs", saved) }
    }

    @Test fun cancelDiscardsDraftAndReopeningPrefillsCurrentTitle() {
        val visible = mutableStateOf(true)
        var saved: String? = null
        compose.setContent {
            EchoTheme {
                RenameNoteSheet(visible.value, "Original title", onSave = { saved = it }, onDismiss = { visible.value = false })
            }
        }

        compose.onNodeWithContentDescription("Note title").performTextReplacement("Discard this")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertNull(saved)
            visible.value = true
        }
        compose.onNodeWithContentDescription("Note title").assertTextEquals("Original title")
    }
}
