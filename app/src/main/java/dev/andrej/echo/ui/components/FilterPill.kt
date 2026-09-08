package dev.andrej.echo.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.hairline
import dev.andrej.echo.ui.theme.rememberEchoHaptics

/** A small toggleable pill chip — day/time quick-picks in the task editor. */
@Composable
fun FilterPill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EchoTheme.colors
    val haptics = rememberEchoHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) EchoTheme.motion.pressScale else 1f,
        label = "filterPillPress",
    )

    Box(
        modifier = modifier
            .height(32.dp)
            .scale(scale)
            .clip(EchoTheme.radii.pill)
            .background(if (active) colors.actionPrimaryBg else colors.surfaceCard)
            .then(
                if (active) Modifier else Modifier.hairline(colors.borderDefault, cornerRadius = 16.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.tick()
                    onClick()
                },
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = EchoTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
            color = if (active) colors.actionPrimaryFg else colors.textSecondary,
        )
    }
}
