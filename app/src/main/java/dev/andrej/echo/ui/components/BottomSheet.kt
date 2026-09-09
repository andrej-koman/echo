package dev.andrej.echo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.ui.theme.EchoTheme
import kotlin.math.roundToInt

@Composable
fun ConfirmSheet(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    cancelLabel: String = "Cancel",
    destructive: Boolean = true,
) {
    Sheet(visible = visible, onDismiss = onDismiss, modifier = modifier) {
        Text(text = title, style = EchoTheme.typography.heading, color = EchoTheme.colors.textPrimary)
        Text(
            text = message,
            style = EchoTheme.typography.bodySm,
            color = EchoTheme.colors.textSecondary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3)) {
            EchoButton(
                text = confirmLabel,
                onClick = onConfirm,
                variant = if (destructive) ButtonVariant.Danger else ButtonVariant.Primary,
                fullWidth = true,
            )
            EchoButton(
                text = cancelLabel,
                onClick = onDismiss,
                variant = ButtonVariant.Ghost,
                fullWidth = true,
            )
        }
    }
}

@Composable
fun <T> PickerSheet(
    visible: Boolean,
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Sheet(visible = visible, onDismiss = onDismiss, modifier = modifier) {
        Text(text = title, style = EchoTheme.typography.heading, color = EchoTheme.colors.textPrimary)
        Column {
            options.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onPick(value) }
                        .padding(vertical = EchoTheme.spacing.s5),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = label, style = EchoTheme.typography.body, color = EchoTheme.colors.textPrimary)
                    if (value == selected) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = EchoTheme.colors.textAccent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IntSliderSheet(
    visible: Boolean,
    title: String,
    value: Int,
    range: IntRange,
    valueLabel: (Int) -> String,
    onValueChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Sheet(visible = visible, onDismiss = onDismiss, modifier = modifier) {
        Text(text = title, style = EchoTheme.typography.heading, color = EchoTheme.colors.textPrimary)
        Text(
            text = valueLabel(value),
            style = EchoTheme.typography.monoTimer,
            color = EchoTheme.colors.textAccent,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
            colors = SliderDefaults.colors(
                thumbColor = EchoTheme.colors.textAccent,
                activeTrackColor = EchoTheme.colors.textAccent,
            ),
        )
        EchoButton(text = "Done", onClick = onDismiss, fullWidth = true)
    }
}

@Composable
private fun Sheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(EchoTheme.colors.surfaceScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(EchoTheme.radii.sheet)
                        .background(EchoTheme.colors.surfaceSheet)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {}
                        .padding(EchoTheme.spacing.gutterScreen)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s6),
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(EchoTheme.radii.pill)
                            .background(EchoTheme.colors.borderStrong),
                    )
                    content()
                }
            }
        }
    }
}
