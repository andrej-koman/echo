package dev.andrej.echo.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import dev.andrej.echo.data.Segment
import dev.andrej.echo.ui.theme.LocalReducedMotion
import kotlinx.coroutines.flow.first

private const val MIN_CHARS_PER_SECOND = 25f
private const val CATCH_UP_SECONDS = 2.5f

/**
 * [segments] as far as they have been "typed". Whisper hands over a 30 s window's lines at once,
 * so the reveal speeds up with the backlog and drains each lump in about [CATCH_UP_SECONDS],
 * reading as steady typing rather than jumps. Null [segments] means not loaded yet: whatever is
 * there once they load is shown at once, never retyped.
 */
@Composable
internal fun typedSegments(segments: List<Segment>?, typing: Boolean): List<Segment> {
    val all = segments.orEmpty()
    val animate = typing && !LocalReducedMotion.current
    val target by rememberUpdatedState(all.sumOf { it.text.trim().length })
    var shown by remember { mutableIntStateOf(Int.MAX_VALUE) }

    LaunchedEffect(animate, segments != null) {
        if (!animate || segments == null) return@LaunchedEffect
        shown = minOf(shown, target)
        var last = withFrameNanos { it }
        var carry = 0f
        while (true) {
            val backlog = target - shown
            if (backlog <= 0) {
                shown = target
                carry = 0f
                snapshotFlow { target }.first { it > shown }
                last = withFrameNanos { it }
                continue
            }
            val now = withFrameNanos { it }
            carry += maxOf(MIN_CHARS_PER_SECOND, backlog / CATCH_UP_SECONDS) * (now - last) / 1e9f
            last = now
            val step = carry.toInt()
            carry -= step
            shown = minOf(target, shown + step)
        }
    }

    if (!animate) return all
    var left = shown
    return buildList {
        for (segment in all) {
            if (left <= 0) break
            val text = segment.text.trim()
            add(if (text.length <= left) segment else segment.copy(text = text.take(left)))
            left -= text.length
        }
    }
}
