package dev.andrej.echo.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.ui.history.HistoryViewModel
import dev.andrej.echo.ui.history.asDateTime

@Composable
fun TranscriptDetailScreen(
    transcriptId: String,
    viewModel: HistoryViewModel,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transcripts by viewModel.transcripts.collectAsStateWithLifecycle()
    val transcript = transcripts.firstOrNull { it.id == transcriptId }
    val context = LocalContext.current

    if (transcript == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Transcript not found.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "${transcript.createdAt.asDateTime()} · ${transcript.language}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = transcript.text,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .testTag(TAG_DETAIL_TEXT),
            style = MaterialTheme.typography.bodyLarge,
        )

        OutlinedButton(onClick = { context.copyToClipboard(transcript.text) }) {
            Text("Copy")
        }

        OutlinedButton(
            onClick = {
                viewModel.delete(transcript.id)
                onDeleted()
            },
        ) {
            Text("Delete")
        }
    }
}

private fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Echo transcript", text))
}

const val TAG_DETAIL_TEXT = "detail_text"
