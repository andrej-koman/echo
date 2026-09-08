package dev.andrej.echo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.rememberEchoHaptics

data class TabItem(
    val route: String,
    val label: String,
    val iconRes: Int,
)

@Composable
fun TabBar(
    items: List<TabItem>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    centerGap: Boolean = false,
    centerGapWidth: Dp = 72.dp,
) {
    val colors = EchoTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceBar)
            .drawBehind {
                drawLine(
                    color = colors.borderSubtle,
                    start = Offset.Zero,
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(top = 10.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            if (centerGap && index == items.size / 2) {
                Spacer(modifier = Modifier.width(centerGapWidth))
            }
            Tab(
                item = item,
                active = item.route == selected,
                onClick = { onSelect(item.route) },
            )
        }
    }
}

@Composable
private fun Tab(
    item: TabItem,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EchoTheme.colors
    val haptics = rememberEchoHaptics()
    val interactionSource = remember { MutableInteractionSource() }

    val tint = if (active) colors.textAccent else colors.textTertiary
    val pillColor = if (active) colors.surfaceAccentSoft else Color.Transparent

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = EchoTheme.spacing.tapMin)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.tick()
                    onClick()
                },
            )
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(36.dp)
                .clip(EchoTheme.radii.pill)
                .background(pillColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(item.iconRes),
                contentDescription = item.label,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
