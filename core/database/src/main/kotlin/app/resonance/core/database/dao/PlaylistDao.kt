package app.resonance.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.resonance.core.database.entity.MediaEntity
import app.resonance.core.database.entity.PlaylistEntity
import app.resonance.core.database.entity.PlaylistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.id, p.name, p.created_at, p.updated_at,
               (SELECT COUNT(*) FROM playlist_items i JOIN media m ON m.id = i.media_id WHERE i.playlist_id = p.id)
               AS track_count
        FROM playlists p ORDER BY p.name COLLATE NOCASE
        """
    )
    fun playlists(): Flow<List<PlaylistRow>>

    @Query(
        """
        SELECT p.id, p.name, p.created_at, p.updated_at,
               (SELECT COUNT(*) FROM playlist_items i JOIN media m ON m.id = i.media_id WHERE i.playlist_id = p.id)
               AS track_count
        FROM playlists p WHERE p.id = :id
        """
    )
    fun playlist(id: Long): Flow<PlaylistRow?>

    @Query(
        """
        SELECT m.* FROM playlist_items i JOIN media m ON m.id = i.media_id
        WHERE i.playlist_id = :playlistId ORDER BY i.position
        """
    )
    fun tracks(playlistId: Long): Flow<List<MediaEntity>>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updated_at = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE playlists SET updated_at = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        """
        SELECT EXISTS(SELECT 1 FROM playlists WHERE name = :name COLLATE NOCASE AND id != :excludingId)
        """
    )
    suspend fun nameExists(name: String, excludingId: Long): Boolean

    /** Every entry, including ones whose file is currently missing. */
    @Query("SELECT media_id FROM playlist_items WHERE playlist_id = :playlistId ORDER BY position")
    suspend fun allMediaIds(playlistId: Long): List<Long>

    /** Entries whose file currently exists, in display order. */
    @Query(
        """
        SELECT i.media_id FROM playlist_items i JOIN media m ON m.id = i.media_id
        WHERE i.playlist_id = :playlistId ORDER BY i.position
        """
    )
    suspend fun visibleMediaIds(playlistId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<PlaylistItemEntity>): List<Long>

    @Query("DELETE FROM playlist_items WHERE playlist_id = :playlistId AND media_id = :mediaId")
    suspend fun removeItem(playlistId: Long, mediaId: Long)

    @Query("UPDATE playlist_items SET position = :position WHERE playlist_id = :playlistId AND media_id = :mediaId")
    suspend fun setPosition(playlistId: Long, mediaId: Long, position: Int)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_items WHERE playlist_id = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Transaction
    suspend fun addTracks(playlistId: Long, mediaIds: List<Long>, now: Long): Int {
        val start = maxPosition(playlistId) + 1
        val items = mediaIds.distinct().mapIndexed { offset, mediaId ->
            PlaylistItemEntity(playlistId, mediaId, start + offset, now)
        }
        val added = insertItems(items).count { it != -1L }
        if (added > 0) touch(playlistId, now)
        return added
    }

    /** Rewrites positions so that [orderedIds] appear in that order. */
    @Transaction
    suspend fun reorder(playlistId: Long, orderedIds: List<Long>, now: Long) {
        orderedIds.forEachIndexed { index, mediaId -> setPosition(playlistId, mediaId, index) }
        touch(playlistId, now)
    }
}

data class PlaylistRow(
    val id: Long,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMs: Long,
    @ColumnInfo(name = "track_count") val trackCount: Int
)
