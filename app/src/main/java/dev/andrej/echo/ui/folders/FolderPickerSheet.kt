package dev.andrej.echo.ui.folders

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.andrej.echo.R
import dev.andrej.echo.data.Folder
import dev.andrej.echo.ui.components.PickerSheet
import dev.andrej.echo.ui.theme.EchoTheme

/** Picks a folder id, or null for "No folder". */
@Composable
internal fun FolderPickerSheet(
    visible: Boolean,
    title: String,
    folders: List<Folder>,
    selected: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    PickerSheet<String?>(
        visible = visible,
        title = title,
        options = listOf("No folder" to null) + folders.map { it.name to it.id },
        selected = selected,
        onPick = onPick,
        onDismiss = onDismiss,
        leading = { id ->
            val folder = folders.firstOrNull { it.id == id }
            Icon(
                painter = painterResource(folder?.let { FolderIcon.of(it.icon).drawable } ?: R.drawable.ic_folder),
                contentDescription = null,
                tint = folder?.let { folderColor(it.color) } ?: EchoTheme.colors.textTertiary,
                modifier = Modifier.size(20.dp),
            )
        },
    )
}
