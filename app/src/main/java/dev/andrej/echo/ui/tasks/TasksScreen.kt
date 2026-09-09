package dev.andrej.echo.ui.tasks

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.R
import dev.andrej.echo.data.TodoItem
import dev.andrej.echo.ui.formatDue
import dev.andrej.echo.ui.formatTime
import dev.andrej.echo.ui.components.ButtonVariant
import dev.andrej.echo.ui.components.CardPadding
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.components.EmptyState
import dev.andrej.echo.ui.components.Mascot
import dev.andrej.echo.ui.components.MascotVariant
import dev.andrej.echo.ui.components.ThinkingDots
import dev.andrej.echo.ui.notes.title
import dev.andrej.echo.ui.theme.EchoTheme
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/** Attempted once per process, not on every Home -> Tasks -> Home -> Tasks round trip. */
private var askedForNotifications = false

@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    onEditTask: (String) -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val transcripts by viewModel.transcripts.collectAsStateWithLifecycle()
    val sourceTitles = remember(transcripts) {
        transcripts.associate { it.id to (it.title ?: title(it.text)) }
    }
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(RequestPermission()) {}
    var completedExpanded by remember { mutableStateOf(false) }
    val onlyCompleted = state.loaded && state.groups.isEmpty() && state.completed.isNotEmpty()
    LaunchedEffect(onlyCompleted) {
        if (onlyCompleted) completedExpanded = true
    }

    LaunchedEffect(state.groups.isNotEmpty()) {
        if (!askedForNotifications && state.groups.isNotEmpty()) {
            askedForNotifications = true
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        EchoTopBar(
            title = "Tasks",
            query = state.query,
            onQueryChange = viewModel::search,
            searchPlaceholder = "Search tasks…",
            leading = { Mascot(size = 30.dp, variant = MascotVariant.Tasks) },
        )

        if (!state.loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ThinkingDots() }
            return@Column
        }

        if (state.groups.isEmpty() && state.completed.isEmpty()) {
            EmptyState(
                title = if (state.aiOff) "Turn on AI to get tasks" else "Nothing to do",
                body = if (state.aiOff) {
                    "Echo pulls tasks out of what you record, but needs on-device AI turned on first."
                } else {
                    "Record something with an action in it and it will show up here."
                },
                variant = MascotVariant.Tasks,
                modifier = Modifier.padding(bottom = 150.dp),
            ) {
                if (state.aiOff) {
                    EchoButton(
                        text = "Turn on AI",
                        onClick = onOpenProfile,
                        variant = ButtonVariant.Secondary,
                        modifier = Modifier.padding(top = EchoTheme.spacing.s4),
                    )
                }
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = EchoTheme.spacing.gutterScreen,
                end = EchoTheme.spacing.gutterScreen,
                top = 14.dp,
                bottom = 150.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
        ) {
            state.groups.forEach { group ->
                item(key = "header-${group.label}") {
                    SectionHeader(
                        label = group.label,
                        stamp = when (group.label) {
                            "Today" -> groupDateFormat.format(Date(System.currentTimeMillis())).uppercase()
                            "Tomorrow" -> groupDateFormat.format(Date(System.currentTimeMillis() + DAY_MILLIS)).uppercase()
                            else -> null
                        },
                        count = group.items.size,
                        modifier = Modifier.padding(start = 2.dp, top = EchoTheme.spacing.s4),
                    )
                }
                item(key = "group-${group.label}") {
                    TaskGroupCard(
                        items = group.items,
                        showDate = group.showDate,
                        sourceTitleFor = { sourceTitles[it] },
                        onToggle = { task -> viewModel.setDone(task.id, !task.done) },
                        onEdit = { task -> onEditTask(task.id) },
                    )
                }
            }

            if (state.completed.isNotEmpty()) {
                item(key = "completed-header") {
                    CompletedHeader(
                        count = state.completed.size,
                        expanded = completedExpanded,
                        onToggle = { completedExpanded = !completedExpanded },
                        modifier = Modifier.padding(start = 2.dp, top = EchoTheme.spacing.s4),
                    )
                }
                if (completedExpanded) {
                    item(key = "completed-list") {
                        TaskGroupCard(
                            items = state.completed,
                            showDate = true,
                            sourceTitleFor = { sourceTitles[it] },
                            onToggle = { task -> viewModel.setDone(task.id, !task.done) },
                            onEdit = { task -> onEditTask(task.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Completed", style = EchoTheme.typography.heading, color = EchoTheme.colors.textPrimary)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = count.toString(),
            style = EchoTheme.typography.monoCaption.copy(fontWeight = FontWeight.Bold),
            color = EchoTheme.colors.textTertiary,
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_down),
            contentDescription = null,
            tint = EchoTheme.colors.textTertiary,
            modifier = Modifier
                .size(14.dp)
                .rotate(if (expanded) 180f else 0f),
        )
    }
}

private val upcomingDateFormat = SimpleDateFormat("d MMM", Locale.getDefault())
private val groupDateFormat = SimpleDateFormat("EEE d MMM", Locale.getDefault())
private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

@Composable
private fun SectionHeader(label: String, stamp: String?, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = EchoTheme.typography.heading,
            color = EchoTheme.colors.textPrimary,
            modifier = Modifier.alignByBaseline(),
        )
        if (stamp != null) {
            Text(
                text = stamp,
                style = EchoTheme.typography.microCaps,
                color = EchoTheme.colors.textTertiary,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = count.toString(),
            style = EchoTheme.typography.monoCaption.copy(fontWeight = FontWeight.Bold),
            color = EchoTheme.colors.textTertiary,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

/** One shared card per section — rows separated by a hairline, matching the design's grouped list. */
@Composable
internal fun TaskGroupCard(
    items: List<TodoItem>,
    showDate: Boolean,
    sourceTitleFor: (String) -> String?,
    onToggle: (TodoItem) -> Unit,
    onEdit: (TodoItem) -> Unit,
    modifier: Modifier = Modifier,
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
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
                text = task.text,
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
                            style = EchoTheme.typography.monoMicro.copy(fontWeight = FontWeight.Bold),
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
