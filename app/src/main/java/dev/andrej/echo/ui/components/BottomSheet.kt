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
import androidx.compose.material3.HorizontalDivider
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
import androidx.annotation.DrawableRes
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    dividerBefore: (T) -> Boolean = { false },
    leading: (@Composable (T) -> Unit)? = null,
) {
    Sheet(visible = visible, onDismiss = onDismiss, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4)) {
            SheetHeader(title = title, subtitle = null)
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEach { (label, value) ->
                    if (dividerBefore(value)) ActionDivider()
                    MenuRow(
                        onClick = { onPick(value) },
                        leading = leading?.let { { it(value) } },
                        label = label,
                        description = subtitle(value),
                        labelColor = EchoTheme.colors.textPrimary,
                        selected = value == selected,
                    ) {
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
}

/** A list of actions under a header naming what they act on, e.g. a note's ··· menu. */
@Composable
fun ActionSheet(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Sheet(visible = visible, onDismiss = onDismiss, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4)) {
            SheetHeader(title = title, subtitle = subtitle)
            Column(content = content)
        }
    }
}

@Composable
fun ActionRow(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    trailing: String? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = EchoTheme.colors
    val tint = if (destructive) colors.textDanger else colors.textPrimary
    MenuRow(
        onClick = onClick,
        modifier = modifier.alpha(if (enabled) 1f else 0.45f),
        enabled = enabled,
        leading = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (destructive) colors.textDanger else colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        },
        label = label,
        description = description,
        labelColor = tint,
    ) {
        trailing?.let {
            Text(
                text = it,
                style = EchoTheme.typography.bodySm,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
        }
    }
}

@Composable
fun ActionDivider() {
    HorizontalDivider(
        color = EchoTheme.colors.borderSubtle,
        modifier = Modifier.padding(horizontal = EchoTheme.spacing.s4, vertical = EchoTheme.spacing.s3),
    )
}

@Composable
private fun SheetHeader(title: String, subtitle: String?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = EchoTheme.spacing.s4),
        verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s1),
    ) {
        Text(
            text = title,
            style = EchoTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = EchoTheme.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        subtitle?.let {
            Text(text = it, style = EchoTheme.typography.micro, color = EchoTheme.colors.textTertiary)
        }
        HorizontalDivider(
            color = EchoTheme.colors.borderSubtle,
            modifier = Modifier.padding(top = EchoTheme.spacing.s5),
        )
    }
}

/** Rows highlight while pressed instead of rippling, matching the sheet's quiet surface. */
@Composable
private fun MenuRow(
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)?,
    label: String,
    description: String?,
    labelColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null,
    trailing: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(EchoTheme.radii.md)
            .background(if (pressed) EchoTheme.colors.surfaceSunken else Color.Transparent)
            .then(
                if (selected == null) {
                    Modifier.clickable(interaction, null, enabled = enabled, role = Role.Button, onClick = onClick)
                } else {
                    Modifier.selectable(selected, interaction, null, enabled, Role.RadioButton, onClick)
                },
            )
            .padding(horizontal = EchoTheme.spacing.s4, vertical = EchoTheme.spacing.s5),
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s1)) {
            Text(text = label, style = EchoTheme.typography.body, color = labelColor)
            description?.let {
                Text(text = it, style = EchoTheme.typography.micro, color = EchoTheme.colors.textTertiary)
            }
        }
        trailing()
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
