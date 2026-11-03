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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
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
        LineText(value.text, highlight, live, modifier)
    }
}

/** Full text: the same segments without speakers, one paragraph each. */
@Composable
internal fun Paragraph(
    text: String,
    current: Boolean,
    highlight: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    live: Boolean = false,
) {
    val colors = EchoTheme.colors
    LineText(
        text = text,
        highlight = highlight,
        live = live,
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
private fun LineText(text: String, highlight: String, live: Boolean, modifier: Modifier) {
    val colors = EchoTheme.colors
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
            append(highlighted(text, highlight, colors.surfaceAccentSoft))
            if (live) withStyle(SpanStyle(color = colors.textAccent.copy(alpha = caretAlpha))) { append(" ▍") }
        },
        style = EchoTheme.typography.body,
        color = colors.textPrimary,
        modifier = modifier,
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

private fun highlighted(text: String, query: String, background: Color): AnnotatedString =
    buildAnnotatedString {
        append(text)
        matchRanges(text, query).forEach { range ->
            addStyle(SpanStyle(background = background), range.first, range.last + 1)
        }
    }
