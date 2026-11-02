package dev.andrej.echo.ui.folders

import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.andrej.echo.R
import dev.andrej.echo.data.Folder
import dev.andrej.echo.data.FolderRepository
import dev.andrej.echo.data.Transcript
import dev.andrej.echo.data.TranscriptRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Stored by name, so reordering or adding icons never relabels a folder. */
enum class FolderIcon(@DrawableRes val drawable: Int) {
    Folder(R.drawable.ic_folder),
    Work(R.drawable.ic_cpu),
    Ideas(R.drawable.ic_sparkles),
    Lists(R.drawable.ic_list_checks),
    Meetings(R.drawable.ic_calendar),
    People(R.drawable.ic_user),
    Travel(R.drawable.ic_plane),
    Private(R.drawable.ic_lock),
    ;

    companion object {
        fun of(name: String): FolderIcon = entries.firstOrNull { it.name == name } ?: Folder
    }
}

data class FolderRow(val folder: Folder, val noteCount: Int)

data class FoldersUiState(
    val folders: List<FolderRow> = emptyList(),
    val unfiled: Int = 0,
    val loaded: Boolean = false,
)

class FoldersViewModel(
    private val repository: FolderRepository,
    transcripts: TranscriptRepository,
) : ViewModel() {

    val state: StateFlow<FoldersUiState> =
        combine(repository.folders, transcripts.transcripts) { folders, all -> foldersState(folders, all) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoldersUiState())

    /** A new folder without [id]. A blank name is ignored. */
    fun save(id: String?, name: String, color: Int, icon: FolderIcon) {
        if (name.isBlank()) return
        viewModelScope.launch {
            if (id == null) {
                repository.create(name, color, icon.name)
            } else {
                repository.update(Folder(id, name, color, icon.name))
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}

internal fun foldersState(folders: List<Folder>, transcripts: List<Transcript>): FoldersUiState {
    val counts = transcripts.groupingBy { it.folderId }.eachCount()
    return FoldersUiState(
        folders = folders.map { FolderRow(it, counts[it.id] ?: 0) },
        unfiled = transcripts.count { it.folderId == null },
        loaded = true,
    )
}
