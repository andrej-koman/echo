package dev.andrej.echo.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.theme.EchoTheme

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        EchoTopBar(
            title = "Profile",
            trailing = {
                EchoIconButton(
                    iconRes = R.drawable.ic_settings,
                    contentDescription = "Settings",
                    onClick = onOpenSettings,
                )
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(EchoTheme.spacing.gutterScreen),
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s6),
        ) {
            StatsGrid(state)
            WeekCard(state, onClick = onOpenHistory)
        }
    }
}

@Composable
private fun StatsGrid(state: ProfileUiState) {
    EchoCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Stat("NOTES", state.notesCount.toString(), Modifier.weight(1f))
            Stat("TASKS DONE", state.tasksDoneCount.toString(), Modifier.weight(1f))
            Stat("CAPTURED", state.capturedTotal, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = value,
            style = EchoTheme.typography.title,
            color = EchoTheme.colors.textPrimary,
        )
        Text(
            text = label,
            style = EchoTheme.typography.microCaps,
            color = EchoTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun WeekCard(state: ProfileUiState, onClick: () -> Unit) {
    EchoCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = "This week",
                style = EchoTheme.typography.heading,
                color = EchoTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "HISTORY",
                style = EchoTheme.typography.caption,
                color = EchoTheme.colors.textAccent,
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = EchoTheme.colors.textTertiary,
                modifier = Modifier.size(15.dp).padding(start = EchoTheme.spacing.s2),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = EchoTheme.spacing.s5)
                .height(62.dp),
            horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
        ) {
            state.week.forEach { day ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height((48 * day.fraction).dp.coerceAtLeast(4.dp))
                        .clip(EchoTheme.radii.xs)
                        .background(
                            if (day.active) EchoTheme.colors.textAccent else EchoTheme.colors.borderDefault,
                        ),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = EchoTheme.spacing.s2),
            horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
        ) {
            state.week.forEach { day ->
                Text(
                    text = day.label,
                    style = EchoTheme.typography.microCaps,
                    color = EchoTheme.colors.textTertiary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
