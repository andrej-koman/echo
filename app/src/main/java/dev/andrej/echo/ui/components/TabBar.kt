package dev.andrej.echo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.andrej.echo.ui.theme.EchoTheme

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
            .padding(
                top = 8.dp,
                bottom = 10.dp,
                start = EchoTheme.spacing.gutterScreen,
                end = EchoTheme.spacing.gutterScreen,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            Tab(
                item = item,
                active = item.route == selected,
                onClick = { onSelect(item.route) },
                modifier = Modifier.weight(1f),
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
    val interactionSource = remember { MutableInteractionSource() }

    val tint by animateColorAsState(
        targetValue = if (active) colors.textAccent else colors.textTertiary,
        label = "tabTint",
    )
    val pillColor by animateColorAsState(
        targetValue = if (active) colors.surfaceAccentSoft else Color.Transparent,
        label = "tabPill",
    )

    Column(
        modifier = modifier
            .defaultMinSize(minHeight = EchoTheme.spacing.tapMin)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(26.dp)
                .clip(EchoTheme.radii.pill)
                .background(pillColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(item.iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(19.dp),
            )
        }
        Text(
            text = item.label,
            style = EchoTheme.typography.micro.copy(
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            ),
            color = tint,
        )
    }
}
