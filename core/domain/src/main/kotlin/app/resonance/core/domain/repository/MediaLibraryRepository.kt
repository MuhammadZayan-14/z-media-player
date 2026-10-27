package app.resonance.core.domain.repository

import app.resonance.core.model.Album
import app.resonance.core.model.Artist
import app.resonance.core.model.Folder
import app.resonance.core.model.Genre
import app.resonance.core.model.LibraryScanState
import app.resonance.core.model.MediaCollection
import app.resonance.core.model.MediaFile
import app.resonance.core.model.SearchResults
import app.resonance.core.model.SongSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Read access to the device media library.
 *
 * MediaStore is the source of truth for which files exist; implementations keep a local index
 * in sync with it and apply the user's library preferences (minimum duration, duplicates).
 */
interface MediaLibraryRepository {
    val scanState: StateFlow<LibraryScanState>

    fun songs(sort: SongSort): Flow<List<MediaFile>>
    fun videos(): Flow<List<MediaFile>>
    fun albums(): Flow<List<Album>>
    fun artists(): Flow<List<Artist>>
    fun genres(): Flow<List<Genre>>
    fun folders(): Flow<List<Folder>>
    fun album(albumId: Long): Flow<Album?>
    fun artist(artistId: Long): Flow<Artist?>
    fun albumsByArtist(artistId: Long): Flow<List<Album>>
    fun tracks(collection: MediaCollection): Flow<List<MediaFile>>
    fun recentlyAdded(limit: Int): Flow<List<MediaFile>>
    fun search(query: String, limitPerSection: Int): Flow<SearchResults>

    /** Resolves ids in the given order, silently dropping ids that no longer exist. */
    suspend fun getMedia(ids: List<Long>): List<MediaFile>

    suspend fun getMedia(id: Long): MediaFile?

    /** Re-synchronises the index with MediaStore. Incremental unless [full] is set. */
    suspend fun refresh(full: Boolean = false)
}
