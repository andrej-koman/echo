package dev.andrej.echo.ui.theme

import dev.andrej.echo.data.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test
    fun `system choice follows the phone`() {
        assertTrue(resolveDarkTheme(ThemeMode.System, systemDark = true))
        assertFalse(resolveDarkTheme(ThemeMode.System, systemDark = false))
    }

    @Test
    fun `light and dark choices override the phone`() {
        assertFalse(resolveDarkTheme(ThemeMode.Light, systemDark = true))
        assertTrue(resolveDarkTheme(ThemeMode.Dark, systemDark = false))
    }
}
