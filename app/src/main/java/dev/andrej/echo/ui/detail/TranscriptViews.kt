package dev.andrej.echo.ui.detail

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.andrej.echo.R
import dev.andrej.echo.data.TaskTextLink
import dev.andrej.echo.data.Segment
import dev.andrej.echo.ui.components.ButtonSize
import dev.andrej.echo.ui.components.ButtonVariant
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.EchoSearchField
import dev.andrej.echo.ui.notes.clock
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.LocalReducedMotion

/** Heads a run of lines from one speaker: `● Speaker 1  0:00 – 0:14`. */
@Composable
internal fun SpeakerHeader(
    speaker: Int?,
    label: String?,
    startMs: Long,
    endMs: Long,
    playing: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(top = EchoTheme.spacing.s4)
            .clip(EchoTheme.radii.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = EchoTheme.spacing.s3, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label != null) {
            SpeakerDot(speaker, size = 6)
            Text(
                text = label,
                style = EchoTheme.typography.micro.copy(fontWeight = FontWeight.SemiBold),
                color = speakerColor(speaker),
            )
        }
        Text(
            text = "${clock(startMs)} – ${clock(endMs)}",
            style = EchoTheme.typography.micro,
            color = EchoTheme.colors.textTertiary,
        )
        if (playing) {
            Icon(
                painter = painterResource(R.drawable.ic_audio_waveform),
                contentDescription = null,
                tint = EchoTheme.colors.textAccent,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SegmentLine(
    value: TextFieldValue,
    current: Boolean,
    editing: Boolean,
    selected: Boolean,
    highlight: String,
    onValueChange: (TextFieldValue) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    live: Boolean = false,
    taskLinks: List<TaskTextLink> = emptyList(),
    onTaskClick: (String) -> Unit = {},
) {
    val colors = EchoTheme.colors
    val modifier = Modifier
        .fillMaxWidth()
        .clip(EchoTheme.radii.md)
        .background(
            when {
                selected -> colors.surfaceAccentSoft
                current && !editing -> colors.surfaceCard
                else -> Color.Transparent
            },
        )
        .then(if (editing) Modifier else Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick))
        .padding(horizontal = EchoTheme.spacing.s3, vertical = EchoTheme.spacing.s2)
    if (editing) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = EchoTheme.typography.body.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.textAccent),
            modifier = modifier
                .clip(EchoTheme.radii.xs)
                .background(colors.surfaceSunken)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    } else {
        LineText(value.text, highlight, live, modifier, taskLinks, onTaskClick)
    }
}

@Composable
internal fun ContinuousTranscript(
    segments: List<Segment>,
    taskLinks: List<List<TaskTextLink>>,
    current: Int,
    highlight: String,
    live: Boolean,
    onTaskClick: (String) -> Unit,
    onSegmentClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onSegmentLongClick: ((Int) -> Unit)? = null,
    reading: AnalysisReading? = null,
) {
    val lines = remember(segments) { segments.map { it.text.trim() } }
    val text = remember(lines) { lines.joinToString(" ") }
    val starts = remember(lines) { lines.runningFold(0) { start, line -> start + line.length + 1 } }
    fun segmentAtOffset(offset: Int) = lines.indices.lastOrNull { starts[it] <= offset } ?: 0
    val links = remember(lines, taskLinks) {
        lines.indices.flatMap { index ->
            taskLinks.getOrNull(index).orEmpty().mapNotNull { link ->
                val end = minOf(link.end, lines[index].length)
                if (link.start >= end) null
                else link.copy(start = starts[index] + link.start, end = starts[index] + end)
            }
        }.groupBy { it.taskId }.flatMap { (_, spans) ->
            val merged = mutableListOf<TaskTextLink>()
            spans.sortedBy { it.start }.forEach { span ->
                val previous = merged.lastOrNull()
                if (previous != null && span.start <= previous.end + 1) {
                    merged[merged.lastIndex] = previous.copy(end = maxOf(previous.end, span.end))
                } else merged += span
            }
            merged
        }.sortedBy { it.start }
    }
    LineText(
        text = text,
        highlight = highlight,
        live = live,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = EchoTheme.spacing.s3, vertical = EchoTheme.spacing.s2),
        taskLinks = links,
        onTaskClick = onTaskClick,
        currentRange = current.takeIf { it in lines.indices }?.let { starts[it] until starts[it] + lines[it].length },
        onTextClick = { onSegmentClick(segmentAtOffset(it)) },
        onTextLongClick = onSegmentLongClick?.let { click -> { offset -> click(segmentAtOffset(offset)) } },
        reading = reading,
        readingIndex = 0,
    )
}

@Composable
internal fun Paragraph(
    text: String,
    current: Boolean,
    highlight: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    taskLinks: List<TaskTextLink> = emptyList(),
    onTaskClick: (String) -> Unit = {},
    reading: AnalysisReading? = null,
    readingIndex: Int = -1,
) {
    val colors = EchoTheme.colors
    LineText(
        text = text,
        highlight = highlight,
        live = live,
        taskLinks = taskLinks,
        onTaskClick = onTaskClick,
        reading = reading,
        readingIndex = readingIndex,
        modifier = modifier
            .fillMaxWidth()
            .clip(EchoTheme.radii.md)
            .background(if (current) colors.surfaceCard else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = EchoTheme.spacing.s3, vertical = EchoTheme.spacing.s2),
    )
}

/** The [live] line is the one being typed in as Whisper hears it, with a blinking caret. */
@Composable
private fun LineText(
    text: String,
    highlight: String,
    live: Boolean,
    modifier: Modifier,
    taskLinks: List<TaskTextLink> = emptyList(),
    onTaskClick: (String) -> Unit = {},
    currentRange: IntRange? = null,
    onTextClick: ((Int) -> Unit)? = null,
    onTextLongClick: ((Int) -> Unit)? = null,
    reading: AnalysisReading? = null,
    readingIndex: Int = -1,
) {
    val colors = EchoTheme.colors
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    LaunchedEffect(reading?.session, layout) {
        layout?.let { reading?.onTextLayout(readingIndex, it) }
    }
    val caretAlpha = if (live && !LocalReducedMotion.current) {
        rememberInfiniteTransition(label = "caret").animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(530, easing = { if (it < 0.5f) 0f else 1f }), RepeatMode.Restart),
            label = "caret",
        ).value
    } else {
        1f
    }
    Text(
        text = buildAnnotatedString {
            append(text)
            currentRange?.let { addStyle(SpanStyle(background = colors.surfaceCard), it.first, it.last + 1) }
            taskLinks.forEach { link ->
                if (link.start >= 0 && link.end <= text.length && link.start < link.end) {
                    addLink(
                        LinkAnnotation.Clickable(
                            tag = link.taskId,
                            styles = TextLinkStyles(style = SpanStyle()),
                            linkInteractionListener = { onTaskClick(link.taskId) },
                        ),
                        link.start,
                        link.end,
                    )
                }
            }
            matchRanges(text, highlight).forEach { range ->
                addStyle(SpanStyle(background = colors.surfaceAccentSoft), range.first, range.last + 1)
            }
            if (live) withStyle(SpanStyle(color = colors.textAccent.copy(alpha = caretAlpha))) { append(" ▍") }
        },
        style = EchoTheme.typography.body.copy(lineHeight = 26.sp),
        color = colors.textPrimary,
        onTextLayout = { layout = it },
        modifier = modifier.then(reading?.highlight(readingIndex) ?: Modifier).drawBehind {
            val measured = layout ?: return@drawBehind
            taskLinks.forEach { link ->
                if (link.start < 0 || link.end > measured.layoutInput.text.length || link.start >= link.end) return@forEach
                val color = colors.taskHighlight
                (link.start until link.end).groupBy { measured.getLineForOffset(it) }.values.forEach { offsets ->
                    val first = measured.getBoundingBox(offsets.first())
                    val last = measured.getBoundingBox(offsets.last())
                    val left = (first.left - 2.dp.toPx()).coerceAtLeast(0f)
                    val right = (last.right + 2.dp.toPx()).coerceAtMost(size.width)
                    val top = first.top + 2.5.dp.toPx()
                    val box = Size(right - left, last.bottom - first.top - 5.dp.toPx())
                    drawRoundRect(color, Offset(left, top), box, CornerRadius(4.dp.toPx()))
                }
            }
        }.then(
            if (onTextClick == null) Modifier else Modifier.pointerInput(text, taskLinks, onTextClick, onTextLongClick) {
                detectTapGestures(
                    onTap = { position ->
                        layout?.getOffsetForPosition(position)?.let { offset ->
                            val link = taskLinks.firstOrNull { offset >= it.start && offset < it.end }
                            if (link != null) onTaskClick(link.taskId) else onTextClick(offset)
                        }
                    },
                    onLongPress = onTextLongClick?.let { click ->
                        { position -> layout?.getOffsetForPosition(position)?.let(click) }
                    },
                )
            },
        ),
    )
}

@Composable
internal fun SelectionBar(count: Int, onAssign: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (count == 1) "1 line selected" else "$count lines selected",
            style = EchoTheme.typography.bodySm,
            color = EchoTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        EchoButton(text = "Speaker…", onClick = onAssign, variant = ButtonVariant.Soft, size = ButtonSize.Sm)
        EchoButton(text = "Cancel", onClick = onCancel, variant = ButtonVariant.Ghost, size = ButtonSize.Sm)
    }
}

@Composable
internal fun EditBar(canAssignSelection: Boolean, onAssignSelection: () -> Unit, onDone: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EchoButton(
            text = "Speaker for selection",
            onClick = onAssignSelection,
            variant = ButtonVariant.Secondary,
            size = ButtonSize.Sm,
            enabled = canAssignSelection,
            modifier = Modifier.weight(1f),
        )
        EchoButton(text = "Done", onClick = onDone, variant = ButtonVariant.Soft, size = ButtonSize.Sm)
    }
}

@Composable
internal fun FindReplaceBar(
    query: String,
    onQueryChange: (String) -> Unit,
    replacement: String,
    onReplacementChange: (String) -> Unit,
    matches: Int,
    canReplace: Boolean,
    onReplaceAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3)) {
        EchoSearchField(value = query, onValueChange = onQueryChange, placeholder = "Find in transcript")
        if (canReplace) {
            EchoSearchField(
                value = replacement,
                onValueChange = onReplacementChange,
                placeholder = "Replace with",
                iconRes = R.drawable.ic_pen_line,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    query.isEmpty() -> ""
                    matches == 1 -> "1 match"
                    else -> "$matches matches"
                },
                style = EchoTheme.typography.micro,
                color = EchoTheme.colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            if (canReplace) {
                EchoButton(
                    text = "Replace all",
                    onClick = onReplaceAll,
                    variant = ButtonVariant.Soft,
                    size = ButtonSize.Sm,
                    enabled = matches > 0,
                )
            }
        }
    }
}
