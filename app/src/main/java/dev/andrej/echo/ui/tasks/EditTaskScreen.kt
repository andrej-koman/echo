package dev.andrej.echo.ui.tasks

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.R
import dev.andrej.echo.ui.components.CardPadding
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.EchoSwitch
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.components.FilterPill
import dev.andrej.echo.ui.components.IconButtonVariant
import dev.andrej.echo.ui.components.ThinkingDots
import dev.andrej.echo.ui.formatTime
import dev.andrej.echo.ui.midnight
import dev.andrej.echo.ui.notes.clock
import dev.andrej.echo.ui.notes.title
import dev.andrej.echo.ui.theme.EchoTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun EditTaskScreen(
    viewModel: TasksViewModel,
    taskId: String,
    onBack: () -> Unit,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val transcripts by viewModel.transcripts.collectAsStateWithLifecycle()
    val task = (state.groups.flatMap { it.items } + state.completed).firstOrNull { it.id == taskId }

    var editedText by remember { mutableStateOf("") }
    var editedDueAt by remember { mutableStateOf(0L) }
    var editedHasTime by remember { mutableStateOf(false) }
    var editedHasDate by remember { mutableStateOf(true) }
    var editedNotify by remember { mutableStateOf(true) }
    var loadedFor by remember { mutableStateOf<String?>(null) }

    if (task != null && loadedFor != taskId) {
        editedText = task.text
        editedDueAt = task.dueAt
        editedHasTime = task.hasTime
        editedHasDate = task.hasDate
        editedNotify = task.notify
        loadedFor = taskId
    }

    Column(modifier = modifier.fillMaxSize()) {
        EchoTopBar(
            title = "Edit task",
            leading = {
                EchoIconButton(
                    iconRes = R.drawable.ic_chevron_left,
                    contentDescription = "Back",
                    onClick = onBack,
                )
            },
            trailing = {
                if (task != null) {
                    EchoIconButton(
                        iconRes = R.drawable.ic_trash,
                        contentDescription = "Delete task",
                        tint = EchoTheme.colors.textDanger,
                        modifier = Modifier.scale(0.8f),
                        onClick = {
                            viewModel.delete(task.id)
                            onBack()
                        },
                    )
                }
                EchoIconButton(
                    iconRes = R.drawable.ic_check,
                    contentDescription = "Save",
                    variant = IconButtonVariant.Soft,
                    onClick = {
                        if (task != null) {
                            viewModel.update(task.id, editedText, editedDueAt, editedHasTime, editedHasDate, editedNotify)
                            onBack()
                        }
                    },
                )
            },
        )

        if (task == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.loaded) {
                    Text(
                        text = "Task not found.",
                        style = EchoTheme.typography.body,
                        color = EchoTheme.colors.textTertiary,
                    )
                } else {
                    ThinkingDots()
                }
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(EchoTheme.spacing.gutterScreen)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s6),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
                verticalAlignment = Alignment.Top,
            ) {
                TaskDoneCheckbox(
                    done = task.done,
                    onToggle = { viewModel.setDone(task.id, !task.done) },
                )
                Column(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = editedText,
                        onValueChange = { editedText = it },
                        textStyle = EchoTheme.typography.heading.copy(color = EchoTheme.colors.textPrimary),
                        cursorBrush = SolidColor(EchoTheme.colors.textAccent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_pen_line),
                            contentDescription = null,
                            tint = EchoTheme.colors.textTertiary,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = "Tap to rewrite",
                            style = EchoTheme.typography.micro,
                            color = EchoTheme.colors.textTertiary,
                        )
                    }
                }
            }

            SectionDivider("WHEN")

            EchoCard(modifier = Modifier.fillMaxWidth(), padding = CardPadding.None) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(EchoTheme.spacing.s5),
                    horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Has a date",
                            style = EchoTheme.typography.bodySm,
                            color = EchoTheme.colors.textPrimary,
                        )
                        Text(
                            text = "Off shows as a plain to-do, due today until done",
                            style = EchoTheme.typography.micro,
                            color = EchoTheme.colors.textTertiary,
                        )
                    }
                    EchoSwitch(
                        checked = editedHasDate,
                        onCheckedChange = { checked ->
                            editedHasDate = checked
                            if (!checked) editedHasTime = false
                        },
                    )
                }
                if (editedHasDate) {
                    HorizontalDivider(color = EchoTheme.colors.borderSubtle)
                    DayRow(dueAt = editedDueAt, onPick = { editedDueAt = it })
                    HorizontalDivider(color = EchoTheme.colors.borderSubtle)
                    TimeRow(
                        dueAt = editedDueAt,
                        hasTime = editedHasTime,
                        onPick = { editedDueAt = it },
                        onEnable = { editedHasTime = true },
                    )
                    HorizontalDivider(color = EchoTheme.colors.borderSubtle)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(EchoTheme.spacing.s5),
                        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notify me",
                                style = EchoTheme.typography.bodySm,
                                color = EchoTheme.colors.textPrimary,
                            )
                            Text(
                                text = "A time always rings",
                                style = EchoTheme.typography.micro,
                                color = EchoTheme.colors.textTertiary,
                            )
                        }
                        EchoSwitch(
                            checked = editedNotify,
                            onCheckedChange = { checked -> editedNotify = checked },
                        )
                    }
                }
            }

            if (editedHasDate) {
                QuickChipRow(
                    dueAt = editedDueAt,
                    hasTime = editedHasTime,
                    onToday = { editedDueAt = withDate(editedDueAt, System.currentTimeMillis()) },
                    onTomorrow = {
                        editedDueAt = withDate(editedDueAt, System.currentTimeMillis() + DAY_MILLIS)
                    },
                    onTime = { hourOfDay ->
                        editedHasTime = true
                        editedDueAt = editedDueAt.withHour(hourOfDay)
                    },
                )
            }

            transcripts.firstOrNull { it.id == task.sourceTranscriptId }?.let { transcript ->
                SectionDivider("HEARD IN")
                EchoCard(
                    modifier = Modifier.fillMaxWidth(),
                    padding = CardPadding.None,
                    onClick = { onOpenSource(transcript.id) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(EchoTheme.spacing.s5),
                        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(EchoTheme.colors.surfaceAccentSoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_audio_waveform),
                                contentDescription = null,
                                tint = EchoTheme.colors.textAccent,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = transcript.title ?: title(transcript.text),
                                style = EchoTheme.typography.bodySm,
                                color = EchoTheme.colors.textPrimary,
                            )
                            Text(
                                text = "${sourceDate.format(Date(transcript.createdAt))} · ${clock(transcript.durationMs)}",
                                style = EchoTheme.typography.micro,
                                color = EchoTheme.colors.textTertiary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
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
}

@Composable
private fun SectionDivider(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = EchoTheme.typography.microCaps,
            color = EchoTheme.colors.textTertiary,
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = EchoTheme.colors.borderSubtle)
    }
}

@Composable
private fun TaskDoneCheckbox(done: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(top = 2.dp)
            .size(24.dp)
            .clip(CircleShape)
            .then(
                if (done) {
                    Modifier.background(EchoTheme.colors.actionPrimaryBg)
                } else {
                    Modifier.border(1.5.dp, EchoTheme.colors.borderStrong, CircleShape)
                },
            )
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = "Done",
                tint = EchoTheme.colors.textInverse,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun WhenRow(
    iconRes: Int,
    iconTint: Color,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(EchoTheme.spacing.s5),
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = EchoTheme.typography.bodySm,
            color = EchoTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        content()
        Icon(
            painter = painterResource(R.drawable.ic_arrow_right),
            contentDescription = null,
            tint = EchoTheme.colors.textTertiary,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun DayRow(dueAt: Long, onPick: (Long) -> Unit) {
    val context = LocalContext.current
    WhenRow(
        iconRes = R.drawable.ic_calendar,
        iconTint = EchoTheme.colors.textTertiary,
        label = "Day",
        onClick = { showDatePicker(context, dueAt, onPick) },
    ) {
        Text(
            text = dayLabel(dueAt),
            style = EchoTheme.typography.monoCaption.copy(fontWeight = FontWeight.Bold),
            color = EchoTheme.colors.textAccent,
        )
    }
}

@Composable
private fun TimeRow(dueAt: Long, hasTime: Boolean, onPick: (Long) -> Unit, onEnable: () -> Unit) {
    val context = LocalContext.current
    val overdue = hasTime && dueAt < System.currentTimeMillis()
    WhenRow(
        iconRes = R.drawable.ic_clock,
        iconTint = if (hasTime) EchoTheme.colors.textTertiary else EchoTheme.colors.borderStrong,
        label = "Time",
        onClick = {
            if (!hasTime) onEnable()
            showTimePicker(context, dueAt, onPick)
        },
    ) {
        Text(
            text = when {
                !hasTime -> "None"
                overdue -> "${formatTime(dueAt)} · overdue"
                else -> formatTime(dueAt)
            },
            style = EchoTheme.typography.monoCaption.copy(fontWeight = FontWeight.Bold),
            color = when {
                !hasTime -> EchoTheme.colors.textTertiary
                overdue -> EchoTheme.colors.textDanger
                else -> EchoTheme.colors.textAccent
            },
        )
    }
}

@Composable
private fun QuickChipRow(
    dueAt: Long,
    hasTime: Boolean,
    onToday: () -> Unit,
    onTomorrow: () -> Unit,
    onTime: (Int) -> Unit,
) {
    val today = (dueAt.midnight() - System.currentTimeMillis().midnight()) / DAY_MILLIS == 0L
    val tomorrow = (dueAt.midnight() - System.currentTimeMillis().midnight()) / DAY_MILLIS == 1L
    val hour = Calendar.getInstance().apply { timeInMillis = dueAt }.get(Calendar.HOUR_OF_DAY)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterPill(label = "Today", active = today, onClick = onToday)
        FilterPill(label = "Tomorrow", active = tomorrow, onClick = onTomorrow)
        FilterPill(label = "9 AM", active = hasTime && hour == 9, onClick = { onTime(9) })
        FilterPill(label = "6 PM", active = hasTime && hour == 18, onClick = { onTime(18) })
    }
}

private fun showDatePicker(context: Context, dueAt: Long, onPick: (Long) -> Unit) {
    val calendar = Calendar.getInstance().apply { timeInMillis = dueAt }
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onPick(
                Calendar.getInstance().apply {
                    timeInMillis = dueAt
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }.timeInMillis,
            )
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH),
    ).show()
}

private fun showTimePicker(context: Context, dueAt: Long, onPick: (Long) -> Unit) {
    val calendar = Calendar.getInstance().apply { timeInMillis = dueAt }
    TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            onPick(
                Calendar.getInstance().apply {
                    timeInMillis = dueAt
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                }.timeInMillis,
            )
        },
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
        false,
    ).show()
}

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

private fun dayLabel(dueAt: Long): String = when ((dueAt.midnight() - System.currentTimeMillis().midnight()) / DAY_MILLIS) {
    0L -> "Today"
    1L -> "Tomorrow"
    else -> dayFormat.format(Date(dueAt))
}

private fun withDate(dueAt: Long, sourceDay: Long): Long {
    val target = Calendar.getInstance().apply { timeInMillis = sourceDay }
    return Calendar.getInstance().apply {
        timeInMillis = dueAt
        set(Calendar.YEAR, target.get(Calendar.YEAR))
        set(Calendar.MONTH, target.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, target.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun Long.withHour(hourOfDay: Int): Long = Calendar.getInstance().apply {
    timeInMillis = this@withHour
    set(Calendar.HOUR_OF_DAY, hourOfDay)
    set(Calendar.MINUTE, 0)
}.timeInMillis

private val dayFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
private val sourceDate = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
