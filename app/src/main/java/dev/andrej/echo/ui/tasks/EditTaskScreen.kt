package dev.andrej.echo.ui.tasks

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.R
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.components.FilterPill
import dev.andrej.echo.ui.formatTime
import dev.andrej.echo.ui.midnight
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.transcripts.clock
import dev.andrej.echo.ui.transcripts.title
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

    var editedText by remember(taskId) { mutableStateOf(task?.text.orEmpty()) }
    var editedDueAt by remember(taskId) { mutableStateOf(task?.dueAt ?: 0L) }
    var editedHasTime by remember(taskId) { mutableStateOf(task?.hasTime ?: false) }

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
                        onClick = {
                            viewModel.delete(task.id)
                            onBack()
                        },
                    )
                }
                EchoIconButton(
                    iconRes = R.drawable.ic_check,
                    contentDescription = "Save",
                    onClick = {
                        if (task != null) {
                            viewModel.update(task.id, editedText, editedDueAt, editedHasTime)
                            onBack()
                        }
                    },
                )
            },
        )

        if (task == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Task not found.",
                    style = EchoTheme.typography.body,
                    color = EchoTheme.colors.textTertiary,
                )
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
            BasicTextField(
                value = editedText,
                onValueChange = { editedText = it },
                textStyle = EchoTheme.typography.body.copy(color = EchoTheme.colors.textPrimary),
                cursorBrush = SolidColor(EchoTheme.colors.textAccent),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionDivider("WHEN")

            DayRow(dueAt = editedDueAt, onPick = { editedDueAt = it })

            TimeRow(
                dueAt = editedDueAt,
                hasTime = editedHasTime,
                onPick = { editedDueAt = it },
                onEnable = { editedHasTime = true },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Notify me",
                    style = EchoTheme.typography.bodySm,
                    color = EchoTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = editedHasTime,
                    onCheckedChange = { checked ->
                        editedHasTime = checked
                        if (!checked) editedDueAt = editedDueAt.midnight()
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = EchoTheme.colors.actionPrimaryBg),
                )
            }

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

            transcripts.firstOrNull { it.id == task.sourceTranscriptId }?.let { transcript ->
                SectionDivider("HEARD IN")
                EchoCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenSource(transcript.id) },
                ) {
                    Column {
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
private fun DayRow(dueAt: Long, onPick: (Long) -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EchoTheme.colors.surfaceCard)
            .clickable { showDatePicker(context, dueAt, onPick) }
            .padding(EchoTheme.spacing.s5),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = "Day", style = EchoTheme.typography.bodySm, color = EchoTheme.colors.textSecondary)
        Text(text = dayLabel(dueAt), style = EchoTheme.typography.bodySm, color = EchoTheme.colors.textPrimary)
    }
}

@Composable
private fun TimeRow(dueAt: Long, hasTime: Boolean, onPick: (Long) -> Unit, onEnable: () -> Unit) {
    val context = LocalContext.current
    val overdue = hasTime && dueAt < System.currentTimeMillis()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EchoTheme.colors.surfaceCard)
            .clickable {
                if (!hasTime) onEnable()
                showTimePicker(context, dueAt, onPick)
            }
            .padding(EchoTheme.spacing.s5),
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_clock),
            contentDescription = null,
            tint = if (hasTime) EchoTheme.colors.textTertiary else EchoTheme.colors.borderStrong,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "Time",
            style = EchoTheme.typography.bodySm,
            color = EchoTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = when {
                !hasTime -> "None"
                overdue -> "${formatTime(dueAt)} · overdue"
                else -> formatTime(dueAt)
            },
            style = EchoTheme.typography.bodySm,
            color = when {
                !hasTime -> EchoTheme.colors.textTertiary
                overdue -> EchoTheme.colors.textDanger
                else -> EchoTheme.colors.textPrimary
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
