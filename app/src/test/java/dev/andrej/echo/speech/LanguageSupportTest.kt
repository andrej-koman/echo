package dev.andrej.echo.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageSupportTest {

    @Test
    fun `nothing to prefetch once any language is installed`() {
        val support = LanguageSupport(installed = listOf("en-GB"), supported = listOf("de-DE"))

        assertNull(support.toPrefetch("de-DE"))
    }

    @Test
    fun `prefetches the device tag when it is supported`() {
        val support = LanguageSupport(installed = emptyList(), supported = listOf("en-US", "de-DE"))

        assertEquals("de-DE", support.toPrefetch("de-DE"))
    }

    @Test
    fun `falls back to another region of the device language`() {
        val support = LanguageSupport(installed = emptyList(), supported = listOf("en-US", "de-DE"))

        assertEquals("en-US", support.toPrefetch("en-SI"))
    }

    @Test
    fun `nothing to prefetch when the device language is not supported at all`() {
        val support = LanguageSupport(installed = emptyList(), supported = listOf("en-US"))

        assertNull(support.toPrefetch("sl-SI"))
    }
}
