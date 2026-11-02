package dev.andrej.echo.ui.folders

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.R
import dev.andrej.echo.data.Folder
import dev.andrej.echo.ui.components.ButtonSize
import dev.andrej.echo.ui.components.ButtonVariant
import dev.andrej.echo.ui.components.CardPadding
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.components.EmptyState
import dev.andrej.echo.ui.components.Sheet
import dev.andrej.echo.ui.theme.EchoTheme

@Composable
fun FoldersScreen(
    viewModel: FoldersViewModel,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            EchoTopBar(
                title = "Folders",
                subtitle = when {
                    !state.loaded -> ""
                    state.unfiled == 1 -> "1 note not in a folder"
                    else -> "${state.unfiled} notes not in a folder"
                },
                trailing = {
                    EchoIconButton(
                        iconRes = R.drawable.ic_plus,
                        contentDescription = "New folder",
                        onClick = { creating = true },
                    )
                },
            )

            if (state.loaded && state.folders.isEmpty()) {
                EmptyState(
                    title = "No folders yet",
                    body = "Group notes by project, person or anything else. Move a note in from its own screen.",
                    modifier = Modifier.padding(bottom = 150.dp),
                ) {
                    EchoButton(
                        text = "New folder",
                        onClick = { creating = true },
                        variant = ButtonVariant.Secondary,
                        size = ButtonSize.Sm,
                        modifier = Modifier.padding(top = EchoTheme.spacing.s4),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = EchoTheme.spacing.gutterScreen,
                        end = EchoTheme.spacing.gutterScreen,
                        top = 14.dp,
                        bottom = 150.dp,
                    ),
                ) {
                    item {
                        EchoCard(modifier = Modifier.fillMaxWidth(), padding = CardPadding.None) {
                            Column(modifier = Modifier.padding(horizontal = EchoTheme.spacing.s4)) {
                                state.folders.forEachIndexed { index, row ->
                                    FolderListRow(row = row, onClick = { onOpen(row.folder.id) })
                                    if (index != state.folders.lastIndex) {
                                        HorizontalDivider(color = EchoTheme.colors.borderSubtle)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        FolderSheet(
            visible = creating,
            folder = null,
            onSave = { name, color, icon ->
                viewModel.save(null, name, color, icon)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun FolderListRow(row: FolderRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = EchoTheme.spacing.s4),
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FolderBadge(row.folder)
        Text(
            text = row.folder.name,
            style = EchoTheme.typography.body,
            color = EchoTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = row.noteCount.toString(),
            style = EchoTheme.typography.caption,
            color = EchoTheme.colors.textTertiary,
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = EchoTheme.colors.textTertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
internal fun folderColor(index: Int): Color {
    val palette = EchoTheme.colors.palette
    return palette[index.mod(palette.size)]
}

@Composable
internal fun FolderBadge(folder: Folder, size: Int = 36) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(EchoTheme.radii.md)
            .background(folderColor(folder.color).copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(FolderIcon.of(folder.icon).drawable),
            contentDescription = null,
            tint = folderColor(folder.color),
            modifier = Modifier.size((size / 2).dp),
        )
    }
}

/** Create with [folder] null, edit otherwise; [onDelete] only offered when editing. */
@Composable
internal fun FolderSheet(
    visible: Boolean,
    folder: Folder?,
    onSave: (name: String, color: Int, icon: FolderIcon) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember(folder, visible) { mutableStateOf(folder?.name.orEmpty()) }
    var color by remember(folder, visible) { mutableIntStateOf(folder?.color ?: 0) }
    var icon by remember(folder, visible) { mutableStateOf(folder?.let { FolderIcon.of(it.icon) } ?: FolderIcon.Folder) }
    val colors = EchoTheme.colors

    Sheet(visible = visible, onDismiss = onDismiss) {
        Text(
            text = if (folder == null) "New folder" else "Edit folder",
            style = EchoTheme.typography.heading,
            color = colors.textPrimary,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(EchoTheme.radii.md)
                .background(colors.surfaceSunken)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (name.isEmpty()) {
                Text(text = "Folder name", style = EchoTheme.typography.body, color = colors.textTertiary)
            }
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = EchoTheme.typography.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.textAccent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(name, color, icon) }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3)) {
            colors.palette.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(EchoTheme.radii.pill)
                        .background(folderColor(index))
                        .then(
                            if (index == color) {
                                Modifier.border(3.dp, colors.textPrimary, EchoTheme.radii.pill)
                            } else {
                                Modifier
                            },
                        )
                        .clickable { color = index },
                )
            }
        }
        IconGrid(selected = icon, tint = folderColor(color), onPick = { icon = it })
        EchoButton(
            text = "Save",
            onClick = { onSave(name, color, icon) },
            enabled = name.isNotBlank(),
            fullWidth = true,
        )
        if (onDelete != null) {
            EchoButton(
                text = "Delete folder",
                onClick = onDelete,
                variant = ButtonVariant.Danger,
                fullWidth = true,
            )
            Text(
                text = "Its notes stay, just out of any folder.",
                style = EchoTheme.typography.micro,
                color = colors.textTertiary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun IconGrid(selected: FolderIcon, tint: Color, onPick: (FolderIcon) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3)) {
        FolderIcon.entries.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3)) {
                row.forEach { icon ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(EchoTheme.radii.md)
                            .background(
                                if (icon == selected) tint.copy(alpha = 0.18f) else EchoTheme.colors.surfaceSunken,
                            )
                            .clickable { onPick(icon) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(icon.drawable),
                            contentDescription = icon.name,
                            tint = if (icon == selected) tint else EchoTheme.colors.textSecondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}
