package dev.andrej.echo.ui.folders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.R
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.components.EmptyState
import dev.andrej.echo.ui.notes.NotesGroupCard
import dev.andrej.echo.ui.notes.NotesGroupHeader
import dev.andrej.echo.ui.notes.NotesViewModel
import dev.andrej.echo.ui.theme.EchoTheme

@Composable
fun FolderScreen(
    folderId: String,
    foldersViewModel: FoldersViewModel,
    notesViewModel: NotesViewModel,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by foldersViewModel.state.collectAsStateWithLifecycle()
    val groups by remember(folderId) { notesViewModel.groupsIn(folderId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val row = state.folders.firstOrNull { it.folder.id == folderId }
    var editing by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            EchoTopBar(
                title = row?.folder?.name ?: "Folder",
                subtitle = row?.let { if (it.noteCount == 1) "1 note" else "${it.noteCount} notes" },
                leading = {
                    EchoIconButton(
                        iconRes = R.drawable.ic_chevron_left,
                        contentDescription = "Back",
                        onClick = onBack,
                    )
                },
                trailing = {
                    if (row != null) {
                        EchoIconButton(
                            iconRes = R.drawable.ic_pen_line,
                            contentDescription = "Edit folder",
                            onClick = { editing = true },
                        )
                    }
                },
            )

            if (groups.isEmpty()) {
                EmptyState(
                    title = "Nothing here yet",
                    body = "Open a note and use its folder button to move it here.",
                    modifier = Modifier.padding(bottom = 80.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(EchoTheme.spacing.gutterScreen),
                    verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
                ) {
                    groups.forEach { group ->
                        item(key = "header-${group.label}") {
                            NotesGroupHeader(label = group.label, count = group.items.size)
                        }
                        item(key = "group-${group.label}") {
                            NotesGroupCard(items = group.items, onOpen = onOpen)
                        }
                    }
                }
            }
        }

        FolderSheet(
            visible = editing && row != null,
            folder = row?.folder,
            onSave = { name, color, icon ->
                foldersViewModel.save(folderId, name, color, icon)
                editing = false
            },
            onDismiss = { editing = false },
            onDelete = {
                foldersViewModel.delete(folderId)
                editing = false
                onBack()
            },
        )
    }
}
