package dev.andrej.echo.ui.profile

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageOptionsTest {

    @Test
    fun `featured languages come first with the remaining names sorted after a divider`() {
        val options = buildLanguageOptions(
            listOf("sl", "de", "es", "en", "fr", "en"),
            Locale.ENGLISH,
        )

        assertEquals(listOf("", "en", "es", "de", "fr", "sl"), options.map { it.code })
        assertEquals(listOf(false, false, false, false, true, false), options.map { it.dividerBefore })
        assertEquals("🌐", options.first().flag)
        assertEquals("🇬🇧", options[1].flag)
        assertEquals("🇪🇸", options[2].flag)
        assertEquals("🇩🇪", options[3].flag)
    }

    @Test
    fun `every Whisper language has a representative country flag`() {
        val whisperCodes = listOf(
            "en", "zh", "de", "es", "ru", "ko", "fr", "ja", "pt", "tr", "pl", "ca", "nl",
            "ar", "sv", "it", "id", "hi", "fi", "vi", "he", "uk", "el", "ms", "cs", "ro",
            "da", "hu", "ta", "no", "th", "ur", "hr", "bg", "lt", "la", "mi", "ml", "cy",
            "sk", "te", "fa", "lv", "bn", "sr", "az", "sl", "kn", "et", "mk", "br", "eu",
            "is", "hy", "ne", "mn", "bs", "kk", "sq", "sw", "gl", "mr", "pa", "si", "km",
            "sn", "yo", "so", "af", "oc", "ka", "be", "tg", "sd", "gu", "am", "yi", "lo",
            "uz", "fo", "ht", "ps", "tk", "nn", "mt", "sa", "lb", "my", "bo", "tl", "mg",
            "as", "tt", "haw", "ln", "ha", "ba", "jw", "su", "yue",
        )

        val options = buildLanguageOptions(whisperCodes, Locale.ENGLISH)

        assertEquals(whisperCodes.toSet().size, options.size - 1)
        assertTrue(options.drop(1).all { it.flag.isNotBlank() })
        assertFalse(options.drop(1).any { it.flag == "🌐" })
        assertFalse(options.drop(1).any { it.flag == "🏳️" })
    }
}
