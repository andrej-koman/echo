package dev.andrej.echo.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.R
import dev.andrej.echo.ui.components.BadgeTone
import dev.andrej.echo.ui.components.ButtonSize
import dev.andrej.echo.ui.components.ButtonVariant
import dev.andrej.echo.ui.components.EchoBadge
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.components.EmptyState
import dev.andrej.echo.ui.components.Mascot
import dev.andrej.echo.ui.components.MascotVariant
import dev.andrej.echo.ui.components.ThinkingDots
import dev.andrej.echo.ui.theme.EchoTheme

@Composable
fun NotesScreen(
    viewModel: NotesViewModel,
    onOpen: (String) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    var acting by remember { mutableStateOf<NoteRow?>(null) }
    val offeredImport by viewModel.offeredImport.collectAsStateWithLifecycle()
    // Non-null is selection mode, entered by long-pressing a note.
    var selection by rememberSaveable { mutableStateOf<Set<String>?>(null) }
    var bulk by remember { mutableStateOf<BulkAction?>(null) }
    LaunchedEffect(state.notes) {
        // A note deleted or filtered away stops counting as selected.
        selection = selection?.intersect(state.notes.map { it.id }.toSet())
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
            } else {
                EchoTopBar(
                    title = "Echo",
                    query = state.query,
                    onQueryChange = viewModel::search,
                    searchPlaceholder = "Search all notes…",
                    leading = { Mascot(size = 30.dp) },
                    actions = {
                        EchoIconButton(
                            iconRes = R.drawable.ic_upload,
                            contentDescription = "Import audio",
                            onClick = onImport,
                        )
                    },
                )
            }
            NotesBody(
                state = state,
                selection = selection,
                onOpen = { id ->
                    val current = selection
                    if (current == null) onOpen(id) else selection = current.toggle(id)
                },
                onSelect = { id -> selection = (selection ?: emptySet()).toggle(id) },
                onImport = onImport,
                onMore = { acting = it },
            )
        }

        NoteActionsSheets(
            note = acting,
            folders = folders,
            onMove = viewModel::moveToFolder,
            onDelete = viewModel::delete,
            onDismiss = { acting = null },
        )

        BulkActionSheets(
            action = bulk,
            notes = state.notes.filter { it.id in selection.orEmpty() },
            folders = folders,
            onMove = { folderId ->
                selection.orEmpty().forEach { viewModel.moveToFolder(it, folderId) }
                selection = null
            },
            onDelete = {
                selection.orEmpty().forEach(viewModel::delete)
                selection = null
            },
            onDismiss = { bulk = null },
        )

        ImportSheet(
            file = offeredImport,
            fixedDay = remember(offeredImport) { viewModel.importDay() },
            onImport = viewModel::acceptImport,
            onDismiss = { offeredImport?.let(viewModel::declineImport) },
        )
    }
}

@Composable
private fun NotesBody(
    state: NotesUiState,
    selection: Set<String>?,
    onOpen: (String) -> Unit,
    onSelect: (String) -> Unit,
    onImport: () -> Unit,
    onMore: (NoteRow) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (!state.loaded) {
            Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 150.dp),
                contentAlignment = Alignment.Center,
            ) {
                ThinkingDots()
            }
            return@Column
        }

        if (state.total == 0) {
            EmptyState(
                title = "No notes yet",
                body = "Tap the button below and say something, or import a recording you already have. " +
                    "Echo will clean it up and pull out your tasks.",
                variant = MascotVariant.Notes,
                modifier = Modifier.padding(bottom = 150.dp),
            ) {
                EchoButton(
                    text = "Import audio",
                    onClick = onImport,
                    variant = ButtonVariant.Secondary,
                    size = ButtonSize.Sm,
                    modifier = Modifier.padding(top = EchoTheme.spacing.s4),
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag(TAG_NOTES_LIST),
            contentPadding = PaddingValues(
                start = EchoTheme.spacing.gutterScreen,
                end = EchoTheme.spacing.gutterScreen,
                top = 14.dp,
                bottom = 150.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
        ) {
            if (state.notes.isEmpty()) {
                item {
                    Text(
                        text = "Nothing matches “${state.query.trim()}”.",
                        style = EchoTheme.typography.bodySm,
                        color = EchoTheme.colors.textTertiary,
                        modifier = Modifier.padding(top = EchoTheme.spacing.s7),
                    )
                }
            }
            items(state.notes, key = { it.id }) { row ->
                NoteCard(
                    row = row,
                    onOpen = { onOpen(row.id) },
                    onMore = { onMore(row) },
                    onLongClick = { onSelect(row.id) },
                    selected = selection?.let { row.id in it },
                )
            }
        }
    }
}

private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id

@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onMove: () -> Unit, onDelete: () -> Unit) {
    EchoTopBar(
        title = if (count == 1) "1 selected" else "$count selected",
        leading = {
            EchoIconButton(
                iconRes = R.drawable.ic_x,
                contentDescription = "Cancel selection",
                onClick = onClose,
                modifier = Modifier.size(30.dp),
            )
        },
        trailing = {
            if (count > 0) {
                EchoIconButton(iconRes = R.drawable.ic_folder, contentDescription = "Move to folder", onClick = onMove)
                EchoIconButton(
                    iconRes = R.drawable.ic_trash,
                    contentDescription = "Delete",
                    onClick = onDelete,
                    tint = EchoTheme.colors.textDanger,
                )
            }
        },
    )
}

@Composable
internal fun PendingBadge(pending: PendingCopy, modifier: Modifier = Modifier) {
    EchoBadge(
        text = pending.text,
        tone = if (pending.severity == PendingSeverity.Failed) BadgeTone.Warning else BadgeTone.Neutral,
        modifier = modifier,
    )
}

const val TAG_NOTES_LIST = "notes_list"
