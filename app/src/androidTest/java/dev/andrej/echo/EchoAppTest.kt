package dev.andrej.echo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class EchoAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsRunningMessage() {
        composeRule.setContent { EchoTheme { EchoApp() } }
        composeRule.onNodeWithText("Echo is running.").assertIsDisplayed()
    }
}
