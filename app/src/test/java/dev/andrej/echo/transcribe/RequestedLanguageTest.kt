package dev.andrej.echo.transcribe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RequestedLanguageTest {
    @Test fun `a note without its own choice follows settings`() {
        assertEquals("sl", requestedLanguage(null, "sl"))
        assertNull(requestedLanguage(null, null))
    }

    @Test fun `a note's language wins over settings`() {
        assertEquals("sl", requestedLanguage("sl", "en"))
    }

    @Test fun `an empty choice auto-detects even when settings name a language`() {
        assertNull(requestedLanguage("", "en"))
    }
}
