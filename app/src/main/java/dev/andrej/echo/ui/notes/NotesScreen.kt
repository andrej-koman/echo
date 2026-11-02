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
import androidx.compose.runtime.Composable
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

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
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
            NotesBody(state = state, onOpen = onOpen, onImport = onImport, onMore = { acting = it })
        }

        NoteActionsSheets(
            note = acting,
            folders = folders,
            onMove = viewModel::moveToFolder,
            onDelete = viewModel::delete,
            onDismiss = { acting = null },
        )
    }
}

@Composable
private fun NotesBody(
    state: NotesUiState,
    onOpen: (String) -> Unit,
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
                NoteCard(row = row, onOpen = { onOpen(row.id) }, onMore = { onMore(row) })
            }
        }
    }
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
