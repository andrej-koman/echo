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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.R
import dev.andrej.echo.data.TodoItem
import dev.andrej.echo.ui.formatDue
import dev.andrej.echo.ui.formatTime
import dev.andrej.echo.ui.components.ButtonVariant
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.components.EmptyState
import dev.andrej.echo.ui.components.Mascot
import dev.andrej.echo.ui.components.MascotVariant
import dev.andrej.echo.ui.components.ThinkingDots
import dev.andrej.echo.ui.theme.EchoTheme

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
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(RequestPermission()) {}
    var completedExpanded by remember { mutableStateOf(false) }

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
                    Text(
                        text = group.label.uppercase(),
                        style = EchoTheme.typography.microCaps,
                        color = EchoTheme.colors.textTertiary,
                        modifier = Modifier.padding(start = 2.dp, top = EchoTheme.spacing.s4),
                    )
                }
                items(group.items, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        showDate = group.showDate,
                        onToggle = { viewModel.setDone(task.id, !task.done) },
                        onEdit = { onEditTask(task.id) },
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
                    items(state.completed, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            onToggle = { viewModel.setDone(task.id, !task.done) },
                            onEdit = { onEditTask(task.id) },
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
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Completed ($count)".uppercase(),
            style = EchoTheme.typography.microCaps,
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

@Composable
private fun TaskRow(
    task: TodoItem,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    showDate: Boolean = false,
) {
    EchoCard(modifier = Modifier.fillMaxWidth(), onClick = onEdit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TaskCheckbox(done = task.done, onToggle = onToggle)
            Text(
                text = task.text,
                style = EchoTheme.typography.bodySm,
                color = if (task.done) EchoTheme.colors.textTertiary else EchoTheme.colors.textPrimary,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f),
            )
            if (task.hasTime) {
                Text(
                    text = if (showDate) formatDue(task.dueAt, System.currentTimeMillis()) else formatTime(task.dueAt),
                    style = EchoTheme.typography.microCaps,
                    color = EchoTheme.colors.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun TaskCheckbox(done: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
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
