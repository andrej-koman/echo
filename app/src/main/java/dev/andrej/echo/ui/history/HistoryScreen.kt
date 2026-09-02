package dev.andrej.echo.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.R
import dev.andrej.echo.data.Transcript
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.components.EmptyState
import dev.andrej.echo.ui.theme.EchoTheme
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transcripts by viewModel.transcripts.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        EchoTopBar(
            title = "History",
            subtitle = "${transcripts.size} transcripts",
            leading = {
                EchoIconButton(
                    iconRes = R.drawable.ic_chevron_down,
                    contentDescription = "Back",
                    onClick = onBack,
                )
            },
        )

        if (transcripts.isEmpty()) {
            EmptyState(
                title = "No transcripts yet",
                body = "Everything you record shows up here.",
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag(TAG_HISTORY_LIST),
            contentPadding = PaddingValues(
                start = EchoTheme.spacing.gutterScreen,
                end = EchoTheme.spacing.gutterScreen,
                top = 14.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.gapList),
        ) {
            items(transcripts, key = { it.id }) { transcript ->
                TranscriptRow(
                    transcript = transcript,
                    onOpen = { onOpen(transcript.id) },
                    onDelete = { viewModel.delete(transcript.id) },
                )
            }
        }

        Box(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun TranscriptRow(
    transcript: Transcript,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    EchoCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Text(
            text = transcript.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = EchoTheme.typography.bodySm,
            color = EchoTheme.colors.textPrimary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${transcript.createdAt.asDateTime()} · ${transcript.language}",
                style = EchoTheme.typography.monoMicro,
                color = EchoTheme.colors.textTertiary,
            )
            EchoIconButton(
                iconRes = R.drawable.ic_trash,
                contentDescription = "Delete",
                onClick = onDelete,
            )
        }
    }
}

internal fun Long.asDateTime(): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(this))

const val TAG_HISTORY_LIST = "history_list"
