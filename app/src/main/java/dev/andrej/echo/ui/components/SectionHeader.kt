package dev.andrej.echo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.andrej.echo.ui.theme.EchoTheme

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = title,
            style = EchoTheme.typography.heading,
            color = EchoTheme.colors.textPrimary,
        )
        if (hint != null) {
            Text(
                text = hint,
                style = EchoTheme.typography.monoMicro,
                color = EchoTheme.colors.textTertiary,
            )
        }
    }
}
