package app.resonance.core.domain.repository

import app.resonance.core.model.MediaFile
import app.resonance.core.model.PlaybackSnapshot
import app.resonance.core.model.Playlist
import app.resonance.core.model.VideoProgress
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun playlists(): Flow<List<Playlist>>
    fun playlist(id: Long): Flow<Playlist?>

    /** Tracks in playlist order. Entries whose file has disappeared are omitted but kept on disk. */
    fun tracks(playlistId: Long): Flow<List<MediaFile>>

    suspend fun create(name: String): Long
    suspend fun rename(id: Long, name: String)
    suspend fun delete(id: Long)
    suspend fun nameExists(name: String, excludingId: Long? = null): Boolean

    /** Appends tracks that are not already in the playlist; returns how many were added. */
    suspend fun addTracks(playlistId: Long, mediaIds: List<Long>): Int
    suspend fun removeTrack(playlistId: Long, mediaId: Long)
    suspend fun moveTrack(playlistId: Long, fromIndex: Int, toIndex: Int)
}

interface FavoritesRepository {
    fun favoriteIds(): Flow<Set<Long>>
    fun favorites(): Flow<List<MediaFile>>
    suspend fun setFavorite(mediaId: Long, favorite: Boolean)
}

interface HistoryRepository {
    fun recentlyPlayed(limit: Int): Flow<List<MediaFile>>
    fun mostPlayed(limit: Int): Flow<List<MediaFile>>
    suspend fun recordPlay(mediaId: Long, playedAtEpochMs: Long)
    suspend fun clear()
}

interface VideoProgressRepository {
    fun inProgress(): Flow<List<VideoProgress>>
    suspend fun get(mediaId: Long): VideoProgress?
    suspend fun save(progress: VideoProgress)
    suspend fun clear(mediaId: Long)
}

interface PlaybackSessionRepository {
    suspend fun load(): PlaybackSnapshot?

    /**
     * Persists the session. [queueChanged] lets callers skip rewriting the whole queue when only
     * the position moved, which keeps periodic position saves to a single-row update.
     */
    suspend fun save(snapshot: PlaybackSnapshot, queueChanged: Boolean)
    suspend fun clear()
}
