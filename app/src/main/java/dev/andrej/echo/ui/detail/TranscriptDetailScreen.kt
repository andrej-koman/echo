package dev.andrej.echo.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.transcripts.TranscriptsViewModel
import dev.andrej.echo.ui.transcripts.clock
import dev.andrej.echo.ui.transcripts.title
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TranscriptDetailScreen(
    transcriptId: String,
    viewModel: TranscriptsViewModel,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transcripts by viewModel.transcripts.collectAsStateWithLifecycle()
    val analyzingId by viewModel.analyzingId.collectAsStateWithLifecycle()
    val transcript = transcripts.firstOrNull { it.id == transcriptId }
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize()) {
        EchoTopBar(
            title = transcript?.let { it.title ?: title(it.text) } ?: "Transcript",
            subtitle = transcript?.let {
                "${detailDate.format(Date(it.createdAt))} · ${clock(it.durationMs)}"
            },
            leading = {
                EchoIconButton(
                    iconRes = R.drawable.ic_chevron_left,
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
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "TRANSCRIPT",
                    style = EchoTheme.typography.microCaps,
                    color = EchoTheme.colors.textTertiary,
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = EchoTheme.colors.borderSubtle,
                )
                Text(
                    text = "${wordCount(transcript.text)} words · ${transcript.language}",
                    style = EchoTheme.typography.micro,
                    color = EchoTheme.colors.textTertiary,
                )
            }

            EchoCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Text(
                    text = transcript.text,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .testTag(TAG_DETAIL_TEXT),
                    style = EchoTheme.typography.body,
                    color = EchoTheme.colors.textPrimary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4),
            ) {
                EchoButton(
                    text = "Copy",
                    onClick = { context.copyToClipboard(transcript.text) },
                    variant = ButtonVariant.Secondary,
                    modifier = Modifier.weight(1f),
                )

                EchoButton(
                    text = "Analyze",
                    onClick = { viewModel.analyze(transcript.id) },
                    variant = ButtonVariant.Secondary,
                    loading = analyzingId == transcript.id,
                    enabled = analyzingId == null,
                    modifier = Modifier.weight(1f),
                )
            }

            EchoButton(
                text = "Delete",
                onClick = {
                    viewModel.delete(transcript.id)
                    onDeleted()
                },
                variant = ButtonVariant.Danger,
                fullWidth = true,
            )

            Box(Modifier.height(EchoTheme.spacing.s2))
        }
    }
}

private val detailDate = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())

private fun wordCount(text: String) = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }

private fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Echo transcript", text))
}

const val TAG_DETAIL_TEXT = "detail_text"
