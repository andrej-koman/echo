package dev.andrej.echo.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.andrej.echo.R
import dev.andrej.echo.data.StorageUsage
import dev.andrej.echo.ui.components.CardPadding
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.theme.EchoTheme

@Composable
fun StorageScreen(
    usage: StorageUsage,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        EchoTopBar(
            title = "On this device",
            leading = {
                EchoIconButton(
                    iconRes = R.drawable.ic_chevron_left,
                    contentDescription = "Back",
                    onClick = onBack,
                )
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(EchoTheme.spacing.gutterScreen)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.gapSection),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3)) {
                Text(
                    text = "ECHO DATA",
                    style = EchoTheme.typography.microCaps,
                    color = EchoTheme.colors.textTertiary,
                )
                Text(
                    text = formatBytes(usage.totalBytes),
                    style = EchoTheme.typography.heading,
                    color = EchoTheme.colors.textPrimary,
                )
            }

            EchoCard(modifier = Modifier.fillMaxWidth(), padding = CardPadding.None) {
                Column(modifier = Modifier.padding(horizontal = EchoTheme.spacing.s5)) {
                    usage.categories.forEachIndexed { index, category ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = EchoTheme.spacing.s5),
                            horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = category.label,
                                style = EchoTheme.typography.body,
                                color = EchoTheme.colors.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = formatBytes(category.bytes),
                                style = EchoTheme.typography.bodySm,
                                color = EchoTheme.colors.textSecondary,
                            )
                        }
                        if (index != usage.categories.lastIndex) {
                            HorizontalDivider(color = EchoTheme.colors.borderSubtle)
                        }
                    }
                }
            }

            Text(
                text = "Includes recordings, downloaded models, notes, settings, and temporary files saved by Echo.",
                style = EchoTheme.typography.bodySm,
                color = EchoTheme.colors.textTertiary,
            )
        }
    }
}
