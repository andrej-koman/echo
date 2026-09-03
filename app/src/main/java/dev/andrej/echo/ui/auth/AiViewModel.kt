package dev.andrej.echo.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.andrej.echo.ai.AiCardState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Download/delete run on an app-scoped coroutine owned by the container, not [viewModelScope] —
 * leaving AccountScreen mid-download must not cancel it.
 */
class AiViewModel(
    aiCardState: Flow<AiCardState>,
    val onDownloadModel: () -> Unit,
    val onCancelDownload: () -> Unit,
    val onDeleteModel: () -> Unit,
) : ViewModel() {

    val state: StateFlow<AiCardState> = aiCardState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiCardState.Checking,
    )
}
