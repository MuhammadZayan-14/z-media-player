package app.resonance.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.resonance.core.database.entity.MediaEntity
import kotlinx.coroutines.flow.Flow

/*
 * Every audio query applies the same visibility rules (the user's library preferences):
 *  - shorter than the minimum duration          -> hidden
 *  - not flagged as music, unless opted in       -> hidden
 *  - a duplicate of a lower id, if hiding dupes  -> hidden
 * Sorting by display order happens in Kotlin with a locale-aware Collator.
 */
private const val VISIBLE_AUDIO = """
    kind = 'AUDIO'
    AND duration_ms >= :minDurationMs
    AND (:includeNonMusic OR is_music = 1)
    AND (NOT :hideDuplicates OR id IN (SELECT MIN(id) FROM media WHERE kind = 'AUDIO' GROUP BY duplicate_key))
"""

@Dao
interface MediaDao {
    @Upsert
    suspend fun upsert(items: List<MediaEntity>)

    @Query("DELETE FROM media WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT id FROM media")
    suspend fun allIds(): List<Long>

    @Query("SELECT COUNT(*) FROM media")
    suspend fun count(): Int

    @Query("DELETE FROM media")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<MediaEntity>) {
        clear()
        items.chunked(UPSERT_CHUNK).forEach { upsert(it) }
    }

    /** Applies one sync pass atomically, so observers see a single invalidation. */
    @Transaction
    suspend fun applyChanges(upserts: List<MediaEntity>, deletedIds: List<Long>) {
        deletedIds.chunked(UPSERT_CHUNK).forEach { deleteByIds(it) }
        upserts.chunked(UPSERT_CHUNK).forEach { upsert(it) }
    }

    @Query("SELECT * FROM media WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<MediaEntity>

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun getById(id: Long): MediaEntity?

    @Query("SELECT * FROM media WHERE $VISIBLE_AUDIO")
    fun songs(minDurationMs: Long, includeNonMusic: Boolean, hideDuplicates: Boolean): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE kind = 'VIDEO' ORDER BY date_added DESC")
    fun videos(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE $VISIBLE_AUDIO ORDER BY date_added DESC, id DESC LIMIT :limit")
    fun recentlyAdded(
        minDurationMs: Long,
        includeNonMusic: Boolean,
        hideDuplicates: Boolean,
        limit: Int
    ): Flow<List<MediaEntity>>

    @Query(
        """
        SELECT album_id AS id, MIN(album) AS title, MIN(artist) AS artist, COUNT(*) AS track_count,
               MAX(year) AS year, MIN(id) AS first_track_id
        FROM media WHERE $VISIBLE_AUDIO
        GROUP BY album_id
        """
    )
    fun albums(minDurationMs: Long, includeNonMusic: Boolean, hideDuplicates: Boolean): Flow<List<AlbumRow>>

    @Query(
        """
        SELECT album_id AS id, MIN(album) AS title, MIN(artist) AS artist, COUNT(*) AS track_count,
               MAX(year) AS year, MIN(id) AS first_track_id
        FROM media WHERE $VISIBLE_AUDIO AND album_id = :albumId
        GROUP BY album_id
        """
    )
    fun album(albumId: Long, minDurationMs: Long, includeNonMusic: Boolean, hideDuplicates: Boolean): Flow<AlbumRow?>

    @Query(
        """
        SELECT album_id AS id, MIN(album) AS title, MIN(artist) AS artist, COUNT(*) AS track_count,
               MAX(year) AS year, MIN(id) AS first_track_id
        FROM media WHERE $VISIBLE_AUDIO AND artist_id = :artistId
        GROUP BY album_id ORDER BY MAX(year) DESC
        """
    )
    fun albumsByArtist(
        artistId: Long,
        minDurationMs: Long,
        includeNonMusic: Boolean,
        hideDuplicates: Boolean
    ): Flow<List<AlbumRow>>

    @Query(
        """
        SELECT artist_id AS id, MIN(artist) AS name, COUNT(*) AS track_count, COUNT(DISTINCT album_id) AS album_count
        FROM media WHERE $VISIBLE_AUDIO
        GROUP BY artist_id
        """
    )
    fun artists(minDurationMs: Long, includeNonMusic: Boolean, hideDuplicates: Boolean): Flow<List<ArtistRow>>

    @Query(
        """
        SELECT artist_id AS id, MIN(artist) AS name, COUNT(*) AS track_count, COUNT(DISTINCT album_id) AS album_count
        FROM media WHERE $VISIBLE_AUDIO AND artist_id = :artistId
        GROUP BY artist_id
        """
    )
    fun artist(artistId: Long, minDurationMs: Long, includeNonMusic: Boolean, hideDuplicates: Boolean): Flow<ArtistRow?>

    @Query(
        """
        SELECT MIN(genre) AS name, COUNT(*) AS track_count
        FROM media WHERE $VISIBLE_AUDIO AND genre IS NOT NULL
        GROUP BY genre COLLATE NOCASE
        """
    )
    fun genres(minDurationMs: Long, includeNonMusic: Boolean, hideDuplicates: Boolean): Flow<List<GenreRow>>

    @Query(
        """
        SELECT folder_path AS path, COUNT(*) AS item_count
        FROM media WHERE $VISIBLE_AUDIO
        GROUP BY folder_path
        """
    )
    fun folders(minDurationMs: Long, includeNonMusic: Boolean, hideDuplicates: Boolean): Flow<List<FolderRow>>

    @Query(
        "SELECT * FROM media WHERE $VISIBLE_AUDIO AND album_id = :albumId ORDER BY track_number, title COLLATE NOCASE"
    )
    fun tracksInAlbum(
        albumId: Long,
        minDurationMs: Long,
        includeNonMusic: Boolean,
        hideDuplicates: Boolean
    ): Flow<List<MediaEntity>>

    @Query(
        "SELECT * FROM media WHERE $VISIBLE_AUDIO AND artist_id = :artistId ORDER BY album COLLATE NOCASE, track_number"
    )
    fun tracksByArtist(
        artistId: Long,
        minDurationMs: Long,
        includeNonMusic: Boolean,
        hideDuplicates: Boolean
    ): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE $VISIBLE_AUDIO AND genre = :genre COLLATE NOCASE ORDER BY title COLLATE NOCASE")
    fun tracksInGenre(
        genre: String,
        minDurationMs: Long,
        includeNonMusic: Boolean,
        hideDuplicates: Boolean
    ): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE $VISIBLE_AUDIO AND folder_path = :path ORDER BY display_name COLLATE NOCASE")
    fun tracksInFolder(
        path: String,
        minDurationMs: Long,
        includeNonMusic: Boolean,
        hideDuplicates: Boolean
    ): Flow<List<MediaEntity>>

    /** [ftsQuery] is an FTS4 MATCH expression, e.g. `daft* punk*`. */
    @Query(
        """
        SELECT media.* FROM media JOIN media_fts ON media.id = media_fts.rowid
        WHERE media_fts MATCH :ftsQuery AND media.kind = :kind
        LIMIT :limit
        """
    )
    fun searchMedia(ftsQuery: String, kind: String, limit: Int): Flow<List<MediaEntity>>

    @Query(
        """
        SELECT album_id AS id, MIN(album) AS title, MIN(artist) AS artist, COUNT(*) AS track_count,
               MAX(year) AS year, MIN(id) AS first_track_id
        FROM media WHERE $VISIBLE_AUDIO AND album LIKE '%' || :query || '%' ESCAPE '\'
        GROUP BY album_id ORDER BY MIN(album) COLLATE NOCASE LIMIT :limit
        """
    )
    fun searchAlbums(
        query: String,
        minDurationMs: Long,
        includeNonMusic: Boolean,
        hideDuplicates: Boolean,
        limit: Int
    ): Flow<List<AlbumRow>>

    @Query(
        """
        SELECT artist_id AS id, MIN(artist) AS name, COUNT(*) AS track_count, COUNT(DISTINCT album_id) AS album_count
        FROM media WHERE $VISIBLE_AUDIO AND artist LIKE '%' || :query || '%' ESCAPE '\'
        GROUP BY artist_id ORDER BY MIN(artist) COLLATE NOCASE LIMIT :limit
        """
    )
    fun searchArtists(
        query: String,
        minDurationMs: Long,
        includeNonMusic: Boolean,
        hideDuplicates: Boolean,
        limit: Int
    ): Flow<List<ArtistRow>>

    companion object {
        /** Stays well under SQLite's bound-variable limit for `IN (...)` lists. */
        const val UPSERT_CHUNK = 500
    }
}

data class AlbumRow(
    val id: Long,
    val title: String?,
    val artist: String?,
    @ColumnInfo(name = "track_count") val trackCount: Int,
    val year: Int?,
    @ColumnInfo(name = "first_track_id") val firstTrackId: Long
)

data class ArtistRow(
    val id: Long,
    val name: String?,
    @ColumnInfo(name = "track_count") val trackCount: Int,
    @ColumnInfo(name = "album_count") val albumCount: Int
)

data class GenreRow(
    val name: String,
    @ColumnInfo(name = "track_count") val trackCount: Int
)

data class FolderRow(
    val path: String,
    @ColumnInfo(name = "item_count") val itemCount: Int
)
