package app.resonance.feature.music

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.resonance.core.domain.playback.PlaybackController
import app.resonance.core.domain.repository.FavoritesRepository
import app.resonance.core.domain.repository.MediaLibraryRepository
import app.resonance.core.domain.usecase.TrackCommands
import app.resonance.core.model.Album
import app.resonance.core.model.MediaCollection
import app.resonance.core.model.MediaFile
import app.resonance.core.ui.TrackCommandHandler
import app.resonance.feature.music.navigation.CollectionKind
import app.resonance.feature.music.navigation.CollectionRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Title/subtitle data for the header; text that needs resources is resolved in the UI. */
data class CollectionHeaderInfo(
    val kind: CollectionKind,
    /** Album title, artist name, genre or folder name; null when the tag is missing. */
    val title: String?,
    /** Album artist for albums. */
    val subtitle: String? = null,
    val artworkUri: String? = null
)

sealed interface CollectionUiState {
    data object Loading : CollectionUiState

    data class Ready(
        val header: CollectionHeaderInfo,
        val tracks: List<MediaFile>,
        /** Only for artists: their albums, shown above the songs. */
        val albums: List<Album>,
        val favoriteIds: Set<Long>,
        val currentMediaId: Long?
    ) : CollectionUiState
}

@HiltViewModel
class CollectionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    library: MediaLibraryRepository,
    favorites: FavoritesRepository,
    controller: PlaybackController,
    private val commands: TrackCommands
) : ViewModel(), TrackCommandHandler {

    private val route = savedStateHandle.toRoute<CollectionRoute>()
    val kind: CollectionKind = CollectionKind.valueOf(route.kind)

    private val collection: MediaCollection = when (kind) {
        CollectionKind.ALBUM -> MediaCollection.AlbumTracks(route.id)
        CollectionKind.ARTIST -> MediaCollection.ArtistTracks(route.id)
        CollectionKind.GENRE -> MediaCollection.GenreTracks(route.key)
        CollectionKind.FOLDER -> MediaCollection.FolderTracks(route.key)
    }

    private val header: Flow<CollectionHeaderInfo> = when (kind) {
        CollectionKind.ALBUM -> library.album(route.id).map { album ->
            CollectionHeaderInfo(kind, album?.title, album?.artist, album?.artworkUri)
        }

        CollectionKind.ARTIST -> library.artist(route.id).map { CollectionHeaderInfo(kind, it?.name) }

        CollectionKind.GENRE -> flowOf(CollectionHeaderInfo(kind, route.key))

        CollectionKind.FOLDER -> flowOf(
            CollectionHeaderInfo(kind, route.key.trimEnd('/').substringAfterLast('/').ifEmpty { route.key }, route.key)
        )
    }

    private val albums: Flow<List<Album>> =
        if (kind == CollectionKind.ARTIST) library.albumsByArtist(route.id) else flowOf(emptyList())

    val uiState: StateFlow<CollectionUiState> = combine(
        header,
        library.tracks(collection),
        albums,
        favorites.favoriteIds(),
        controller.state.map { it.current?.libraryId }.distinctUntilChanged()
    ) { header, tracks, albums, favoriteIds, currentId ->
        CollectionUiState.Ready(header, tracks, albums, favoriteIds, currentId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionUiState.Loading)

    private val tracks: List<MediaFile>
        get() = (uiState.value as? CollectionUiState.Ready)?.tracks.orEmpty()

    fun play(index: Int) = commands.playFrom(tracks, index)

    fun shuffle() = commands.shuffle(tracks)

    override fun playNext(track: MediaFile) = commands.playNext(track)

    override fun addToQueue(track: MediaFile) = commands.addToQueue(track)

    override fun toggleFavorite(track: MediaFile) {
        viewModelScope.launch { commands.toggleFavorite(track) }
    }
}
