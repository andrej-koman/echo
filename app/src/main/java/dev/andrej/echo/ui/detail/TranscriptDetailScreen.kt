package dev.andrej.echo.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.R
import dev.andrej.echo.ui.components.ButtonVariant
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.history.HistoryViewModel
import dev.andrej.echo.ui.history.asDateTime
import dev.andrej.echo.ui.theme.EchoTheme

@Composable
fun TranscriptDetailScreen(
    transcriptId: String,
    viewModel: HistoryViewModel,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transcripts by viewModel.transcripts.collectAsStateWithLifecycle()
    val transcript = transcripts.firstOrNull { it.id == transcriptId }
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize()) {
        EchoTopBar(
            title = "Transcript",
            subtitle = transcript?.let { "${it.createdAt.asDateTime()} · ${it.language}" },
            leading = {
                EchoIconButton(
                    iconRes = R.drawable.ic_chevron_down,
                    contentDescription = "Back",
                    onClick = onBack,
                )
            },
        )

        if (transcript == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Transcript not found.",
                    style = EchoTheme.typography.body,
                    color = EchoTheme.colors.textTertiary,
                )
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(EchoTheme.spacing.gutterScreen)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = transcript.text,
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .testTag(TAG_DETAIL_TEXT),
                style = EchoTheme.typography.body,
                color = EchoTheme.colors.textPrimary,
            )

            EchoButton(
                text = "Copy",
                onClick = { context.copyToClipboard(transcript.text) },
                variant = ButtonVariant.Secondary,
                fullWidth = true,
                modifier = Modifier.fillMaxWidth(),
            )

            EchoButton(
                text = "Delete",
                onClick = {
                    viewModel.delete(transcript.id)
                    onDeleted()
                },
                variant = ButtonVariant.Danger,
                fullWidth = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Echo transcript", text))
}

const val TAG_DETAIL_TEXT = "detail_text"
