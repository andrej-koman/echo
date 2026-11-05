package dev.andrej.echo.ui.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.luminance
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
    onOpenAllNotes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Folder?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            EchoTopBar(
                title = "Folders",
                trailing = {
                    EchoIconButton(
                        iconRes = R.drawable.ic_plus,
                        contentDescription = "New folder",
                        modifier = Modifier.clip(EchoTheme.radii.pill).background(EchoTheme.colors.actionPrimaryBg),
                        size = dev.andrej.echo.ui.components.IconButtonSize.Md,
                        tint = EchoTheme.colors.actionPrimaryFg,
                        onClick = { creating = true },
                    )
                },
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = EchoTheme.spacing.gutterScreen,
                    end = EchoTheme.spacing.gutterScreen,
                    top = 14.dp,
                    bottom = 150.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item {
                    EchoCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenAllNotes, padding = CardPadding.None) {
                        Box(Modifier.padding(horizontal = 12.dp)) {
                        FolderListRow(
                            row = FolderRow(Folder("", "All notes", 0, "Folder"), state.total),
                            onClick = onOpenAllNotes,
                        )
                        }
                    }
                }
                if (state.loaded) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("YOUR FOLDERS", style = EchoTheme.typography.micro,
                                color = EchoTheme.colors.textTertiary)
                            if (state.folders.isEmpty()) {
                                Text("Tap + to create your first folder",
                                    style = EchoTheme.typography.body,
                                    color = EchoTheme.colors.textSecondary)
                            } else {
                                EchoCard(modifier = Modifier.fillMaxWidth(), padding = CardPadding.None) {
                                    Column(modifier = Modifier.padding(horizontal = EchoTheme.spacing.s4)) {
                                        state.folders.forEachIndexed { index, row ->
                                            FolderListRow(row, { onOpen(row.folder.id) }, { editing = row.folder })
                                            if (index != state.folders.lastIndex) {
                                                HorizontalDivider(color = EchoTheme.colors.borderSubtle,
                                                    modifier = Modifier.padding(start = 50.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        FolderSheet(
            visible = editing != null,
            folder = editing,
            onSave = { name, color, icon ->
                editing?.let { viewModel.save(it.id, name, color, icon) }
                editing = null
            },
            onDismiss = { editing = null },
            onDelete = { editing?.let { viewModel.delete(it.id) }; editing = null },
        )
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
private fun FolderListRow(row: FolderRow, onClick: () -> Unit, onEdit: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onEdit, onLongClickLabel = "Edit folder")
            .padding(vertical = EchoTheme.spacing.s4),
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FolderBadge(row.folder)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.folder.name, style = EchoTheme.typography.body,
                color = EchoTheme.colors.textPrimary)
            Text(text = "${row.noteCount} ${if (row.noteCount == 1) "note" else "notes"}",
                style = EchoTheme.typography.caption, color = EchoTheme.colors.textTertiary)
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = EchoTheme.colors.textTertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun folderPalette(): List<Color> {
    val dark = EchoTheme.colors.surfacePage.luminance() < 0.5f
    return EchoTheme.colors.palette + if (dark) listOf(
        Color(0xFF91C4BC), Color(0xFF99B8DC), Color(0xFFBBA6D3), Color(0xFFD6A0B0), Color(0xFFBDBD83),
    ) else listOf(
        Color(0xFF397D74), Color(0xFF496F9F), Color(0xFF80619B), Color(0xFFA0526B), Color(0xFF77782F),
    )
}

@Composable
internal fun folderColor(index: Int): Color {
    val palette = folderPalette()
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
        Text("Name", style = EchoTheme.typography.caption, color = colors.textSecondary)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(EchoTheme.radii.md)
                .background(colors.surfaceSunken)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (name.isEmpty()) {
                Text(text = "Folder", style = EchoTheme.typography.body, color = colors.textTertiary)
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
        Text("Color", style = EchoTheme.typography.caption, color = colors.textSecondary)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            folderPalette().indices.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { index ->
                        Box(
                            Modifier.size(48.dp)
                                .clip(EchoTheme.radii.pill)
                                .background(folderColor(index))
                                .then(if (index == color) Modifier.border(3.dp, colors.textPrimary, EchoTheme.radii.pill) else Modifier)
                                .semantics { contentDescription = "Folder color ${index + 1}" }
                                .selectable(selected = index == color, role = Role.RadioButton, onClick = { color = index }),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (index == color) Icon(painterResource(R.drawable.ic_check), null,
                                tint = if (folderColor(index).luminance() > 0.4f) Color(0xFF2A2318) else Color.White,
                                modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
        Text("Icon", style = EchoTheme.typography.caption, color = colors.textSecondary)
        IconGrid(selected = icon, tint = folderColor(color), onPick = { icon = it })
        EchoButton(
            text = "Save",
            leading = { Icon(painterResource(R.drawable.ic_check), null, modifier = Modifier.size(18.dp)) },
            onClick = { onSave(name, color, icon) },
            enabled = name.isNotBlank(),
            fullWidth = true,
        )
        if (onDelete != null) {
            EchoButton(
                text = "Delete",
                onClick = onDelete,
                variant = ButtonVariant.Danger,
                leading = { Icon(painterResource(R.drawable.ic_trash), null, modifier = Modifier.size(18.dp)) },
                fullWidth = true,
            )

        }
    }
}

@Composable
private fun IconGrid(selected: FolderIcon, tint: Color, onPick: (FolderIcon) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3)) {
        FolderIcon.entries.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { icon ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(EchoTheme.radii.md)
                            .background(
                                if (icon == selected) tint.copy(alpha = 0.18f) else EchoTheme.colors.surfaceSunken,
                            )
                            .selectable(selected = icon == selected, role = Role.RadioButton, onClick = { onPick(icon) }),
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
