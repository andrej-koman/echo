package dev.andrej.echo.ui.tasks

internal fun formatTaskText(text: String): String {
    val sentence = text.trim().replaceFirstChar { it.titlecase() }
    if (sentence.isEmpty()) return sentence
    val ending = sentence.trimEnd('"', '\'', '’', '”', ')', ']').lastOrNull()
    return if (ending in listOf('.', '!', '?', '…')) sentence else "$sentence."
}
