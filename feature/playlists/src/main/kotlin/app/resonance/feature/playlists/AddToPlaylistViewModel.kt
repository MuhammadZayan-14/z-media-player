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

data class AddOutcome(val playlistName: String, val added: Int)

data class AddToPlaylistUiState(
    val playlists: List<Playlist> = emptyList(),
    val nameError: PlaylistNameResult? = null,
    /** Set once the songs were added; the dialog reports it and closes. */
    val outcome: AddOutcome? = null
)

@HiltViewModel
class AddToPlaylistViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val createPlaylist: CreatePlaylistUseCase
) : ViewModel() {
    private val nameError = MutableStateFlow<PlaylistNameResult?>(null)
    private val outcome = MutableStateFlow<AddOutcome?>(null)

    val uiState: StateFlow<AddToPlaylistUiState> =
        combine(playlists.playlists(), nameError, outcome) { list, error, done ->
            AddToPlaylistUiState(list, error, done)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddToPlaylistUiState())

    fun addTo(playlist: Playlist, mediaIds: List<Long>) {
        viewModelScope.launch {
            val added = playlists.addTracks(playlist.id, mediaIds)
            outcome.value = AddOutcome(playlist.name, added)
        }
    }

    fun createAndAdd(name: String, mediaIds: List<Long>) {
        viewModelScope.launch {
            when (val result = createPlaylist(name)) {
                is PlaylistNameResult.Saved -> {
                    val added = playlists.addTracks(result.playlistId, mediaIds)
                    outcome.value = AddOutcome(name.trim(), added)
                }

                else -> nameError.value = result
            }
        }
    }

    fun clearNameError() {
        nameError.value = null
    }

    /** Called after the outcome was shown, so a reused ViewModel starts clean. */
    fun consumeOutcome() {
        outcome.value = null
        nameError.value = null
    }
}
