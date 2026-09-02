package dev.andrej.echo.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.andrej.echo.ui.theme.EchoTheme

enum class IconButtonVariant { Ghost, Soft, Inverse }

enum class IconButtonSize(val box: Dp, val icon: Dp) {
    Sm(32.dp, 18.dp),
    Md(40.dp, 20.dp),
}

@Composable
fun EchoIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: IconButtonVariant = IconButtonVariant.Ghost,
    size: IconButtonSize = IconButtonSize.Sm,
    tint: Color? = null,
) {
    val colors = EchoTheme.colors

    val background = when (variant) {
        IconButtonVariant.Ghost -> Color.Transparent
        IconButtonVariant.Soft -> colors.surfaceAccentSoft
        IconButtonVariant.Inverse -> colors.surfaceInverse
    }
    val foreground = tint ?: when (variant) {
        IconButtonVariant.Ghost -> colors.textSecondary
        IconButtonVariant.Soft -> colors.textAccent
        IconButtonVariant.Inverse -> colors.textInverse
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) EchoTheme.motion.pressScale else 1f,
        label = "iconButtonPress",
    )

    Box(
        modifier = modifier
            .size(size.box)
            .scale(scale)
            .clip(EchoTheme.radii.pill)
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = foreground,
            modifier = Modifier.size(size.icon),
        )
    }
}
