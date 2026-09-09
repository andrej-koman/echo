package dev.andrej.echo.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.ui.components.CardPadding
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.SpaceMono
import dev.andrej.echo.ui.theme.hairline
import java.time.LocalDate

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onBack: () -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onPickDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        EchoTopBar(
            title = "History",
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
                .padding(EchoTheme.spacing.gutterScreen),
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s6),
        ) {
            EchoCard(modifier = Modifier.fillMaxWidth(), padding = CardPadding.Md) {
                MonthHeader(state.monthLabel, onPrevMonth, onNextMonth, state.canGoNext)
                WeekdayHeader(state.weekdayLabels)
                DayGrid(state.days, state.selectedDay, onPickDay)
                Legend()
                MonthStats(state)
            }

            SelectedDayPanel(state)

            Box(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun MonthHeader(monthLabel: String, onPrev: () -> Unit, onNext: () -> Unit, canGoNext: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = EchoTheme.spacing.s5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EchoIconButton(iconRes = R.drawable.ic_chevron_left, contentDescription = "Previous month", onClick = onPrev)
        Text(
            text = monthLabel,
            style = EchoTheme.typography.heading,
            color = EchoTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        EchoIconButton(
            iconRes = R.drawable.ic_chevron_right,
            contentDescription = "Next month",
            onClick = onNext,
            tint = if (canGoNext) EchoTheme.colors.textSecondary else EchoTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun WeekdayHeader(labels: List<String>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEach { label ->
            Text(
                text = label,
                style = EchoTheme.typography.microCaps,
                color = EchoTheme.colors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DayGrid(days: List<HistoryDay>, selected: LocalDate, onPick: (LocalDate) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.fillMaxWidth().padding(top = EchoTheme.spacing.s3),
        userScrollEnabled = false,
    ) {
        items(days) { day ->
            DayCell(day, isSelected = day.date == selected, onClick = { onPick(day.date) })
        }
    }
}

@Composable
private fun DayCell(day: HistoryDay, isSelected: Boolean, onClick: () -> Unit) {
    val colors = EchoTheme.colors
    val heatColor = when (day.heatLevel) {
        0 -> colors.surfaceSunken
        1 -> colors.surfaceAccentSoft
        2 -> colors.textAccent.copy(alpha = 0.55f)
        else -> colors.textAccent
    }
    val textColor = when {
        !day.inMonth -> colors.textTertiary.copy(alpha = 0.4f)
        day.heatLevel >= 3 -> colors.textInverse
        else -> colors.textPrimary
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(3.dp)
            .clip(EchoTheme.radii.sm)
            .background(if (day.inMonth) heatColor else colors.surfacePage)
            .then(if (isSelected) Modifier.hairline(colors.borderAccent, cornerRadius = 8.dp) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = day.inMonth,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            style = if (isSelected) {
                EchoTheme.typography.bodySm.copy(fontFamily = SpaceMono)
            } else {
                EchoTheme.typography.bodySm
            },
            color = textColor,
        )
    }
}

@Composable
private fun Legend() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = EchoTheme.spacing.s4),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "LESS",
            style = EchoTheme.typography.microCaps,
            color = EchoTheme.colors.textTertiary,
            modifier = Modifier.padding(end = EchoTheme.spacing.s2),
        )
        listOf(
            EchoTheme.colors.surfaceSunken,
            EchoTheme.colors.surfaceAccentSoft,
            EchoTheme.colors.textAccent.copy(alpha = 0.55f),
            EchoTheme.colors.textAccent,
        ).forEach { swatch ->
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .padding(1.dp)
                    .clip(EchoTheme.radii.xs)
                    .background(swatch),
            )
        }
        Text(
            text = "MORE",
            style = EchoTheme.typography.microCaps,
            color = EchoTheme.colors.textTertiary,
            modifier = Modifier.padding(start = EchoTheme.spacing.s2),
        )
    }
}

@Composable
private fun MonthStats(state: HistoryUiState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = EchoTheme.spacing.s5),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MonthStat("NOTES", state.notesInMonth.toString())
        MonthStat("TASKS", state.tasksInMonth.toString())
        MonthStat("DAYS USED", state.activeDaysInMonth.toString())
    }
}

@Composable
private fun MonthStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = EchoTheme.typography.heading.copy(fontFamily = SpaceMono), color = EchoTheme.colors.textPrimary)
        Text(text = label, style = EchoTheme.typography.microCaps, color = EchoTheme.colors.textTertiary)
    }
}

@Composable
private fun SelectedDayPanel(state: HistoryUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3)) {
        Text(
            text = state.selectedDayLabel,
            style = EchoTheme.typography.heading,
            color = EchoTheme.colors.textPrimary,
        )
        if (state.selectedDetail.isEmpty()) {
            EchoCard(modifier = Modifier.fillMaxWidth(), padding = CardPadding.Lg) {
                Text(
                    text = "Nothing recorded this day.",
                    style = EchoTheme.typography.bodySm,
                    color = EchoTheme.colors.textTertiary,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3)) {
                state.selectedDetail.forEach { row ->
                    EchoCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = row.time,
                                style = EchoTheme.typography.monoCaption,
                                color = EchoTheme.colors.textAccent,
                            )
                            Text(
                                text = row.title,
                                style = EchoTheme.typography.bodySm,
                                color = EchoTheme.colors.textPrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (row.taskCount > 0) {
                                Text(
                                    text = "${row.taskCount}",
                                    style = EchoTheme.typography.caption,
                                    color = EchoTheme.colors.textTertiary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
