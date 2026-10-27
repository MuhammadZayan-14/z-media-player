package app.resonance.feature.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.resonance.core.domain.repository.PlaylistRepository
import app.resonance.core.domain.usecase.CreatePlaylistUseCase
import app.resonance.core.domain.usecase.PlaylistNameResult
import app.resonance.core.model.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State of the "name a playlist" dialog; null when it is closed. */
data class NameEditorState(val error: PlaylistNameResult? = null)

data class PlaylistsUiState(
    val isLoading: Boolean = true,
    val playlists: List<Playlist> = emptyList(),
    val editor: NameEditorState? = null
)

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    playlists: PlaylistRepository,
    private val createPlaylist: CreatePlaylistUseCase
) : ViewModel() {
    private val editor = MutableStateFlow<NameEditorState?>(null)

    val uiState: StateFlow<PlaylistsUiState> = combine(playlists.playlists(), editor) { list, editorState ->
        PlaylistsUiState(isLoading = false, playlists = list, editor = editorState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistsUiState())

    fun openCreate() {
        editor.value = NameEditorState()
    }

    fun dismissCreate() {
        editor.value = null
    }

    fun create(name: String) {
        viewModelScope.launch {
            when (val result = createPlaylist(name)) {
                is PlaylistNameResult.Saved -> editor.value = null
                else -> editor.value = NameEditorState(result)
            }
        }
    }
}
