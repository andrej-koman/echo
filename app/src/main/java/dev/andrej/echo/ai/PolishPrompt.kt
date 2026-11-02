package dev.andrej.echo.ai

fun buildPolishPrompt(text: String): String = """
    Clean up this speech-to-text transcript. Remove filler words (um, uh, like, you know, I mean)
    and false starts, fix punctuation and capitalization, and break it into paragraphs where the
    topic changes. Keep every other word as spoken, in the language spoken: do not summarize,
    shorten, reorder or answer it. Reply with the cleaned text only.

    Transcript:
    ""${'"'}
    $text
    ""${'"'}
""".trimIndent()

/**
 * Null when the reply is not a plausible cleanup of [original]: removing fillers shortens a
 * transcript a little, so a reply under half its length was summarized, and one far longer
 * was answered or continued.
 */
fun parsePolish(raw: String, original: String): String? {
    val text = raw.trim()
        .removePrefix("```").removeSuffix("```")
        .trim().removeSurrounding("\"\"\"").trim()
    val ratio = text.wordCount().toFloat() / original.wordCount().coerceAtLeast(1)
    return text.takeIf { it.isNotEmpty() && ratio in 0.5f..1.3f }
}

private fun String.wordCount() = split(Regex("\\s+")).count { it.isNotBlank() }
