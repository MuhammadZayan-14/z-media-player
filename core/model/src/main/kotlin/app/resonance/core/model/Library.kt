package app.resonance.core.model

sealed interface LibraryScanState {
    data object Idle : LibraryScanState
    data object Scanning : LibraryScanState
    data class Done(val finishedAtEpochMs: Long, val itemCount: Int) : LibraryScanState
    data class Failed(val error: LibraryError) : LibraryScanState
}

sealed interface LibraryError {
    /** Read access to audio/video is missing or was revoked. */
    data object PermissionDenied : LibraryError

    /** MediaStore could not be queried (provider crash, storage unmounted mid-scan, ...). */
    data object StorageUnavailable : LibraryError
}

enum class SearchFilter { ALL, SONGS, ALBUMS, ARTISTS, VIDEOS }

data class SearchResults(
    val songs: List<MediaFile> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val videos: List<MediaFile> = emptyList()
) {
    val isEmpty: Boolean get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && videos.isEmpty()

    companion object {
        val EMPTY = SearchResults()
    }
}

data class HomeFeed(
    val recentlyPlayed: List<MediaFile> = emptyList(),
    val recentlyAdded: List<MediaFile> = emptyList(),
    val mostPlayed: List<MediaFile> = emptyList(),
    val continueWatching: List<Pair<MediaFile, VideoProgress>> = emptyList()
) {
    val isEmpty: Boolean
        get() = recentlyPlayed.isEmpty() && recentlyAdded.isEmpty() && mostPlayed.isEmpty() &&
            continueWatching.isEmpty()
}
