package dev.andrej.echo.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.data.TodoItem
import dev.andrej.echo.ui.formatDue
import dev.andrej.echo.ui.formatTime
import dev.andrej.echo.ui.components.CardPadding
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.theme.EchoTheme
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale

private val upcomingDateFormat = SimpleDateFormat("d MMM", Locale.getDefault())

@Composable
internal fun TaskGroupCard(
    items: List<TodoItem>,
    showDate: Boolean,
    sourceTitleFor: (String) -> String?,
    onToggle: (TodoItem) -> Unit,
    onEdit: (TodoItem) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: ((TodoItem) -> Unit)? = null,
) {
    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    EchoCard(modifier = modifier.fillMaxWidth(), padding = CardPadding.None) {
        Column(modifier = Modifier.padding(vertical = EchoTheme.spacing.s2, horizontal = EchoTheme.spacing.s4)) {
            items.forEachIndexed { index, task ->
                TaskRow(
                    task = task,
                    overdue = isOverdue(task, now, zone),
                    showDate = showDate,
                    sourceTitle = sourceTitleFor(task.sourceTranscriptId),
                    onToggle = { onToggle(task) },
                    onEdit = { onEdit(task) },
                    onLongPress = onLongPress?.let { press -> { press(task) } },
                )
                if (index != items.lastIndex) {
                    HorizontalDivider(color = EchoTheme.colors.borderSubtle)
                }
            }
        }
    }
}

@Composable
internal fun TaskRow(
    task: TodoItem,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    showDate: Boolean = false,
    overdue: Boolean = false,
    sourceTitle: String? = null,
    onLongPress: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onEdit, onLongClick = onLongPress)
            .padding(vertical = EchoTheme.spacing.s4, horizontal = EchoTheme.spacing.s2),
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskCheckbox(done = task.done, overdue = overdue, onToggle = onToggle)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = formatTaskText(task.text),
                style = EchoTheme.typography.bodySm,
                color = if (task.done) EchoTheme.colors.textTertiary else EchoTheme.colors.textPrimary,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
            )
            val whenIcon = if (task.hasTime) R.drawable.ic_bell else R.drawable.ic_calendar
            val whenLabel = when {
                task.hasTime -> if (showDate) formatDue(task.dueAt, System.currentTimeMillis()) else formatTime(task.dueAt)
                showDate -> upcomingDateFormat.format(Date(task.dueAt))
                else -> null
            }
            val whenColor = when {
                task.done -> EchoTheme.colors.textTertiary
                overdue -> EchoTheme.colors.textDanger
                task.hasTime -> EchoTheme.colors.textAccent
                else -> EchoTheme.colors.textSecondary
            }
            val showPlaceholder = whenLabel == null && !task.done
            if (whenLabel != null || sourceTitle != null || showPlaceholder) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (whenLabel != null) {
                        Icon(
                            painter = painterResource(whenIcon),
                            contentDescription = null,
                            tint = whenColor,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = whenLabel,
                            style = EchoTheme.typography.micro.copy(fontWeight = FontWeight.Bold),
                            color = whenColor,
                        )
                    } else if (showPlaceholder) {
                        Text(
                            text = "Add a time",
                            style = EchoTheme.typography.micro,
                            color = EchoTheme.colors.textTertiary,
                        )
                    }
                    if (sourceTitle != null) {
                        if (whenLabel != null || showPlaceholder) {
                            Box(
                                modifier = Modifier
                                    .size(3.dp)
                                    .clip(CircleShape)
                                    .background(EchoTheme.colors.borderStrong),
                            )
                        }
                        Text(
                            text = sourceTitle,
                            style = EchoTheme.typography.micro,
                            color = EchoTheme.colors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TaskCheckbox(
    done: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    overdue: Boolean = false,
    size: Dp = 24.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (done) {
                    Modifier.background(EchoTheme.colors.actionPrimaryBg)
                } else {
                    Modifier.border(1.5.dp, if (overdue) EchoTheme.colors.textDanger else EchoTheme.colors.borderStrong, CircleShape)
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
