package dev.andrej.echo.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.andrej.echo.ui.theme.EchoShadow
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.echoShadow
import dev.andrej.echo.ui.theme.hairline

enum class ButtonVariant { Primary, Secondary, Soft, Ghost, Inverse, Danger }

enum class ButtonSize(val height: Dp, val horizontal: Dp, val gap: Dp) {
    Sm(34.dp, 14.dp, 6.dp),
    Md(44.dp, 20.dp, 8.dp),
    Lg(52.dp, 26.dp, 10.dp),
}

@Composable
fun EchoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Md,
    fullWidth: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    leading: @Composable (RowScope.() -> Unit)? = null,
) {
    val colors = EchoTheme.colors
    val off = !enabled || loading

    val background = when {
        off -> colors.actionDisabledBg
        variant == ButtonVariant.Primary -> colors.actionPrimaryBg
        variant == ButtonVariant.Secondary -> colors.actionSecondaryBg
        variant == ButtonVariant.Soft -> colors.surfaceAccentSoft
        variant == ButtonVariant.Ghost -> Color.Transparent
        variant == ButtonVariant.Inverse -> colors.actionInverseBg
        else -> colors.statusDangerBg
    }
    val foreground = when {
        off -> colors.actionDisabledFg
        variant == ButtonVariant.Primary -> colors.actionPrimaryFg
        variant == ButtonVariant.Secondary -> colors.actionSecondaryFg
        variant == ButtonVariant.Soft -> colors.textAccent
        variant == ButtonVariant.Ghost -> colors.actionSecondaryFg
        variant == ButtonVariant.Inverse -> colors.textInverse
        else -> colors.statusDangerFg
    }

    val shadow: List<EchoShadow> = when {
        off || variant == ButtonVariant.Ghost || variant == ButtonVariant.Soft ||
            variant == ButtonVariant.Danger -> emptyList()
        variant == ButtonVariant.Secondary -> EchoTheme.elevation.shadow1
        else -> EchoTheme.elevation.shadow2
    }

    val fontSize = when (size) {
        ButtonSize.Sm -> EchoTheme.typography.caption
        ButtonSize.Md -> EchoTheme.typography.bodySm
        ButtonSize.Lg -> EchoTheme.typography.body
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !off) EchoTheme.motion.pressScale else 1f,
        label = "buttonPress",
    )

    Row(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .height(size.height)
            .scale(scale)
            .echoShadow(shadow, cornerRadius = size.height / 2)
            .clip(EchoTheme.radii.pill)
            .background(background)
            .then(
                if (variant == ButtonVariant.Secondary && !off) {
                    Modifier.hairline(colors.borderDefault, cornerRadius = size.height / 2)
                } else {
                    Modifier
                },
            )
            .clickable(
                enabled = !off,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = size.horizontal)
            .alpha(if (loading) 0.75f else 1f),
        horizontalArrangement = Arrangement.spacedBy(size.gap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides foreground) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = foreground,
                    strokeWidth = 2.dp,
                )
            } else {
                leading?.invoke(this)
            }
            Text(
                text = text,
                style = fontSize.merge(TextStyle(fontWeight = FontWeight.Medium)),
                color = foreground,
            )
        }
    }
}
