package dev.andrej.echo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.andrej.echo.ui.theme.EchoTheme

/** A pill toggle with a visible sliding knob — Material's Switch renders track and thumb the
 *  same tan when only checkedThumbColor is set, so "on" and "off" both look like a solid fill. */
@Composable
fun EchoSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = EchoTheme.colors
    val trackColor by animateColorAsState(if (checked) colors.actionPrimaryBg else colors.borderSubtle, label = "switchTrack")
    val knobOffset by animateDpAsState(if (checked) 22.dp else 2.dp, label = "switchKnob")
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 24.dp)
            .clip(EchoTheme.radii.pill)
            .background(trackColor)
            .then(
                if (enabled) Modifier.clickable(onClick = { onCheckedChange(!checked) }) else Modifier,
            ),
    ) {
        Box(
            modifier = Modifier
                .padding(start = knobOffset, top = 2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(colors.surfaceCard),
        )
    }
}
