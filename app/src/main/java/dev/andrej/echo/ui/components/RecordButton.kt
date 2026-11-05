package dev.andrej.echo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.andrej.echo.ui.theme.Bronze400
import dev.andrej.echo.ui.theme.Bronze500
import dev.andrej.echo.ui.theme.Clay400
import dev.andrej.echo.ui.theme.Clay500
import dev.andrej.echo.R
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.echoShadow
import dev.andrej.echo.ui.theme.rememberEchoHaptics

enum class RecordButtonState { Idle, Recording }

enum class RecordButtonSize(val diameter: Dp) {
    Sm(64.dp),
    Md(72.dp),
    Lg(96.dp),
}

@Composable
fun RecordButton(
    state: RecordButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: RecordButtonSize = RecordButtonSize.Lg,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val live = state == RecordButtonState.Recording
    val diameter = size.diameter

    val haptics = rememberEchoHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val label = contentDescription
        ?: if (live) "Stop recording" else "Start recording"

    Box(
        modifier = modifier.size(diameter * 1.55f),
        contentAlignment = Alignment.Center,
    ) {

        Box(
            modifier = Modifier
                .size(diameter)
                .then(
                    if (live) {
                        Modifier.echoShadow(EchoTheme.elevation.haloClay, cornerRadius = diameter / 2)
                    } else {
                        Modifier
                    },
                )
                .echoShadow(EchoTheme.elevation.shadow3, cornerRadius = diameter / 2)
                .clip(EchoTheme.radii.pill)
                .background(
                    Brush.linearGradient(
                        colors = if (live) {
                            listOf(Clay400, Clay500)
                        } else {
                            listOf(Bronze400, Bronze500)
                        },
                        start = Offset.Zero,
                        end = Offset.Infinite,
                    ),
                )
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (live) haptics.release() else haptics.engage()
                        onClick()
                    },
                )
                .semantics { this.contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            if (live) {
                StopGlyph(diameter)
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_mic),
                    contentDescription = null,
                    tint = EchoTheme.colors.recordGlyph,
                    modifier = Modifier.size(diameter * 0.42f),
                )
            }
        }
    }
}

@Composable
private fun StopGlyph(diameter: Dp) {
    val side = diameter * 0.26f
    val radius = maxOf(3.dp, diameter * 0.06f)
    Box(
        modifier = Modifier
            .size(side)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(radius))
            .background(EchoTheme.colors.recordGlyph),
    )
}
