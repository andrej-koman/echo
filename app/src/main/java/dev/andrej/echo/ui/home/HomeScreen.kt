package dev.andrej.echo.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.R
import dev.andrej.echo.ui.components.ButtonSize
import dev.andrej.echo.ui.components.ButtonVariant
import dev.andrej.echo.ui.components.CardPadding
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoSearchField
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.components.EmptyState
import dev.andrej.echo.ui.components.Mascot
import dev.andrej.echo.ui.components.SectionHeader
import dev.andrej.echo.ui.components.ThinkingDots
import dev.andrej.echo.ui.components.Waveform
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.transcripts.PendingBadge
import dev.andrej.echo.ui.transcripts.TranscriptRow

private const val RECENT_COUNT = 3

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSeeTasks: () -> Unit,
    onSeeAll: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.notes.collectAsStateWithLifecycle()
    val upNext by viewModel.upNext.collectAsStateWithLifecycle()
    val recent = state.groups.flatMap { it.items }.take(RECENT_COUNT)

    Column(modifier = modifier.fillMaxSize()) {
        EchoTopBar(
            leading = { Mascot(size = 30.dp) },
        ) {
            EchoSearchField(
                value = state.query,
                onValueChange = viewModel::search,
                placeholder = "Search for notes…",
                modifier = Modifier.weight(1f),
            )
        }

        if (!state.loaded) {
            Box(
                modifier = Modifier.weight(1f).padding(bottom = 120.dp),
                contentAlignment = Alignment.Center,
            ) {
                ThinkingDots()
            }
        } else if (state.total == 0) {
            EmptyState(
                title = "Nothing recorded yet",
                body = "Hold the record button and talk. OutLoud writes it down and sorts the " +
                    "rest into notes, todos and reminders.",
                modifier = Modifier.weight(1f).padding(bottom = 120.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = EchoTheme.spacing.s4),
                ) {
                    EchoButton(
                        text = "Upload audio",
                        onClick = {},
                        variant = ButtonVariant.Secondary,
                        size = ButtonSize.Sm,
                    )
                    EchoButton(
                        text = "Type a note",
                        onClick = {},
                        variant = ButtonVariant.Ghost,
                        size = ButtonSize.Sm,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = EchoTheme.spacing.gutterScreen,
                    end = EchoTheme.spacing.gutterScreen,
                    top = EchoTheme.spacing.s6,
                    bottom = 150.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
            ) {
                if (upNext.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Up next",
                            hint = "See all",
                            modifier = Modifier.clickable(onClick = onSeeTasks),
                        )
                    }

                    item { UpNextCard(items = upNext, onOpen = onOpen) }
                }

                item {
                    SectionHeader(
                        title = "Recent notes",
                        hint = "See all",
                        modifier = Modifier
                            .padding(top = EchoTheme.spacing.s5)
                            .clickable(onClick = onSeeAll),
                    )
                }

                items(recent, key = { it.id }) { row ->
                    RecentNote(row = row, onOpen = { onOpen(row.id) })
                }
            }
        }
    }
}

@Composable
private fun UpNextCard(items: List<UpNextItem>, onOpen: (String) -> Unit) {
    EchoCard(modifier = Modifier.fillMaxWidth(), padding = CardPadding.Md) {
        Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s6)) {
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(item.sourceTranscriptId) },
                    horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.isEvent) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(EchoTheme.radii.sm)
                                .background(EchoTheme.colors.surfaceAccentSoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_calendar),
                                contentDescription = null,
                                tint = EchoTheme.colors.textAccent,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .border(1.5.dp, EchoTheme.colors.borderStrong, CircleShape),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s1)) {
                        Text(
                            text = item.title,
                            style = EchoTheme.typography.heading,
                            color = EchoTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.detail,
                            style = EchoTheme.typography.caption,
                            color = EchoTheme.colors.textTertiary,
                        )
                    }
                }
            }
        }
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
        row.pending?.let {
            PendingBadge(it, modifier = Modifier.padding(top = EchoTheme.spacing.s5))
        }
    }
}
