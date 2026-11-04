package dev.andrej.echo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.ui.theme.EchoTheme

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
    subtitle: (T) -> String? = { null },
) {
    Sheet(visible = visible, onDismiss = onDismiss, modifier = modifier) {
        Text(text = title, style = EchoTheme.typography.heading, color = EchoTheme.colors.textPrimary)
        Column(
            Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
        ) {
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = label, style = EchoTheme.typography.body, color = EchoTheme.colors.textPrimary)
                        subtitle(value)?.let {
                            Text(text = it, style = EchoTheme.typography.bodySm, color = EchoTheme.colors.textTertiary)
                        }
                    }
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

/**
 * How many sheets are open. Sheets draw inside their screen and the tab bar sits above every
 * screen, so the bar hides while this is above zero rather than covering the sheet's buttons.
 */
val LocalOpenSheets = staticCompositionLocalOf { mutableIntStateOf(0) }

/** Height of the tab bar under a screen that lays out above it rather than behind it. */
val LocalBottomBarHeight = compositionLocalOf { 0.dp }

@Composable
internal fun Sheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val transition = remember { MutableTransitionState(false) }
    transition.targetState = visible
    val openSheets = LocalOpenSheets.current
    if (transition.currentState || transition.targetState || !transition.isIdle) {
        DisposableEffect(Unit) {
            openSheets.intValue++
            onDispose { openSheets.intValue-- }
        }
        var dragOffset by remember { mutableFloatStateOf(0f) }
        var dragging by remember { androidx.compose.runtime.mutableStateOf(false) }
        val threshold = with(LocalDensity.current) { 64.dp.toPx() }
        val offset by animateFloatAsState(
            targetValue = dragOffset,
            animationSpec = if (dragging) snap() else spring(),
            label = "sheetDrag",
        )
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            AnimatedVisibility(
                visibleState = transition,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = modifier.fillMaxSize(),
            ) {
                Box(Modifier.fillMaxSize()) {
                    // A sibling rather than a clickable ancestor: only outside taps dismiss.
                    Box(
                        Modifier.fillMaxSize().background(EchoTheme.colors.surfaceScrim)
                            .semantics { contentDescription = "Dismiss sheet" }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss,
                            ),
                    )
                    Column(
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .animateEnterExit(
                                enter = slideInVertically(initialOffsetY = { it }),
                                exit = slideOutVertically(targetOffsetY = { it }),
                            )
                            .imePadding()
                            .offset { IntOffset(0, offset.roundToInt()) }
                            .fillMaxWidth()
                            .clip(EchoTheme.radii.sheet)
                            .background(EchoTheme.colors.surfaceSheet)
                            .pointerInput(Unit) { detectTapGestures { /* Keep sheet taps off the scrim. */ } }
                            .padding(horizontal = EchoTheme.spacing.gutterScreen)
                            .navigationBarsPadding()
                            .padding(bottom = EchoTheme.spacing.gutterScreen),
                    ) {
                        DragHandle(
                            description = "Sheet drag handle",
                            onDrag = { dragging = true; dragOffset = (dragOffset + it).coerceAtLeast(0f) },
                            onRelease = {
                                dragging = false
                                if (dragOffset >= threshold) onDismiss()
                                else dragOffset = 0f
                            },
                            onCancel = { dragging = false; dragOffset = 0f },
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s6)) { content() }
                    }
                }
            }
        }
    }
}
