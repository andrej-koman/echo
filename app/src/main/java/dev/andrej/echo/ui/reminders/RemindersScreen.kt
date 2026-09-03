package dev.andrej.echo.ui.reminders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.R
import dev.andrej.echo.data.Reminder
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.components.EmptyState
import dev.andrej.echo.ui.components.Mascot
import dev.andrej.echo.ui.components.MascotVariant
import dev.andrej.echo.ui.components.ThinkingDots
import dev.andrej.echo.ui.theme.EchoTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RemindersScreen(
    viewModel: RemindersViewModel,
    onOpenSource: (String) -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        EchoTopBar(
            title = "Reminders",
            leading = { Mascot(size = 30.dp) },
            trailing = {
                EchoIconButton(
                    iconRes = R.drawable.ic_user,
                    contentDescription = "Account",
                    onClick = onOpenAccount,
                )
            },
        )

        if (!state.loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ThinkingDots() }
            return@Column
        }

        if (state.groups.isEmpty()) {
            EmptyState(
                title = "Nothing to remember",
                body = "Mention a time when you record — \"remind me to call the plumber tomorrow\" " +
                    "— and it lands here.",
                modifier = Modifier.padding(bottom = 150.dp),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = EchoTheme.spacing.gutterScreen,
                end = EchoTheme.spacing.gutterScreen,
                top = 14.dp,
                bottom = 150.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
        ) {
            state.groups.forEach { group ->
                item(key = "header-${group.sourceTranscriptId}") {
                    Text(
                        text = group.sourceTitle.uppercase(),
                        style = EchoTheme.typography.microCaps,
                        color = EchoTheme.colors.textTertiary,
                        modifier = Modifier
                            .padding(start = 2.dp, top = EchoTheme.spacing.s4)
                            .clickable { onOpenSource(group.sourceTranscriptId) },
                    )
                }
                items(group.reminders, key = { it.id }) { reminder ->
                    ReminderRow(reminder = reminder, onOpen = { onOpenSource(group.sourceTranscriptId) })
                }
            }
        }
    }
}

@Composable
private fun ReminderRow(reminder: Reminder, onOpen: () -> Unit) {
    EchoCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Text(
            text = reminder.text,
            style = EchoTheme.typography.bodySm,
            color = EchoTheme.colors.textPrimary,
        )
        Text(
            text = reminder.dueAt?.let { dueFormat.format(Date(it)) } ?: "No time given",
            style = EchoTheme.typography.micro,
            color = EchoTheme.colors.textTertiary,
            modifier = Modifier.padding(top = EchoTheme.spacing.s2),
        )
    }
}

private val dueFormat = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault())
