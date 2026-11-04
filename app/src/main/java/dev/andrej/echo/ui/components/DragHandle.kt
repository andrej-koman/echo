package dev.andrej.echo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.andrej.echo.ui.theme.EchoTheme

/** A generous drag target around the visual grip. Taps intentionally do nothing. */
@Composable
internal fun DragHandle(
    description: String,
    onDrag: (Float) -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val drag = rememberUpdatedState(onDrag)
    val release = rememberUpdatedState(onRelease)
    val cancel = rememberUpdatedState(onCancel)
    Box(
        modifier.fillMaxWidth().height(32.dp)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { release.value() },
                    onDragCancel = { cancel.value() },
                ) { change, amount ->
                    change.consume()
                    drag.value(amount)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(36.dp, 4.dp).clip(EchoTheme.radii.pill).background(EchoTheme.colors.borderStrong))
    }
}
