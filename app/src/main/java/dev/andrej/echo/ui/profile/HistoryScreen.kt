package dev.andrej.echo.ui.profile

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
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
import androidx.compose.ui.platform.LocalContext
import dev.andrej.echo.ui.detail.copyToClipboard

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
    val analyzingId by notesViewModel.analyzingId.collectAsStateWithLifecycle()
    val context = LocalContext.current
    NoteActionsSheets(
        note = allNotes.firstOrNull { it.id == actingId },
        folders = folders,
        analyzingId = analyzingId,
        onMove = notesViewModel::moveToFolder,
        onRename = notesViewModel::rename,
        onCopy = { id -> notesViewModel.transcriptText(id) { context.copyToClipboard(it) } },
        onAnalyze = notesViewModel::analyze,
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
    val reducedMotion = LocalReducedMotion.current
    val weeks = state.days.chunked(7)
    val selectedRow = (state.days.indexOfFirst { it.date == state.selectedDay } / 7).coerceAtLeast(0)
    val density = LocalDensity.current
    val rowPx = with(density) { DayHeight.toPx() }
    val collapsedPx = rowPx
    val expandedPx = rowPx * weeks.size.coerceAtLeast(1)
    val height = remember { Animatable(if (expanded) expandedPx else collapsedPx) }
    var dragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(expanded, expandedPx) {
        if (dragging) return@LaunchedEffect
        val target = if (expanded) expandedPx else collapsedPx
        if (reducedMotion) height.snapTo(target) else height.animateTo(target)
    }
    val progress = if (expandedPx > collapsedPx) ((height.value - collapsedPx) / (expandedPx - collapsedPx)).coerceIn(0f, 1f) else 1f
    Column(
        modifier = Modifier.fillMaxWidth()
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
                    contentDescription = null, tint = colors.textAccent, modifier = Modifier.size(14.dp).rotate(180f * progress))
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
        // The month grid is clipped to the dragged height; the selected week slides from its row
        // to the top so collapsing reveals or hides the other weeks around it.
        Box(
            Modifier.padding(top = 6.dp).fillMaxWidth()
                .height(with(density) { height.value.toDp() })
                .clipToBounds(),
        ) {
            Column(
                Modifier.wrapContentHeight(Alignment.Top, unbounded = true)
                    .offset { IntOffset(0, (-selectedRow * rowPx * (1f - progress)).roundToInt()) },
            ) {
                weeks.forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            CalendarDay(day, day.date == state.selectedDay, { onPick(day.date) }, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        DragHandle(
            description = "Calendar drag handle",
            onDrag = { delta ->
                dragging = true
                scope.launch { height.snapTo((height.value + delta).coerceIn(collapsedPx, expandedPx)) }
            },
            onRelease = {
                val open = if (expanded) progress > 0.75f else progress > 0.25f
                scope.launch {
                    height.animateTo(if (open) expandedPx else collapsedPx)
                    dragging = false
                }
                if (open != expanded) onToggle()
            },
            onCancel = {
                scope.launch {
                    height.animateTo(if (expanded) expandedPx else collapsedPx)
                    dragging = false
                }
            },
        )
    }
}

private val DayHeight = 56.dp

@Composable
private fun CalendarDay(day: HistoryDay, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = EchoTheme.colors
    Column(
        modifier.height(DayHeight).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
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
