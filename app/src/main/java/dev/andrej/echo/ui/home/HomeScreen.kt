package dev.andrej.echo.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.R
import dev.andrej.echo.data.TodoItem
import dev.andrej.echo.ui.components.BadgeTone
import dev.andrej.echo.ui.components.ButtonSize
import dev.andrej.echo.ui.components.ButtonVariant
import dev.andrej.echo.ui.components.CardPadding
import dev.andrej.echo.ui.components.EchoBadge
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.components.EmptyState
import dev.andrej.echo.ui.components.Mascot
import dev.andrej.echo.ui.components.MascotMood
import dev.andrej.echo.ui.components.ThinkingDots
import dev.andrej.echo.ui.tasks.TaskCheckbox
import dev.andrej.echo.ui.tasks.TaskRow
import dev.andrej.echo.ui.theme.EchoTheme

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    userName: String?,
    onSeeTasks: () -> Unit,
    onSeeAll: () -> Unit,
    onOpen: (String) -> Unit,
    onEditTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        EchoTopBar(
            leading = { Mascot(size = 30.dp) },
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = if (userName != null) "${state.greeting}, $userName" else state.greeting,
                    style = EchoTheme.typography.titleSm,
                    color = EchoTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.dateLabel,
                    style = EchoTheme.typography.microCaps,
                    color = EchoTheme.colors.textTertiary,
                )
            }
        }

        if (!state.loaded) {
            Box(
                modifier = Modifier.weight(1f).padding(bottom = 120.dp),
                contentAlignment = Alignment.Center,
            ) {
                ThinkingDots()
            }
        } else if (!state.hasAnyTranscripts) {
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
                    top = EchoTheme.spacing.s5,
                    bottom = 150.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
            ) {
                if (state.analyzingCount > 0) {
                    item {
                        AnalyzingBanner(count = state.analyzingCount, onClick = onSeeAll)
                    }
                }

                item {
                    when (val hero = state.hero) {
                        is HeroState.NextUp -> {
                            Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s2)) {
                                Text(
                                    text = "NEXT UP",
                                    style = EchoTheme.typography.microCaps,
                                    color = EchoTheme.colors.textTertiary,
                                )
                                HeroCard(
                                    hero = hero,
                                    onToggle = { viewModel.setDone(hero.item.id, !hero.item.done) },
                                    onOpen = { onEditTask(hero.item.id) },
                                    onSnooze = { viewModel.snooze(hero.item) },
                                    onAddTime = { onEditTask(hero.item.id) },
                                )
                            }
                        }

                        HeroState.Clear -> ClearHeroCard(body = state.heroBody)
                    }
                }

                if (state.thenRows.isNotEmpty()) {
                    item {
                        ThenHeader(
                            label = state.thenLabel,
                            dateLabel = state.thenDateLabel,
                            count = state.thenTotalCount,
                            seeAll = state.thenDateLabel != null,
                            onSeeAll = onSeeTasks,
                        )
                    }
                    item {
                        ThenCard(
                            rows = state.thenRows,
                            showDate = state.thenShowDate,
                            moreUntimed = state.thenMoreUntimed,
                            onToggle = { item -> viewModel.setDone(item.id, !item.done) },
                            onEdit = { item -> onEditTask(item.id) },
                            onMoreClick = onSeeTasks,
                        )
                    }
                }

                item {
                    ThenHeader(
                        label = "Latest note",
                        dateLabel = null,
                        count = 0,
                        seeAll = true,
                        seeAllText = "All notes",
                        onSeeAll = onSeeAll,
                    )
                }

                state.latestNote?.let { note ->
                    item(key = note.id) {
                        LatestNoteCard(note = note, onOpen = { onOpen(note.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyzingBanner(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(24.dp)
            .clip(EchoTheme.radii.pill)
            .background(EchoTheme.colors.statusWarningBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_clock),
            contentDescription = null,
            tint = EchoTheme.colors.statusWarningFg,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = "$count ${if (count == 1) "note" else "notes"} still need analyzing",
            style = EchoTheme.typography.micro,
            color = EchoTheme.colors.statusWarningFg,
        )
    }
}

@Composable
private fun HeroCard(
    hero: HeroState.NextUp,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onSnooze: () -> Unit,
    onAddTime: () -> Unit,
) {
    val colors = EchoTheme.colors
    EchoCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen), padding = CardPadding.Lg) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TaskCheckbox(done = hero.item.done, overdue = hero.overdue, onToggle = onToggle, size = 28.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hero.item.text,
                    style = EchoTheme.typography.titleSm,
                    color = colors.textPrimary,
                )
                Row(
                    modifier = Modifier.padding(top = EchoTheme.spacing.s2),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val whenIcon = if (hero.item.hasTime) R.drawable.ic_bell else R.drawable.ic_calendar
                    val whenColor = when {
                        hero.overdue -> colors.textDanger
                        hero.item.hasTime -> colors.textAccent
                        else -> colors.textTertiary
                    }
                    Icon(
                        painter = painterResource(whenIcon),
                        contentDescription = null,
                        tint = whenColor,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = hero.whenLabel,
                        style = EchoTheme.typography.monoCaption.copy(fontWeight = FontWeight.Bold),
                        color = whenColor,
                    )
                }
            }
        }

        if (hero.overdue || !hero.item.hasTime) {
            HorizontalDivider(
                color = colors.borderSubtle,
                modifier = Modifier.padding(top = EchoTheme.spacing.s5, bottom = EchoTheme.spacing.s4),
            )
            if (hero.overdue) {
                HeroActionPill(text = "Snooze", iconRes = R.drawable.ic_clock, primary = false, onClick = onSnooze)
            } else {
                HeroActionPill(text = "Add a time", iconRes = R.drawable.ic_bell, primary = true, onClick = onAddTime)
            }
        }
    }
}

@Composable
private fun HeroActionPill(text: String, iconRes: Int, primary: Boolean, onClick: () -> Unit) {
    val colors = EchoTheme.colors
    val background = if (primary) colors.actionPrimaryBg else colors.surfaceAccentSoft
    val foreground = if (primary) colors.actionPrimaryFg else colors.textAccent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(EchoTheme.radii.pill)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = text,
            style = EchoTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
            color = foreground,
        )
    }
}

@Composable
private fun ClearHeroCard(body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = EchoTheme.spacing.s7),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3, Alignment.CenterVertically),
    ) {
        Mascot(size = 76.dp, mood = MascotMood.Nodding)
        Text(
            text = "Today is clear",
            style = EchoTheme.typography.heading,
            color = EchoTheme.colors.textPrimary,
        )
        Text(
            text = body,
            style = EchoTheme.typography.bodySm,
            color = EchoTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ThenHeader(
    label: String,
    dateLabel: String?,
    count: Int,
    seeAll: Boolean,
    onSeeAll: () -> Unit,
    seeAllText: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(text = label, style = EchoTheme.typography.heading, color = EchoTheme.colors.textPrimary)
        if (dateLabel != null) {
            Text(text = dateLabel, style = EchoTheme.typography.microCaps, color = EchoTheme.colors.textTertiary)
        }
        Spacer(modifier = Modifier.weight(1f))
        if (seeAll) {
            Text(
                text = seeAllText ?: "See all $count",
                style = EchoTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                color = EchoTheme.colors.textAccent,
                modifier = Modifier.clickable(onClick = onSeeAll),
            )
        } else {
            Text(
                text = count.toString(),
                style = EchoTheme.typography.monoCaption.copy(fontWeight = FontWeight.Bold),
                color = EchoTheme.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun ThenCard(
    rows: List<HomeTaskRow>,
    showDate: Boolean,
    moreUntimed: Int,
    onToggle: (TodoItem) -> Unit,
    onEdit: (TodoItem) -> Unit,
    onMoreClick: () -> Unit,
) {
    EchoCard(modifier = Modifier.fillMaxWidth(), padding = CardPadding.None) {
        Column(modifier = Modifier.padding(vertical = EchoTheme.spacing.s2, horizontal = EchoTheme.spacing.s4)) {
            rows.forEachIndexed { index, row ->
                TaskRow(
                    task = row.item,
                    overdue = row.overdue,
                    showDate = showDate,
                    sourceTitle = row.sourceTitle,
                    onToggle = { onToggle(row.item) },
                    onEdit = { onEdit(row.item) },
                )
                if (index != rows.lastIndex || moreUntimed > 0) {
                    HorizontalDivider(color = EchoTheme.colors.borderSubtle)
                }
            }
            if (moreUntimed > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onMoreClick)
                        .padding(vertical = EchoTheme.spacing.s4, horizontal = EchoTheme.spacing.s2),
                    horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "+$moreUntimed more today with no time set",
                        style = EchoTheme.typography.micro,
                        color = EchoTheme.colors.textTertiary,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_right),
                        contentDescription = null,
                        tint = EchoTheme.colors.textTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LatestNoteCard(note: LatestNote, onOpen: () -> Unit) {
    val colors = EchoTheme.colors
    val (iconRes, iconBg, iconFg) = when {
        note.pendingFailed -> Triple(R.drawable.ic_x, colors.statusWarningBg, colors.statusWarningFg)
        note.pendingText != null -> Triple(R.drawable.ic_clock, colors.statusNeutralBg, colors.statusNeutralFg)
        else -> Triple(R.drawable.ic_audio_waveform, colors.surfaceAccentSoft, colors.textAccent)
    }

    EchoCard(modifier = Modifier.fillMaxWidth(), padding = CardPadding.Lg, onClick = onOpen) {
        Row(horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(32.dp).clip(EchoTheme.radii.pill).background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = iconFg,
                    modifier = Modifier.size(15.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = note.title,
                    style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.Medium),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val tasksLabel = if (note.taskCount > 0) "${note.taskCount} TASKS" else "NO TASKS"
                Text(
                    text = "${note.relativeTime} · ${note.duration} · $tasksLabel",
                    style = EchoTheme.typography.monoMicro,
                    color = colors.textTertiary,
                )
            }
        }
        if (note.pendingText != null) {
            EchoBadge(
                text = note.pendingText,
                tone = if (note.pendingFailed) BadgeTone.Warning else BadgeTone.Neutral,
                modifier = Modifier.padding(top = EchoTheme.spacing.s4),
            )
        } else {
            Text(
                text = note.excerpt,
                style = EchoTheme.typography.bodySm,
                color = colors.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = EchoTheme.spacing.s4),
            )
        }
    }
}
