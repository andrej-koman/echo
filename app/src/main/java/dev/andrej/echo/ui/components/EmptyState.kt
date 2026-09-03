package dev.andrej.echo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.andrej.echo.ui.theme.EchoTheme

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    variant: MascotVariant = MascotVariant.Plain,
    actions: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = EchoTheme.spacing.s9),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Mascot(size = 76.dp, mood = MascotMood.Breathing, variant = variant)
        Text(
            text = title,
            style = EchoTheme.typography.heading,
            color = EchoTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = EchoTheme.typography.bodySm,
            color = EchoTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
        )
        actions?.invoke()
    }
}
