package dev.andrej.echo.ui.transcripts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.R
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.components.EmptyState
import dev.andrej.echo.ui.components.Mascot
import dev.andrej.echo.ui.components.MascotVariant
import dev.andrej.echo.ui.components.ThinkingDots
import dev.andrej.echo.ui.theme.EchoTheme

/** The same rows as the Transcripts archive, titled by the analysis rather than raw text. */
@Composable
fun NotesScreen(
    viewModel: TranscriptsViewModel,
    onOpen: (String) -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notes = state.groups.flatMap { it.items }

    Column(modifier = modifier.fillMaxSize()) {
        EchoTopBar(
            title = "Notes",
            leading = { Mascot(size = 30.dp, variant = MascotVariant.Notes) },
            trailing = {
                EchoIconButton(
                    iconRes = R.drawable.ic_user,
                    contentDescription = "Account",
                    onClick = onOpenAccount,
                )
            },
        )

        if (!state.loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ThinkingDots() }
            return@Column
        }

        if (notes.isEmpty()) {
            EmptyState(
                title = "No notes yet",
                body = "Record something and Echo will pull out a title and summary.",
                variant = MascotVariant.Notes,
                modifier = Modifier.padding(bottom = 150.dp),
            )
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
            items(notes, key = { it.id }) { row ->
                TranscriptRow(row = row, onOpen = { onOpen(row.id) })
            }
        }
    }
}
