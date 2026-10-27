package app.resonance.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.resonance.core.database.entity.FavoriteEntity
import app.resonance.core.database.entity.MediaEntity
import app.resonance.core.database.entity.PlayHistoryEntity
import app.resonance.core.database.entity.PlaybackStateEntity
import app.resonance.core.database.entity.QueueEntryEntity
import app.resonance.core.database.entity.VideoProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT media_id FROM favorites")
    fun favoriteIds(): Flow<List<Long>>

    @Query("SELECT m.* FROM favorites f JOIN media m ON m.id = f.media_id ORDER BY f.added_at DESC")
    fun favorites(): Flow<List<MediaEntity>>

    @Upsert
    suspend fun upsert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE media_id = :mediaId")
    suspend fun delete(mediaId: Long)
}

@Dao
interface HistoryDao {
    @Query(
        """
        SELECT m.* FROM media m
        JOIN (SELECT media_id, MAX(played_at) AS last_played FROM play_history GROUP BY media_id) h
          ON h.media_id = m.id
        ORDER BY h.last_played DESC LIMIT :limit
        """
    )
    fun recentlyPlayed(limit: Int): Flow<List<MediaEntity>>

    @Query(
        """
        SELECT m.* FROM media m
        JOIN (SELECT media_id, COUNT(*) AS plays, MAX(played_at) AS last_played FROM play_history GROUP BY media_id) h
          ON h.media_id = m.id
        ORDER BY h.plays DESC, h.last_played DESC LIMIT :limit
        """
    )
    fun mostPlayed(limit: Int): Flow<List<MediaEntity>>

    @Insert
    suspend fun insert(entry: PlayHistoryEntity)

    @Query(
        """
        DELETE FROM play_history WHERE id NOT IN
        (SELECT id FROM play_history ORDER BY played_at DESC LIMIT :keep)
        """
    )
    suspend fun prune(keep: Int)

    @Query("DELETE FROM play_history")
    suspend fun clear()

    @Transaction
    suspend fun record(entry: PlayHistoryEntity, keep: Int) {
        insert(entry)
        prune(keep)
    }
}

@Dao
interface VideoProgressDao {
    @Query("SELECT * FROM video_progress ORDER BY updated_at DESC")
    fun all(): Flow<List<VideoProgressEntity>>

    @Query("SELECT * FROM video_progress WHERE media_id = :mediaId")
    suspend fun get(mediaId: Long): VideoProgressEntity?

    @Upsert
    suspend fun upsert(progress: VideoProgressEntity)

    @Query("DELETE FROM video_progress WHERE media_id = :mediaId")
    suspend fun delete(mediaId: Long)
}

@Dao
interface PlaybackSessionDao {
    @Query("SELECT media_id FROM playback_queue ORDER BY position")
    suspend fun queue(): List<Long>

    @Query("SELECT * FROM playback_state WHERE id = ${PlaybackStateEntity.SINGLETON_ID}")
    suspend fun state(): PlaybackStateEntity?

    @Upsert
    suspend fun upsertState(state: PlaybackStateEntity)

    @Insert
    suspend fun insertQueue(entries: List<QueueEntryEntity>)

    @Query("DELETE FROM playback_queue")
    suspend fun clearQueue()

    @Query("DELETE FROM playback_state")
    suspend fun clearState()

    @Transaction
    suspend fun replaceQueue(mediaIds: List<Long>, state: PlaybackStateEntity) {
        clearQueue()
        mediaIds.mapIndexed { index, id -> QueueEntryEntity(index, id) }
            .chunked(MediaDao.UPSERT_CHUNK)
            .forEach { insertQueue(it) }
        upsertState(state)
    }

    @Transaction
    suspend fun clear() {
        clearQueue()
        clearState()
    }
}
