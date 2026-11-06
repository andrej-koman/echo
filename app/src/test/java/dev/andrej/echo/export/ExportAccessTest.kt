package dev.andrej.echo.export

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportAccessTest {
    @Test fun `free users can export only text`() {
        assertTrue(ExportFormat.Text.canExport(isPro = false))
        listOf(ExportFormat.Markdown, ExportFormat.Subtitles, ExportFormat.Audio).forEach {
            assertFalse(it.canExport(isPro = false))
        }
    }

    @Test fun `pro users can export every format`() {
        ExportFormat.entries.forEach { assertTrue(it.canExport(isPro = true)) }
    }
}
