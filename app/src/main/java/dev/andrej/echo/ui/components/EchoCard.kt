package dev.andrej.echo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.echoShadow
import dev.andrej.echo.ui.theme.hairline

enum class CardTone { Paper, Raised, Sage, Bronze, Cream, Inverse }

enum class CardPadding(val value: Dp) {
    None(0.dp),
    Sm(12.dp),
    Md(16.dp),
    Lg(20.dp),
}

private val CardRadius = 18.dp

@Composable
fun EchoCard(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Paper,
    padding: CardPadding = CardPadding.Md,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = EchoTheme.colors

    val background = when (tone) {
        CardTone.Paper -> colors.surfaceCard
        CardTone.Raised -> colors.surfaceRaised
        CardTone.Sage -> colors.surfaceSageSoft
        CardTone.Bronze -> colors.surfaceAccentSoft
        CardTone.Cream -> colors.surfacePage
        CardTone.Inverse -> colors.surfaceInverse
    }
    val foreground = when (tone) {
        CardTone.Inverse -> colors.textInverse
        CardTone.Sage -> colors.textSuccess
        CardTone.Bronze -> colors.textAccent
        else -> colors.textPrimary
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shadow = if (onClick != null && pressed) {
        EchoTheme.elevation.shadow3
    } else {
        EchoTheme.elevation.shadow1
    }

    CompositionLocalProvider(LocalContentColor provides foreground) {
        Column(
            modifier = modifier
                .echoShadow(shadow, cornerRadius = CardRadius)
                .clip(EchoTheme.radii.lg)
                .background(background)
                .then(
                    if (tone == CardTone.Inverse) Modifier
                    else Modifier.hairline(colors.borderSubtle, cornerRadius = CardRadius),
                )
                .then(
                    if (onClick == null) {
                        Modifier
                    } else {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
                        )
                    },
                )
                .padding(padding.value),
            content = content,
        )
    }
}
