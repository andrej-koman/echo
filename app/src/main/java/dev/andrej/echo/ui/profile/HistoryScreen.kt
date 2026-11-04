package dev.andrej.echo.ui.profile

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.R
import dev.andrej.echo.ui.components.DragHandle
import androidx.compose.ui.platform.LocalDensity
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.notes.NoteActionsSheets
import dev.andrej.echo.ui.notes.NoteCard
import dev.andrej.echo.ui.notes.NotesViewModel
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.LocalReducedMotion
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onPrevious: (Boolean) -> Unit,
    onNext: (Boolean) -> Unit,
    onToday: () -> Unit,
    onPickDay: (LocalDate) -> Unit,
    notesViewModel: NotesViewModel,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val allNotes by notesViewModel.calendarNotes.collectAsStateWithLifecycle()
    val folders by notesViewModel.folders.collectAsStateWithLifecycle()
    val ids = remember(state.selectedDetail) { state.selectedDetail.map { it.id }.toSet() }
    val notes = allNotes.filter { it.id in ids }
    var actingId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(state.selectedDay) { listState.scrollToItem(0) }

    Column(modifier.fillMaxSize()) {
        CalendarPanel(state, expanded, { expanded = !expanded },
            { onPrevious(expanded) }, { onNext(expanded) }, onToday, onPickDay)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = EchoTheme.spacing.gutterScreen,
                end = EchoTheme.spacing.gutterScreen,
                top = 22.dp,
                bottom = 130.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.selectedDayLabel, style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold),
                        color = EchoTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                    if (state.selectedDetail.isNotEmpty()) {
                        val count = state.selectedDetail.size
                        Text(if (count == 1) "1 note" else "$count notes", style = EchoTheme.typography.micro,
                            color = EchoTheme.colors.textTertiary)
                    }
                }
            }
            if (state.selectedDetail.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No notes on this day", style = EchoTheme.typography.bodySm, color = EchoTheme.colors.textSecondary)
                        state.previousNoteDay?.let { previous ->
                            Text(
                                "Go to ${DateTimeFormatter.ofPattern("EEE, d MMM").format(previous)}  →",
                                style = EchoTheme.typography.bodySm.copy(fontWeight = FontWeight.Medium),
                                color = EchoTheme.colors.textAccent,
                                modifier = Modifier.clip(CircleShape).clickable { onPickDay(previous) }.padding(16.dp),
                            )
                        }
                    }
                }
            }
            items(notes, key = { it.id }) { note ->
                NoteCard(row = note, onOpen = { onOpen(note.id) }, onMore = { actingId = note.id })
            }
        }
    }
    NoteActionsSheets(
        note = allNotes.firstOrNull { it.id == actingId },
        folders = folders,
        onMove = notesViewModel::moveToFolder,
        onDelete = notesViewModel::delete,
        onDismiss = { actingId = null },
    )
}

@Composable
internal fun CalendarPanel(
    state: HistoryUiState,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val colors = EchoTheme.colors
    val motion = if (LocalReducedMotion.current) Modifier else Modifier.animateContentSize()
    Column(
        modifier = Modifier.fillMaxWidth().then(motion)
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(colors.surfaceCard)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = if (expanded) "Collapse calendar" else "Expand calendar", onClick = onToggle)
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(state.monthLabel, style = EchoTheme.typography.heading, color = colors.textPrimary,
                    modifier = Modifier.weight(1f, fill = false))
                Icon(painterResource(R.drawable.ic_chevron_down),
                    contentDescription = null, tint = colors.textAccent, modifier = Modifier.size(14.dp).rotate(if (expanded) 180f else 0f))
            }
            Text("Today", style = EchoTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.textAccent,
                modifier = Modifier.clip(CircleShape).background(colors.surfaceAccentSoft)
                    .clickable(onClick = onToday).padding(horizontal = 14.dp, vertical = 12.dp))
            EchoIconButton(R.drawable.ic_chevron_left, if (expanded) "Previous month" else "Previous week", onPrevious)
            EchoIconButton(R.drawable.ic_chevron_right, if (expanded) "Next month" else "Next week", onNext)
        }
        Row(Modifier.fillMaxWidth()) {
            state.weekdayLabels.forEach { label ->
                Text(label.uppercase(), style = EchoTheme.typography.microCaps, color = colors.textTertiary,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Column(Modifier.padding(top = 6.dp)) {
            (if (expanded) state.days else state.weekDays).chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        CalendarDay(day, day.date == state.selectedDay, { onPick(day.date) }, Modifier.weight(1f))
                    }
                }
            }
        }
        var handleDrag by remember { mutableFloatStateOf(0f) }
        val threshold = with(LocalDensity.current) { 24.dp.toPx() }
        DragHandle(
            description = "Calendar drag handle",
            onDrag = { handleDrag += it },
            onRelease = {
                if ((!expanded && handleDrag > threshold) || (expanded && handleDrag < -threshold)) onToggle()
                handleDrag = 0f
            },
            onCancel = { handleDrag = 0f },
        )
    }
}

@Composable
private fun CalendarDay(day: HistoryDay, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = EchoTheme.colors
    Column(
        modifier.height(56.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                selected = isSelected
                contentDescription = "${day.date}, ${if (day.heatLevel == 3) "3 or more" else day.heatLevel} notes"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape)
                .then(if (isSelected) Modifier.background(colors.actionPrimaryBg) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(day.date.dayOfMonth.toString(), style = EchoTheme.typography.bodySm.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
                color = when {
                    isSelected -> colors.actionPrimaryFg
                    !day.inMonth -> colors.textTertiary
                    else -> colors.textPrimary
                })
        }
        Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(day.heatLevel.coerceIn(0, 3)) {
                Box(Modifier.size(3.dp).clip(CircleShape).background(colors.waveformActive))
            }
        }
    }
}
