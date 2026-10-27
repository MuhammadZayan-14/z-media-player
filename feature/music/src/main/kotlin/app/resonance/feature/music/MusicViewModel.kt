package app.resonance.feature.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.resonance.core.domain.playback.PlaybackController
import app.resonance.core.domain.repository.FavoritesRepository
import app.resonance.core.domain.repository.MediaLibraryRepository
import app.resonance.core.domain.repository.UserPreferencesRepository
import app.resonance.core.domain.usecase.TrackCommands
import app.resonance.core.model.Album
import app.resonance.core.model.Artist
import app.resonance.core.model.Folder
import app.resonance.core.model.Genre
import app.resonance.core.model.LibraryScanState
import app.resonance.core.model.MediaFile
import app.resonance.core.model.SongSort
import app.resonance.core.ui.TrackCommandHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryContent(
    val songs: List<MediaFile> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val folders: List<Folder> = emptyList()
)

data class MusicUiState(
    val isLoading: Boolean = true,
    val content: LibraryContent = LibraryContent(),
    val sort: SongSort = SongSort.TITLE,
    val favoriteIds: Set<Long> = emptySet(),
    val currentMediaId: Long? = null,
    val scanState: LibraryScanState = LibraryScanState.Idle
) {
    val isRefreshing: Boolean get() = scanState is LibraryScanState.Scanning
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MusicViewModel @Inject constructor(
    private val library: MediaLibraryRepository,
    private val preferences: UserPreferencesRepository,
    favorites: FavoritesRepository,
    private val controller: PlaybackController,
    private val commands: TrackCommands
) : ViewModel(), TrackCommandHandler {

    private val sort = preferences.preferences.map { it.songSort }.distinctUntilChanged()

    private val content = combine(
        sort.flatMapLatest { library.songs(it) },
        library.albums(),
        library.artists(),
        library.genres(),
        library.folders()
    ) { songs, albums, artists, genres, folders -> LibraryContent(songs, albums, artists, genres, folders) }

    val uiState: StateFlow<MusicUiState> = combine(
        content,
        sort,
        favorites.favoriteIds(),
        controller.state.map { it.current?.libraryId }.distinctUntilChanged(),
        library.scanState
    ) { content, sort, favoriteIds, currentId, scan ->
        MusicUiState(
            isLoading = false,
            content = content,
            sort = sort,
            favoriteIds = favoriteIds,
            currentMediaId = currentId,
            scanState = scan
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MusicUiState())

    private val songs: List<MediaFile> get() = uiState.value.content.songs

    fun playSong(index: Int) = commands.playFrom(songs, index)

    fun shuffleAll() = commands.shuffle(songs)

    fun setSort(sort: SongSort) {
        viewModelScope.launch { preferences.setSongSort(sort) }
    }

    fun refresh() {
        viewModelScope.launch { library.refresh(full = true) }
    }

    /** Called when access was granted or changed, so the library reflects it immediately. */
    fun onAccessChanged() {
        viewModelScope.launch { library.refresh(full = false) }
    }

    fun playFile(uri: String, title: String) = controller.playUri(uri, title)

    override fun playNext(track: MediaFile) = commands.playNext(track)

    override fun addToQueue(track: MediaFile) = commands.addToQueue(track)

    override fun toggleFavorite(track: MediaFile) {
        viewModelScope.launch { commands.toggleFavorite(track) }
    }
}
