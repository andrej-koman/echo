package dev.andrej.echo.ui.folders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import dev.andrej.echo.ui.notes.BulkAction
import dev.andrej.echo.ui.notes.BulkActionSheets
import dev.andrej.echo.ui.notes.NoteActionsSheets
import dev.andrej.echo.ui.notes.SelectionBar
import dev.andrej.echo.ui.notes.toggle
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import dev.andrej.echo.ui.notes.NoteCard
import dev.andrej.echo.ui.notes.NoteRow
import dev.andrej.echo.ui.notes.NotesViewModel
import dev.andrej.echo.ui.theme.EchoTheme
import androidx.compose.ui.platform.LocalContext
import dev.andrej.echo.ui.detail.copyToClipboard

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
    val notes by remember(folderId) { notesViewModel.notesIn(folderId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val folders by notesViewModel.folders.collectAsStateWithLifecycle()
    var acting by remember { mutableStateOf<NoteRow?>(null) }
    val row = state.folders.firstOrNull { it.folder.id == folderId }
    var editing by remember { mutableStateOf(false) }
    // Non-null is selection mode, entered by long-pressing a note.
    var selection by rememberSaveable { mutableStateOf<Set<String>?>(null) }
    var bulk by remember { mutableStateOf<BulkAction?>(null) }
    LaunchedEffect(notes) {
        // A note deleted or moved out of this folder stops counting as selected.
        selection = selection?.intersect(notes.map { it.id }.toSet())
    }
    BackHandler(enabled = selection != null) { selection = null }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            val selected = selection
            if (selected != null) {
                SelectionBar(
                    count = selected.size,
                    onClose = { selection = null },
                    onMove = { bulk = BulkAction.Move },
                    onDelete = { bulk = BulkAction.Delete },
                )
            } else EchoTopBar(
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

            if (notes.isEmpty()) {
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
                    items(notes, key = { it.id }) { note ->
                        NoteCard(
                            row = note,
                            onOpen = {
                                val current = selection
                                if (current == null) onOpen(note.id) else selection = current.toggle(note.id)
                            },
                            onMore = { acting = note },
                            showFolder = false,
                            onLongClick = { selection = (selection ?: emptySet()).toggle(note.id) },
                            selected = selection?.let { note.id in it },
                        )
                    }
                }
            }
        }

        val analyzingId by notesViewModel.analyzingId.collectAsStateWithLifecycle()
        val context = LocalContext.current
        NoteActionsSheets(
            note = acting,
            folders = folders,
            analyzingId = analyzingId,
            onMove = notesViewModel::moveToFolder,
            onRename = notesViewModel::rename,
            onCopy = { id -> notesViewModel.transcriptText(id) { context.copyToClipboard(it) } },
            onAnalyze = notesViewModel::analyze,
            onDelete = notesViewModel::delete,
            onDismiss = { acting = null },
        )

        BulkActionSheets(
            action = bulk,
            notes = notes.filter { it.id in selection.orEmpty() },
            folders = folders,
            onMove = { folderId ->
                selection.orEmpty().forEach { notesViewModel.moveToFolder(it, folderId) }
                selection = null
            },
            onDelete = {
                selection.orEmpty().forEach(notesViewModel::delete)
                selection = null
            },
            onDismiss = { bulk = null },
        )

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
