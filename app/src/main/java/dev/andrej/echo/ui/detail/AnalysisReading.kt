package dev.andrej.echo.ui.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.LocalReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first

internal data class ReadingLine(val start: Int, val end: Int, val top: Float, val bottom: Float)
internal data class ReadingPassage(
    val start: Int,
    val end: Int,
    val top: Float,
    val bottom: Float,
    val bounds: List<Rect> = emptyList(),
)

internal data class ReadingLayout(val lines: List<ReadingLine>, val words: List<ReadingPassage>)

internal fun readingPassages(lines: List<ReadingLine>): List<ReadingPassage> =
    lines.chunked(3).mapNotNull { group ->
        val first = group.first()
        val last = group.last()
        if (last.end <= first.start) null else ReadingPassage(first.start, last.end, first.top, last.bottom)
    }

internal data class ReadingPosition(val segment: Int, val passage: ReadingPassage)

internal class ReadingSession {
    var stopped by mutableStateOf(false)
    var started by mutableStateOf(false)
    var current by mutableStateOf<ReadingPosition?>(null)
    var previous by mutableStateOf<ReadingPosition?>(null)
    val fade = Animatable(0f)
    val layouts = mutableStateMapOf<Int, ReadingLayout>()

    fun stop() {
        stopped = true
        current = null
        previous = null
    }
}

internal class AnalysisReading(
    val session: ReadingSession,
    val visible: Boolean,
    private val color: Color,
) {
    fun stop() = session.stop()

    fun onTextLayout(segment: Int, layout: TextLayoutResult) {
        val lines = List(layout.lineCount) { line ->
            ReadingLine(layout.getLineStart(line), layout.getLineEnd(line), layout.getLineTop(line), layout.getLineBottom(line))
        }
        val words = Regex("[^\\s\\p{Z}]+").findAll(layout.layoutInput.text.text).map { match ->
            val bounds = match.range.groupBy { layout.getLineForOffset(it) }.values.map { offsets ->
                offsets.map { layout.getBoundingBox(it) }.reduce { a, b ->
                    Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
                }
            }
            ReadingPassage(match.range.first, match.range.last + 1, bounds.first().top, bounds.last().bottom, bounds)
        }.toList()
        val measured = ReadingLayout(lines, words)
        if (session.layouts[segment] != measured) session.layouts[segment] = measured
    }

    fun highlight(segment: Int): Modifier = Modifier.drawBehind {
        if (!visible || session.stopped) return@drawBehind
        val progress = session.fade.value
        val previous = session.previous
        val current = session.current
        val from = previous?.passage?.bounds?.singleOrNull()
        val to = current?.passage?.bounds?.singleOrNull()
        val gliding = previous?.segment == segment && current?.segment == segment &&
            from != null && to != null && from.top == to.top && from.bottom == to.bottom

        fun marker(rect: Rect, alpha: Float) {
            val padded = Rect(
                (rect.left - 4.dp.toPx()).coerceAtLeast(0f),
                (rect.top - 1.dp.toPx()).coerceAtLeast(0f),
                (rect.right + 4.dp.toPx()).coerceAtMost(size.width),
                (rect.bottom + 1.dp.toPx()).coerceAtMost(size.height),
            )
            val radius = CornerRadius(7.dp.toPx())
            drawRoundRect(
                color = color.copy(alpha = 0.30f * alpha),
                topLeft = padded.topLeft,
                size = padded.size,
                cornerRadius = radius,
            )
            drawRoundRect(
                color = color.copy(alpha = 0.55f * alpha),
                topLeft = padded.topLeft,
                size = padded.size,
                cornerRadius = radius,
                style = Stroke(0.8.dp.toPx()),
            )
        }
        fun band(position: ReadingPosition?, alpha: Float) {
            if (position?.segment != segment) return
            val passage = position.passage
            val bounds = passage.bounds.ifEmpty { listOf(Rect(0f, passage.top, size.width, passage.bottom)) }
            bounds.forEach { rect -> marker(rect, alpha) }
        }
        if (gliding) {
            marker(lerp(requireNotNull(from), requireNotNull(to), progress), 1f)
        } else {
            band(previous, 1f - progress)
            band(current, progress)
        }
    }
}

@Composable
internal fun rememberAnalysisReading(
    noteId: String,
    analyzing: Boolean,
    ready: Boolean,
    showingFullText: Boolean,
    interacting: Boolean,
    paragraphs: List<String>,
    listState: LazyListState,
    firstParagraphIndex: Int,
    onStart: () -> Unit,
): AnalysisReading {
    val session = remember(noteId, analyzing) { ReadingSession() }
    val reduced = LocalReducedMotion.current
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    val start by rememberUpdatedState(onStart)
    val texts by rememberUpdatedState(paragraphs)
    val inset = with(LocalDensity.current) { 20.dp.toPx() }
    val paragraphPadding = with(LocalDensity.current) { EchoTheme.spacing.s2.toPx() }
    val started = session.started
    val visible = analyzing && ready && session.started && !session.stopped &&
        showingFullText && !interacting && !dragged

    LaunchedEffect(analyzing, ready, interacting, dragged) {
        if (!analyzing) return@LaunchedEffect
        if (interacting || dragged) session.stop()
        if (ready && !session.started && !session.stopped) {
            start()
            session.started = true
        }
    }
    LaunchedEffect(started, showingFullText, interacting, dragged) {
        if (started && (!showingFullText || interacting || dragged)) session.stop()
    }

    LaunchedEffect(visible, reduced, firstParagraphIndex) {
        if (!visible || texts.isEmpty()) return@LaunchedEffect
        if (reduced) {
            val position = snapshotFlow {
                val info = listState.layoutInfo
                val header = info.visibleItemsInfo.firstOrNull { it.key == "tabs" }?.let { it.offset + it.size } ?: 0
                info.visibleItemsInfo.firstNotNullOfOrNull { item ->
                    val index = (item.key as? String)?.takeIf { it.startsWith("p") }?.drop(1)?.toIntOrNull()
                        ?.takeIf { it in texts.indices } ?: return@firstNotNullOfOrNull null
                    val lines = session.layouts[index]?.lines ?: return@firstNotNullOfOrNull null
                    val firstVisibleLine = lines.indexOfFirst {
                        item.offset + paragraphPadding + it.bottom > header &&
                            item.offset + paragraphPadding + it.top < info.viewportEndOffset
                    }
                    if (firstVisibleLine < 0) return@firstNotNullOfOrNull null
                    readingPassages(lines.drop(firstVisibleLine)).firstOrNull()?.let { ReadingPosition(index, it) }
                }
            }.first { it != null } ?: return@LaunchedEffect
            session.current = position
            session.fade.snapTo(1f)
            return@LaunchedEffect
        }

        session.current = null
        session.previous = null
        if (texts.all { it.isBlank() }) return@LaunchedEffect
        delay(300)
        while (currentCoroutineContext().isActive) {
            listState.animateScrollToItem(0)
            for (segment in texts.indices) {
                if (texts[segment].isBlank()) continue
                if (listState.layoutInfo.visibleItemsInfo.none { it.key == "p$segment" }) {
                    val header = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "tabs" }?.size ?: 0
                    listState.animateScrollToItem(firstParagraphIndex + segment, -(header + inset).toInt())
                }
                val words = snapshotFlow { session.layouts[segment] }.first { it != null }?.words.orEmpty()
                for (word in words) {
                    val info = listState.layoutInfo
                    val item = info.visibleItemsInfo.firstOrNull { it.key == "p$segment" }
                    val header = info.visibleItemsInfo.firstOrNull { it.key == "tabs" }?.size ?: 0
                    if (item != null) {
                        val top = item.offset + paragraphPadding + word.top
                        val bottom = item.offset + paragraphPadding + word.bottom
                        if (top < header + inset || bottom > info.viewportEndOffset - inset) {
                            val readingTop = header + inset + (info.viewportEndOffset - header) * 0.15f
                            listState.animateScrollBy(top - readingTop, tween(400, easing = FastOutSlowInEasing))
                        }
                    }
                    session.previous = session.current
                    session.current = ReadingPosition(segment, word)
                    session.fade.snapTo(0f)
                    session.fade.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
                    session.previous = null
                    delay(20)
                }
            }
        }
    }

    return AnalysisReading(session, visible, EchoTheme.colors.actionPrimaryBg)
}
