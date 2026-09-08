package dev.andrej.echo.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.R
import dev.andrej.echo.ui.components.BadgeTone
import dev.andrej.echo.ui.components.CardPadding
import dev.andrej.echo.ui.components.EchoBadge
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.components.EmptyState
import dev.andrej.echo.ui.components.Mascot
import dev.andrej.echo.ui.components.MascotVariant
import dev.andrej.echo.ui.components.ThinkingDots
import dev.andrej.echo.ui.theme.EchoTheme

@Composable
fun NotesScreen(
    viewModel: NotesViewModel,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        EchoTopBar(
            title = "Notes",
            query = state.query,
            onQueryChange = viewModel::search,
            searchPlaceholder = "Search all notes…",
            subtitle = if (!state.loaded) {
                ""
            } else if (state.total == 0) {
                "Nothing recorded yet"
            } else {
                "${state.total} recordings · ${state.totalDuration} of speech"
            },
            leading = { Mascot(size = 30.dp, variant = MascotVariant.Notes) },
        )

        if (!state.loaded) {
            Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 150.dp),
                contentAlignment = Alignment.Center,
            ) {
                ThinkingDots()
            }
            return@Column
        }

        if (state.total == 0) {
            EmptyState(
                title = "No notes yet",
                body = "Tap the button below and say something. Echo will clean it up and pull out your tasks.",
                variant = MascotVariant.Notes,
                modifier = Modifier.padding(bottom = 150.dp),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag(TAG_NOTES_LIST),
            contentPadding = PaddingValues(
                start = EchoTheme.spacing.gutterScreen,
                end = EchoTheme.spacing.gutterScreen,
                top = 14.dp,
                bottom = 150.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
        ) {
            if (state.groups.isEmpty()) {
                item {
                    Text(
                        text = "Nothing matches “${state.query.trim()}”.",
                        style = EchoTheme.typography.bodySm,
                        color = EchoTheme.colors.textTertiary,
                        modifier = Modifier.padding(top = EchoTheme.spacing.s7),
                    )
                }
            }

            state.groups.forEach { group ->
                item(key = "header-${group.label}") {
                    NotesGroupHeader(
                        label = group.label,
                        count = group.items.size,
                        modifier = Modifier.padding(start = 2.dp, top = EchoTheme.spacing.s4),
                    )
                }
                item(key = "group-${group.label}") {
                    NotesGroupCard(items = group.items, onOpen = onOpen)
                }
            }
        }
    }
}

/** One shared card per day, rows separated by a hairline — same rhythm as Tasks' grouped list. */
@Composable
private fun NotesGroupCard(items: List<NoteRow>, onOpen: (String) -> Unit) {
    EchoCard(modifier = Modifier.fillMaxWidth(), padding = CardPadding.None) {
        Column(modifier = Modifier.padding(vertical = EchoTheme.spacing.s2, horizontal = EchoTheme.spacing.s4)) {
            items.forEachIndexed { index, row ->
                NoteRow(row = row, onOpen = { onOpen(row.id) })
                if (index != items.lastIndex) {
                    HorizontalDivider(color = EchoTheme.colors.borderSubtle)
                }
            }
        }
    }
}

@Composable
private fun NotesGroupHeader(label: String, count: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
            text = label,
            style = EchoTheme.typography.titleSm,
            color = EchoTheme.colors.textPrimary,
        )
        Box(modifier = Modifier.weight(1f))
        Text(
            text = count.toString(),
            style = EchoTheme.typography.monoCaption.copy(fontWeight = FontWeight.Bold),
            color = EchoTheme.colors.textTertiary,
        )
    }
}

@Composable
internal fun NoteRow(
    row: NoteRow,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = EchoTheme.spacing.s4, horizontal = EchoTheme.spacing.s2),
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
    ) {
        val (iconRes, iconBg, iconFg) = when (row.pending?.severity) {
            PendingSeverity.Failed -> Triple(R.drawable.ic_x, EchoTheme.colors.statusWarningBg, EchoTheme.colors.statusWarningFg)
            PendingSeverity.Parked -> Triple(R.drawable.ic_clock, EchoTheme.colors.statusNeutralBg, EchoTheme.colors.statusNeutralFg)
            null -> Triple(R.drawable.ic_audio_waveform, EchoTheme.colors.surfaceAccentSoft, EchoTheme.colors.textAccent)
        }

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(EchoTheme.radii.pill)
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconFg,
                modifier = Modifier.size(15.dp),
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = row.title,
                style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.Medium),
                color = EchoTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = row.excerpt,
                style = EchoTheme.typography.micro,
                color = EchoTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${row.time} · ${row.duration}",
                    style = EchoTheme.typography.monoMicro,
                    color = EchoTheme.colors.textTertiary,
                )
                Box(modifier = Modifier.weight(1f))
                when {
                    row.pending != null -> Text(
                        text = row.pending.text,
                        style = EchoTheme.typography.micro,
                        color = iconFg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    row.taskCount > 0 -> Text(
                        text = if (row.taskCount == 1) "1 task" else "${row.taskCount} tasks",
                        style = EchoTheme.typography.monoMicro.copy(fontWeight = FontWeight.Bold),
                        color = EchoTheme.colors.textTertiary,
                    )
                }
            }
        }
    }
}

@Composable
internal fun PendingBadge(pending: PendingCopy, modifier: Modifier = Modifier) {
    EchoBadge(
        text = pending.text,
        tone = if (pending.severity == PendingSeverity.Failed) BadgeTone.Warning else BadgeTone.Neutral,
        modifier = modifier,
    )
}

const val TAG_NOTES_LIST = "notes_list"
