package app.resonance.feature.playlists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.resonance.core.domain.playback.PlaybackController
import app.resonance.core.domain.repository.FavoritesRepository
import app.resonance.core.domain.repository.HistoryRepository
import app.resonance.core.domain.repository.MediaLibraryRepository
import app.resonance.core.domain.repository.PlaylistRepository
import app.resonance.core.domain.usecase.PlaylistNameResult
import app.resonance.core.domain.usecase.RenamePlaylistUseCase
import app.resonance.core.domain.usecase.TrackCommands
import app.resonance.core.model.MediaFile
import app.resonance.core.model.SmartCollection
import app.resonance.core.ui.TrackCommandHandler
import app.resonance.feature.playlists.navigation.PlaylistDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PlaylistDetailUiState {
    data object Loading : PlaylistDetailUiState

    /** The playlist was deleted (here or elsewhere); the screen should close. */
    data object Gone : PlaylistDetailUiState

    data class Ready(
        /** User playlist name; null for smart collections, whose title comes from resources. */
        val name: String?,
        val smart: SmartCollection?,
        val tracks: List<MediaFile>,
        val favoriteIds: Set<Long>,
        val currentMediaId: Long?,
        val editor: NameEditorState?
    ) : PlaylistDetailUiState {
        val isUserPlaylist: Boolean get() = smart == null
    }
}

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlists: PlaylistRepository,
    favorites: FavoritesRepository,
    history: HistoryRepository,
    library: MediaLibraryRepository,
    controller: PlaybackController,
    private val commands: TrackCommands,
    private val renamePlaylist: RenamePlaylistUseCase
) : ViewModel(), TrackCommandHandler {

    private val route = savedStateHandle.toRoute<PlaylistDetailRoute>()
    private val smart: SmartCollection? = route.smart?.let { name ->
        SmartCollection.entries.firstOrNull {
            it.name ==
                name
        }
    }
    private val playlistId: Long = route.playlistId
    private val editor = MutableStateFlow<NameEditorState?>(null)

    private val name: Flow<String?> = if (smart != null) {
        flowOf("")
    } else {
        playlists.playlist(playlistId).map { it?.name }
    }

    private val tracks: Flow<List<MediaFile>> = when (smart) {
        SmartCollection.FAVORITES -> favorites.favorites()
        SmartCollection.RECENTLY_PLAYED -> history.recentlyPlayed(SMART_LIMIT)
        SmartCollection.MOST_PLAYED -> history.mostPlayed(SMART_LIMIT)
        SmartCollection.RECENTLY_ADDED -> library.recentlyAdded(SMART_LIMIT)
        null -> playlists.tracks(playlistId)
    }

    val uiState: StateFlow<PlaylistDetailUiState> = combine(
        name,
        tracks,
        favorites.favoriteIds(),
        controller.state.map { it.current?.libraryId }.distinctUntilChanged(),
        editor
    ) { name, tracks, favoriteIds, currentId, editorState ->
        if (name == null) {
            PlaylistDetailUiState.Gone
        } else {
            PlaylistDetailUiState.Ready(
                name = name.takeIf { smart == null },
                smart = smart,
                tracks = tracks,
                favoriteIds = favoriteIds,
                currentMediaId = currentId,
                editor = editorState
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistDetailUiState.Loading)

    private val readyTracks: List<MediaFile>
        get() = (uiState.value as? PlaylistDetailUiState.Ready)?.tracks.orEmpty()

    fun play(index: Int) = commands.playFrom(readyTracks, index)

    fun shuffle() = commands.shuffle(readyTracks)

    override fun playNext(track: MediaFile) = commands.playNext(track)

    override fun addToQueue(track: MediaFile) = commands.addToQueue(track)

    override fun toggleFavorite(track: MediaFile) {
        viewModelScope.launch { commands.toggleFavorite(track) }
    }

    fun remove(track: MediaFile) {
        if (smart != null) return
        viewModelScope.launch { playlists.removeTrack(playlistId, track.id) }
    }

    fun move(track: MediaFile, offset: Int) {
        if (smart != null) return
        val from = readyTracks.indexOfFirst { it.id == track.id }
        val to = from + offset
        if (from < 0 || to !in readyTracks.indices) return
        viewModelScope.launch { playlists.moveTrack(playlistId, from, to) }
    }

    fun openRename() {
        editor.value = NameEditorState()
    }

    fun dismissRename() {
        editor.value = null
    }

    fun rename(newName: String) {
        viewModelScope.launch {
            when (val result = renamePlaylist(playlistId, newName)) {
                is PlaylistNameResult.Saved -> editor.value = null
                else -> editor.value = NameEditorState(result)
            }
        }
    }

    fun delete() {
        viewModelScope.launch { playlists.delete(playlistId) }
    }

    private companion object {
        const val SMART_LIMIT = 100
    }
}
