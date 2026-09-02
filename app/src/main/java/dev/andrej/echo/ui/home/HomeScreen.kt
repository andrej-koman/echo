package dev.andrej.echo.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.R
import dev.andrej.echo.ui.components.ButtonSize
import dev.andrej.echo.ui.components.CardPadding
import dev.andrej.echo.ui.components.CardTone
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.EchoSearchField
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.components.Mascot
import dev.andrej.echo.ui.components.SectionHeader
import dev.andrej.echo.ui.components.Waveform
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.transcripts.TranscriptRow
import dev.andrej.echo.ui.transcripts.TranscriptsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val RECENT_COUNT = 3

@Composable
fun HomeScreen(
    viewModel: TranscriptsViewModel,
    userName: String?,
    onStartRecording: () -> Unit,
    onOpenAccount: () -> Unit,
    onSeeAll: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today = state.groups.firstOrNull { it.label == "Today" }?.items.orEmpty()
    val recent = state.groups.flatMap { it.items }.take(RECENT_COUNT)
    val date = remember { dateFormat.format(Date()) }

    Column(modifier = modifier.fillMaxSize()) {
        EchoTopBar(
            leading = { Mascot(size = 30.dp) },
            trailing = {
                EchoIconButton(
                    iconRes = R.drawable.ic_user,
                    contentDescription = "Account",
                    onClick = onOpenAccount,
                )
            },
        ) {
            EchoSearchField(
                value = state.query,
                onValueChange = viewModel::search,
                placeholder = "Search for notes…",
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = EchoTheme.spacing.gutterScreen,
                end = EchoTheme.spacing.gutterScreen,
                top = 14.dp,
                bottom = 150.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3)) {
                    Text(
                        text = "Hi ${userName ?: "there"}, how may I help you?",
                        style = EchoTheme.typography.display,
                        color = EchoTheme.colors.textPrimary,
                    )
                    Text(
                        text = "$date · ${today.size} ${if (today.size == 1) "note" else "notes"} today",
                        style = EchoTheme.typography.caption,
                        color = EchoTheme.colors.textTertiary,
                    )
                }
            }

            item {
                Actions(
                    onStartRecording = onStartRecording,
                    modifier = Modifier.padding(top = EchoTheme.spacing.s4),
                )
            }

            item {
                SectionHeader(
                    title = "Recent Notes",
                    hint = "See all",
                    modifier = Modifier
                        .padding(top = EchoTheme.spacing.s5)
                        .clickable(onClick = onSeeAll),
                )
            }

            if (recent.isEmpty()) {
                item {
                    Text(
                        text = "Nothing recorded yet. Tap the button below and say something.",
                        style = EchoTheme.typography.bodySm,
                        color = EchoTheme.colors.textTertiary,
                    )
                }
            }

            items(recent, key = { it.id }) { row ->
                RecentNote(row = row, onOpen = { onOpen(row.id) })
            }
        }
    }
}

@Composable
private fun Actions(
    onStartRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
    ) {
        EchoCard(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            tone = CardTone.Bronze,
            padding = CardPadding.Md,
        ) {
            Text(
                text = "Start audio recording",
                style = EchoTheme.typography.heading,
                color = EchoTheme.colors.textPrimary,
            )
            Spacer(modifier = Modifier.weight(1f).height(EchoTheme.spacing.s6))
            EchoButton(
                text = "Start recording",
                onClick = onStartRecording,
                size = ButtonSize.Sm,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
        ) {
            SecondaryAction(iconRes = R.drawable.ic_upload, label = "Upload audio")
            SecondaryAction(iconRes = R.drawable.ic_pen_line, label = "Type a note")
        }
    }
}

@Composable
private fun SecondaryAction(iconRes: Int, label: String) {
    EchoCard(modifier = Modifier.fillMaxWidth(), padding = CardPadding.Md) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = EchoTheme.colors.textPrimary,
                modifier = Modifier.size(20.dp),
            )
            Icon(
                painter = painterResource(R.drawable.ic_arrow_right),
                contentDescription = null,
                tint = EchoTheme.colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.Medium),
            color = EchoTheme.colors.textPrimary,
            modifier = Modifier.padding(top = EchoTheme.spacing.s5),
        )
    }
}

@Composable
private fun RecentNote(row: TranscriptRow, onOpen: () -> Unit) {
    EchoCard(modifier = Modifier.fillMaxWidth(), padding = CardPadding.Lg, onClick = onOpen) {
        Text(
            text = row.title,
            style = EchoTheme.typography.heading,
            color = EchoTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = row.time,
            style = EchoTheme.typography.micro,
            color = EchoTheme.colors.textTertiary,
            modifier = Modifier.padding(top = EchoTheme.spacing.s2),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = EchoTheme.spacing.s5),
            horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Waveform(modifier = Modifier.weight(1f), height = 26.dp, bars = 34)
            Text(
                text = row.duration,
                style = EchoTheme.typography.monoMicro,
                color = EchoTheme.colors.textTertiary,
            )
        }
        Text(
            text = row.excerpt,
            style = EchoTheme.typography.bodySm,
            color = EchoTheme.colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = EchoTheme.spacing.s5),
        )
    }
}

private val dateFormat = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())
