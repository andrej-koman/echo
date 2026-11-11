package dev.andrej.echo.ui.notes

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.data.Folder
import dev.andrej.echo.ui.components.CardPadding
import dev.andrej.echo.ui.components.CardTone
import dev.andrej.echo.ui.components.ConfirmSheet
import dev.andrej.echo.ui.components.EchoCard
import dev.andrej.echo.ui.components.EchoIconButton
import dev.andrej.echo.ui.components.IconButtonSize
import dev.andrej.echo.ui.components.ActionDivider
import dev.andrej.echo.ui.components.ActionRow
import dev.andrej.echo.ui.components.ActionSheet
import dev.andrej.echo.ui.detail.RenameNoteSheet
import dev.andrej.echo.ui.folders.FolderIcon
import dev.andrej.echo.ui.folders.FolderPickerSheet
import dev.andrej.echo.ui.folders.folderColor
import dev.andrej.echo.ui.theme.EchoTheme
import dev.andrej.echo.ui.theme.LocalReducedMotion

@Composable
internal fun NoteCard(
    row: NoteRow,
    onOpen: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    showFolder: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean? = null,
) {
    EchoCard(
        modifier = modifier.fillMaxWidth(),
        tone = if (selected == true) CardTone.Bronze else CardTone.Paper,
        padding = CardPadding.None,
        onClick = onOpen,
        onLongClick = onLongClick,
    ) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(start = EchoTheme.spacing.s5, top = EchoTheme.spacing.s5, bottom = EchoTheme.spacing.s5),
        ) {
            ProgressStripe(stage = row.stage, modifier = Modifier.width(3.dp).fillMaxHeight())
            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.padding(start = EchoTheme.spacing.s5),
                    verticalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
                ) {
                    Text(
                        text = row.title,
                        style = EchoTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = EchoTheme.colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = IconButtonSize.Sm.box + EchoTheme.spacing.s2 + EchoTheme.spacing.s4),
                    )
                    Text(
                        text = row.excerpt,
                        style = EchoTheme.typography.bodySm,
                        color = EchoTheme.colors.textSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = EchoTheme.spacing.s5),
                    )
                    Meta(row = row, showFolder = showFolder)
                }
                // Overlaid so the 32dp touch target doesn't push the excerpt away from the title;
                // the ellipsis glyph sits low in its square box, so lift it to match the end inset.
                val cornerModifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = -EchoTheme.spacing.s4)
                    .padding(start = EchoTheme.spacing.s2, end = EchoTheme.spacing.s4)
                if (selected == null) {
                    EchoIconButton(
                        iconRes = R.drawable.ic_ellipsis,
                        contentDescription = "More",
                        tint = EchoTheme.colors.textTertiary,
                        onClick = onMore,
                        modifier = cornerModifier,
                    )
                } else {
                    SelectionCheck(selected, cornerModifier)
                }
            }
        }
    }
}

@Composable
private fun SelectionCheck(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = EchoTheme.colors
    Box(modifier = modifier.size(IconButtonSize.Sm.box), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .then(
                    if (selected) Modifier.background(colors.textAccent)
                    else Modifier.border(1.5.dp, colors.borderStrong, CircleShape),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = "Selected",
                    tint = colors.surfaceCard,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun Meta(row: NoteRow, showFolder: Boolean) {
    Row(
        modifier = Modifier.padding(end = EchoTheme.spacing.s5),
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${row.date} · ${row.duration}",
            style = EchoTheme.typography.micro,
            color = EchoTheme.colors.textTertiary,
            maxLines = 1,
        )
        if (row.totalTasks > 0) TaskCount(row)
        val folder = row.folder
        when {
            row.pending != null -> PendingBadge(row.pending, modifier = Modifier.weight(1f, fill = false))
            showFolder && folder != null -> FolderLabel(folder, modifier = Modifier.weight(1f, fill = false))
        }
    }
}

/** Open tasks while any remain; once all are ticked off, how many were done, muted. */
@Composable
private fun TaskCount(row: NoteRow) {
    val open = row.openTasks > 0
    val color = if (open) EchoTheme.colors.textSecondary else EchoTheme.colors.textTertiary
    Row(
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "·", style = EchoTheme.typography.micro, color = EchoTheme.colors.textTertiary)
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = when {
                !open -> "${row.totalTasks} done"
                row.openTasks == 1 -> "1 task"
                else -> "${row.openTasks} tasks"
            },
            style = EchoTheme.typography.micro.copy(fontWeight = FontWeight.Medium),
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun FolderLabel(folder: Folder, modifier: Modifier = Modifier) {
    val color = folderColor(folder.color)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(EchoTheme.spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "·", style = EchoTheme.typography.micro, color = EchoTheme.colors.textTertiary)
        Icon(
            painter = painterResource(FolderIcon.of(folder.icon).drawable),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = folder.name,
            style = EchoTheme.typography.micro.copy(fontWeight = FontWeight.Medium),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Fills top to bottom while transcribing, pulses while analyzing, solid once done. */
@Composable
private fun ProgressStripe(stage: NoteStage, modifier: Modifier = Modifier) {
    val colors = EchoTheme.colors
    val fraction by animateFloatAsState(
        targetValue = when (stage) {
            NoteStage.Waiting -> 0f
            is NoteStage.Transcribing -> stage.fraction.coerceIn(0f, 1f)
            NoteStage.Analyzing, NoteStage.Failed, NoteStage.Done -> 1f
        },
        label = "stripeFill",
    )
    val pulse by rememberInfiniteTransition(label = "stripePulse").animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "stripeAlpha",
    )
    val pulsing = stage == NoteStage.Analyzing && !LocalReducedMotion.current

    Box(
        modifier = modifier
            .clip(EchoTheme.radii.pill)
            .background(colors.borderDefault),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction)
                .alpha(if (pulsing) pulse else 1f)
                .clip(EchoTheme.radii.pill)
                .background(if (stage == NoteStage.Failed) colors.statusWarningFg else colors.textAccent),
        )
    }
}

/** The card's ··· menu: move to a folder, or delete behind a confirmation. */
@Composable
internal fun NoteActionsSheets(
    note: NoteRow?,
    folders: List<Folder>,
    analyzingId: String?,
    onMove: (noteId: String, folderId: String?) -> Unit,
    onRename: (noteId: String, title: String) -> Unit,
    onCopy: (noteId: String) -> Unit,
    onAnalyze: (noteId: String) -> Unit,
    onDelete: (noteId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember(note?.id) { mutableStateOf(NoteAction.Menu) }
    val canAnalyze = note?.pending?.severity != PendingSeverity.Parked
    val analyzing = note != null && analyzingId == note.id
    ActionSheet(
        visible = note != null && mode == NoteAction.Menu,
        title = note?.title.orEmpty(),
        subtitle = note?.let { "${it.date} · ${it.duration}" },
        onDismiss = onDismiss,
    ) {
        ActionRow(
            icon = R.drawable.ic_pen_line,
            label = "Rename",
            onClick = { mode = NoteAction.Rename },
        )
        ActionRow(
            icon = R.drawable.ic_folder,
            label = "Move to folder",
            trailing = note?.folder?.name ?: "No folder",
            onClick = { mode = NoteAction.Move },
        )
        ActionRow(
            icon = R.drawable.ic_copy,
            label = "Copy text",
            description = "Copies the full transcript",
            onClick = {
                note?.let { onCopy(it.id) }
                onDismiss()
            },
        )
        ActionRow(
            icon = R.drawable.ic_sparkles,
            label = if (analyzing) "Analyzing…" else "Redo summary and tasks",
            description = when {
                analyzing -> "Echo is reading this note now"
                !canAnalyze -> "Available once transcription finishes"
                else -> "Replaces the current ones"
            },
            enabled = canAnalyze && !analyzing,
            onClick = {
                note?.let { onAnalyze(it.id) }
                onDismiss()
            },
        )
        ActionDivider()
        ActionRow(
            icon = R.drawable.ic_trash,
            label = "Delete note",
            destructive = true,
            onClick = { mode = NoteAction.Delete },
        )
    }
    RenameNoteSheet(
        visible = note != null && mode == NoteAction.Rename,
        title = note?.title.orEmpty(),
        onSave = { title ->
            note?.let { onRename(it.id, title) }
            onDismiss()
        },
        onDismiss = onDismiss,
    )
    FolderPickerSheet(
        visible = note != null && mode == NoteAction.Move,
        title = "Move to folder",
        folders = folders,
        selected = note?.folder?.id,
        onPick = { folderId ->
            note?.let { onMove(it.id, folderId) }
            onDismiss()
        },
        onDismiss = onDismiss,
    )
    ConfirmSheet(
        visible = note != null && mode == NoteAction.Delete,
        title = "Delete this note?",
        message = "The recording, transcript and its tasks are removed. This can't be undone.",
        confirmLabel = "Delete note",
        onConfirm = {
            note?.let { onDelete(it.id) }
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

private enum class NoteAction { Menu, Rename, Move, Delete }

internal enum class BulkAction { Move, Delete }

@Composable
internal fun BulkActionSheets(
    action: BulkAction?,
    notes: List<NoteRow>,
    folders: List<Folder>,
    onMove: (folderId: String?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val count = if (notes.size == 1) "1 note" else "${notes.size} notes"
    val places = notes.map { it.folder?.id }.distinct()
    FolderPickerSheet(
        visible = action == BulkAction.Move,
        title = "Move $count to",
        folders = folders,
        // Checked only when every note is already in the same place.
        selected = if (places.size == 1) places.single() else MIXED,
        onPick = { folderId ->
            onMove(folderId)
            onDismiss()
        },
        onDismiss = onDismiss,
    )
    ConfirmSheet(
        visible = action == BulkAction.Delete,
        title = "Delete $count?",
        message = "Their recordings, transcripts and tasks are removed. This can't be undone.",
        confirmLabel = "Delete $count",
        onConfirm = {
            onDelete()
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

private const val MIXED = "\u0000mixed"
