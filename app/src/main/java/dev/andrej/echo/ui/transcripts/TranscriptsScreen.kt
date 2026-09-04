package dev.andrej.echo.ui.transcripts

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import dev.andrej.echo.ui.components.EchoBadge
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.EchoSearchField
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.components.EmptyState
import dev.andrej.echo.ui.components.ThinkingDots
import dev.andrej.echo.ui.components.Mascot
import dev.andrej.echo.ui.components.MascotVariant
import dev.andrej.echo.ui.theme.EchoTheme

@Composable
fun TranscriptsScreen(
    viewModel: TranscriptsViewModel,
    onOpen: (String) -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        EchoTopBar(
            title = "Transcripts",
            subtitle = if (!state.loaded) {
                ""
            } else if (state.total == 0) {
                "Nothing recorded yet"
            } else {
                "${state.total} recordings · ${state.totalDuration} of speech"
            },
            leading = { Mascot(size = 30.dp, variant = MascotVariant.Transcripts) },
            trailing = {
                EchoIconButton(
                    iconRes = R.drawable.ic_user,
                    contentDescription = "Account",
                    onClick = onOpenAccount,
                )
            },
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
                title = "No transcripts yet",
                body = "Tap the button below and say something. Everything you record lands here.",
                variant = MascotVariant.Transcripts,
                modifier = Modifier.padding(bottom = 150.dp),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag(TAG_TRANSCRIPTS_LIST),
            contentPadding = PaddingValues(
                start = EchoTheme.spacing.gutterScreen,
                end = EchoTheme.spacing.gutterScreen,
                top = 14.dp,
                bottom = 150.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
        ) {
            item {
                EchoSearchField(
                    value = state.query,
                    onValueChange = viewModel::search,
                    placeholder = "Search all transcripts…",
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }

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
                item(key = "group-${group.label}") {
                    Text(
                        text = group.label.uppercase(),
                        style = EchoTheme.typography.microCaps,
                        color = EchoTheme.colors.textTertiary,
                        modifier = Modifier.padding(start = 2.dp, top = EchoTheme.spacing.s4),
                    )
                }
                items(group.items, key = { it.id }) { row ->
                    TranscriptRow(
                        row = row,
                        onOpen = { onOpen(row.id) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun TranscriptRow(
    row: TranscriptRow,
    onOpen: () -> Unit,
) {
    EchoCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(EchoTheme.radii.pill)
                    .background(EchoTheme.colors.surfaceAccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_audio_waveform),
                    contentDescription = null,
                    tint = EchoTheme.colors.textAccent,
                    modifier = Modifier.size(17.dp),
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.title,
                        style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.Medium),
                        color = EchoTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = row.duration,
                        style = EchoTheme.typography.monoMicro.copy(fontWeight = FontWeight.Bold),
                        color = EchoTheme.colors.textTertiary,
                    )
                }

                Text(
                    text = row.excerpt,
                    style = EchoTheme.typography.micro,
                    color = EchoTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = row.time,
                    style = EchoTheme.typography.micro,
                    color = EchoTheme.colors.textTertiary,
                )

                row.pending?.let {
                    PendingBadge(it, modifier = Modifier.padding(top = 2.dp))
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

const val TAG_TRANSCRIPTS_LIST = "transcripts_list"
