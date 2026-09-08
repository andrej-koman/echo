package dev.andrej.echo.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.andrej.echo.R
import dev.andrej.echo.ui.components.ButtonVariant
import dev.andrej.echo.ui.components.ButtonSize
import dev.andrej.echo.ui.components.EchoButton
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.EchoTopBar
import dev.andrej.echo.ui.components.IconButtonVariant
import dev.andrej.echo.ui.tasks.TaskGroupCard
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.transcripts.PendingCopy
import dev.andrej.echo.ui.transcripts.PendingSeverity
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
    onEditTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val transcripts by viewModel.transcripts.collectAsStateWithLifecycle()
    val analyzingId by viewModel.analyzingId.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val transcript = transcripts.firstOrNull { it.id == transcriptId }
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize()) {
        val entryPending = pending[transcriptId]
        val analyzing = analyzingId == transcriptId

        EchoTopBar(
            title = transcript?.let { it.title ?: title(it.text) } ?: "Transcript",
            subtitle = transcript?.let {
                "${detailDate.format(Date(it.createdAt))} · ${clock(it.durationMs)} · ${wordCount(it.text)} words"
            },
            leading = {
                EchoIconButton(
                    iconRes = R.drawable.ic_chevron_left,
                    contentDescription = "Back",
                    onClick = onBack,
                )
            },
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    EchoIconButton(
                        iconRes = R.drawable.ic_trash,
                        contentDescription = "Delete",
                        tint = EchoTheme.colors.textDanger,
                        onClick = {
                            viewModel.delete(transcriptId)
                            onDeleted()
                        },
                    )
                    EchoIconButton(
                        iconRes = R.drawable.ic_audio_waveform,
                        contentDescription = "Re-analyze",
                        variant = if (entryPending?.severity == PendingSeverity.Parked) {
                            IconButtonVariant.Ghost
                        } else {
                            IconButtonVariant.Soft
                        },
                        tint = if (entryPending?.severity == PendingSeverity.Parked) {
                            EchoTheme.colors.textTertiary
                        } else {
                            null
                        },
                        onClick = {
                            if (!analyzing && entryPending?.severity != PendingSeverity.Parked) {
                                viewModel.analyze(transcriptId)
                            }
                        },
                    )
                }
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
                .verticalScroll(rememberScrollState())
                .padding(EchoTheme.spacing.gutterScreen)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s5),
        ) {
            if (entryPending != null) {
                AnalysisStatusCard(
                    pending = entryPending,
                    retrying = analyzing,
                    onRetry = { viewModel.analyze(transcriptId) },
                )
            } else {
                transcript.summary?.let { summary ->
                    SectionCaption(label = "NOTE")
                    Text(
                        text = summary,
                        style = EchoTheme.typography.body,
                        color = EchoTheme.colors.textPrimary,
                    )
                }

                val notesTasks = tasks.filter { it.sourceTranscriptId == transcriptId }
                if (notesTasks.isNotEmpty()) {
                    SectionCaption(label = "TASKS FROM THIS NOTE", trailingCount = notesTasks.size)
                    TaskGroupCard(
                        items = notesTasks,
                        showDate = false,
                        sourceTitleFor = { null },
                        onToggle = { viewModel.setTaskDone(it.id, !it.done) },
                        onEdit = { onEditTask(it.id) },
                    )
                }
            }

            SectionCaption(label = "WHAT WAS SAID", trailingText = transcript.language)

            RawTranscriptCard(
                text = transcript.text,
                onCopy = { context.copyToClipboard(transcript.text) },
            )
        }
    }
}

@Composable
private fun SectionCaption(
    label: String,
    trailingText: String? = null,
    trailingCount: Int? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = EchoTheme.typography.microCaps,
            color = EchoTheme.colors.textTertiary,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = EchoTheme.colors.borderSubtle,
        )
        if (trailingText != null) {
            Text(
                text = trailingText,
                style = EchoTheme.typography.micro,
                color = EchoTheme.colors.textTertiary,
            )
        }
        if (trailingCount != null) {
            Text(
                text = trailingCount.toString(),
                style = EchoTheme.typography.monoCaption,
                color = EchoTheme.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun AnalysisStatusCard(
    pending: PendingCopy,
    retrying: Boolean,
    onRetry: () -> Unit,
) {
    val failed = pending.severity == PendingSeverity.Failed
    val iconBg = if (failed) EchoTheme.colors.statusWarningBg else EchoTheme.colors.statusNeutralBg
    val iconFg = if (failed) EchoTheme.colors.statusWarningFg else EchoTheme.colors.textSecondary

    EchoCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s4)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(EchoTheme.radii.pill)
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(if (failed) R.drawable.ic_x else R.drawable.ic_clock),
                    contentDescription = null,
                    tint = iconFg,
                    modifier = Modifier.size(17.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = pending.text,
                    style = EchoTheme.typography.micro.copy(fontWeight = FontWeight.Medium),
                    color = iconFg,
                )
                Text(
                    text = if (failed) {
                        "The recording is safe — retry when the phone is idle, or leave it as a plain recording."
                    } else {
                        "Echo keeps recording either way. The note and its tasks appear here as soon as the model finishes."
                    },
                    style = EchoTheme.typography.micro,
                    color = EchoTheme.colors.textSecondary,
                )
                if (failed) {
                    EchoButton(
                        text = if (retrying) "Analyzing…" else "Try again",
                        onClick = onRetry,
                        variant = ButtonVariant.Soft,
                        size = ButtonSize.Sm,
                        loading = retrying,
                    )
                }
            }
        }
    }
}

@Composable
private fun RawTranscriptCard(text: String, onCopy: () -> Unit) {
    var expanded by remember(text) { mutableStateOf(false) }

    EchoCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = EchoTheme.typography.body,
            color = EchoTheme.colors.textPrimary,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(TAG_DETAIL_TEXT),
        )
        Row(
            modifier = Modifier.padding(top = EchoTheme.spacing.s4),
            horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
        ) {
            EchoButton(
                text = if (expanded) "Show less" else "Show all",
                onClick = { expanded = !expanded },
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Sm,
            )
            EchoButton(
                text = "Copy",
                onClick = onCopy,
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Sm,
            )
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
