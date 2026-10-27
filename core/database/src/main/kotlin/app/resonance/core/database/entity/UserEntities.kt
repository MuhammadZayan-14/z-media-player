package app.resonance.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.resonance.core.model.RepeatMode

/*
 * User data deliberately has no foreign key to `media`: a card being unmounted or a rescan
 * racing a file copy must not cascade-delete playlists, favourites or history. Queries join
 * against `media`, so entries for files that are gone are simply not shown, and reappear if
 * the file comes back.
 */

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMs: Long
)

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlist_id", "media_id"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["playlist_id", "position"]), Index("media_id")]
)
data class PlaylistItemEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "media_id") val mediaId: Long,
    val position: Int,
    @ColumnInfo(name = "added_at") val addedAtEpochMs: Long
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey @ColumnInfo(name = "media_id") val mediaId: Long,
    @ColumnInfo(name = "added_at") val addedAtEpochMs: Long
)

@Entity(
    tableName = "play_history",
    indices = [Index("media_id"), Index("played_at")]
)
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "media_id") val mediaId: Long,
    @ColumnInfo(name = "played_at") val playedAtEpochMs: Long
)

@Entity(tableName = "video_progress", indices = [Index("updated_at")])
data class VideoProgressEntity(
    @PrimaryKey @ColumnInfo(name = "media_id") val mediaId: Long,
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMs: Long
)

/** The persisted play queue, in playlist (not shuffle) order. */
@Entity(tableName = "playback_queue")
data class QueueEntryEntity(
    @PrimaryKey val position: Int,
    @ColumnInfo(name = "media_id") val mediaId: Long
)

/** Single-row table holding the scalar part of the persisted session. */
@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "current_index") val currentIndex: Int,
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    @ColumnInfo(name = "repeat_mode") val repeatMode: RepeatMode,
    @ColumnInfo(name = "shuffle_enabled") val shuffleEnabled: Boolean,
    val speed: Float
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
