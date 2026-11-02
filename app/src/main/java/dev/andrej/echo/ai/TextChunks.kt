package dev.andrej.echo.ai

/**
 * Splits numbered lines into runs of at most [maxWords], so each prompt and its answer fit the
 * model's context. Lines are never split, except one that alone is over the limit.
 */
fun chunkLines(lines: List<String>, maxWords: Int): List<List<IndexedValue<String>>> {
    val chunks = mutableListOf<List<IndexedValue<String>>>()
    var current = mutableListOf<IndexedValue<String>>()
    var words = 0

    for ((index, line) in lines.withIndex()) {
        for (piece in line.words().chunked(maxWords)) {
            if (words + piece.size > maxWords && current.isNotEmpty()) {
                chunks += current
                current = mutableListOf()
                words = 0
            }
            current += IndexedValue(index, piece.joinToString(" "))
            words += piece.size
        }
    }
    if (current.isNotEmpty()) chunks += current
    return chunks
}

/** A transcript with no timed segments, split at sentence ends. */
fun sentences(text: String): List<String> =
    text.trim().split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }

private fun String.words(): List<String> = trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
