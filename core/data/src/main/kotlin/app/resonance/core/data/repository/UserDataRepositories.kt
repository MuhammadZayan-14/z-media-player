package app.resonance.core.data.repository

import app.resonance.core.common.AppDispatcher
import app.resonance.core.common.Dispatcher
import app.resonance.core.data.mapper.asExternalModel
import app.resonance.core.data.mapper.asExternalModels
import app.resonance.core.database.dao.FavoriteDao
import app.resonance.core.database.dao.HistoryDao
import app.resonance.core.database.dao.PlaybackSessionDao
import app.resonance.core.database.dao.PlaylistDao
import app.resonance.core.database.dao.VideoProgressDao
import app.resonance.core.database.entity.FavoriteEntity
import app.resonance.core.database.entity.PlayHistoryEntity
import app.resonance.core.database.entity.PlaybackStateEntity
import app.resonance.core.database.entity.PlaylistEntity
import app.resonance.core.database.entity.VideoProgressEntity
import app.resonance.core.domain.repository.FavoritesRepository
import app.resonance.core.domain.repository.HistoryRepository
import app.resonance.core.domain.repository.PlaybackSessionRepository
import app.resonance.core.domain.repository.PlaylistRepository
import app.resonance.core.domain.repository.VideoProgressRepository
import app.resonance.core.model.MediaFile
import app.resonance.core.model.PlaybackSnapshot
import app.resonance.core.model.Playlist
import app.resonance.core.model.VideoProgress
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomPlaylistRepository @Inject constructor(
    private val dao: PlaylistDao,
    private val clock: Clock
) : PlaylistRepository {
    override fun playlists(): Flow<List<Playlist>> = dao.playlists().map { rows -> rows.map { it.asExternalModel() } }

    override fun playlist(id: Long): Flow<Playlist?> = dao.playlist(id).map { it?.asExternalModel() }

    override fun tracks(playlistId: Long): Flow<List<MediaFile>> = dao.tracks(playlistId).map { it.asExternalModels() }

    override suspend fun create(name: String): Long {
        val now = clock.millis()
        return dao.insert(PlaylistEntity(name = name, createdAtEpochMs = now, updatedAtEpochMs = now))
    }

    override suspend fun rename(id: Long, name: String) = dao.rename(id, name, clock.millis())

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun nameExists(name: String, excludingId: Long?): Boolean = dao.nameExists(
        name,
        excludingId ?: -1L
    )

    override suspend fun addTracks(playlistId: Long, mediaIds: List<Long>): Int =
        if (mediaIds.isEmpty()) 0 else dao.addTracks(playlistId, mediaIds, clock.millis())

    override suspend fun removeTrack(playlistId: Long, mediaId: Long) {
        dao.removeItem(playlistId, mediaId)
        dao.touch(playlistId, clock.millis())
    }

    /**
     * Indices refer to the visible list (entries whose file exists). Hidden entries keep their
     * relative order and are placed after the visible ones they used to follow.
     */
    override suspend fun moveTrack(playlistId: Long, fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val visible = dao.visibleMediaIds(playlistId)
        if (fromIndex !in visible.indices || toIndex !in visible.indices) return
        val all = dao.allMediaIds(playlistId).toMutableList()
        val moving = visible[fromIndex]
        val anchor = visible[toIndex]
        all.remove(moving)
        val anchorIndex = all.indexOf(anchor)
        all.add(if (toIndex > fromIndex) anchorIndex + 1 else anchorIndex, moving)
        dao.reorder(playlistId, all, clock.millis())
    }
}

class RoomFavoritesRepository @Inject constructor(
    private val dao: FavoriteDao,
    private val clock: Clock
) : FavoritesRepository {
    override fun favoriteIds(): Flow<Set<Long>> = dao.favoriteIds().map { it.toSet() }

    override fun favorites(): Flow<List<MediaFile>> = dao.favorites().map { it.asExternalModels() }

    override suspend fun setFavorite(mediaId: Long, favorite: Boolean) {
        if (favorite) dao.upsert(FavoriteEntity(mediaId, clock.millis())) else dao.delete(mediaId)
    }
}

class RoomHistoryRepository @Inject constructor(
    private val dao: HistoryDao
) : HistoryRepository {
    override fun recentlyPlayed(
        limit: Int
    ): Flow<List<MediaFile>> = dao.recentlyPlayed(limit).map { it.asExternalModels() }

    override fun mostPlayed(limit: Int): Flow<List<MediaFile>> = dao.mostPlayed(limit).map { it.asExternalModels() }

    override suspend fun recordPlay(mediaId: Long, playedAtEpochMs: Long) =
        dao.record(PlayHistoryEntity(mediaId = mediaId, playedAtEpochMs = playedAtEpochMs), keep = MAX_HISTORY_ROWS)

    override suspend fun clear() = dao.clear()

    private companion object {
        /** Bounds the table; plenty for "most played" to be meaningful. */
        const val MAX_HISTORY_ROWS = 5_000
    }
}

class RoomVideoProgressRepository @Inject constructor(
    private val dao: VideoProgressDao,
    private val clock: Clock
) : VideoProgressRepository {
    override fun inProgress(): Flow<List<VideoProgress>> = dao.all().map { rows -> rows.map { it.asExternalModel() } }

    override suspend fun get(mediaId: Long): VideoProgress? = dao.get(mediaId)?.asExternalModel()

    /** Positions near either end are not worth resuming from, so they clear the entry instead. */
    override suspend fun save(progress: VideoProgress) {
        val remaining = progress.durationMs - progress.positionMs
        if (progress.durationMs <= 0 || progress.positionMs < MIN_RESUME_MS || remaining < FINISHED_THRESHOLD_MS) {
            dao.delete(progress.mediaId)
        } else {
            dao.upsert(VideoProgressEntity(progress.mediaId, progress.positionMs, progress.durationMs, clock.millis()))
        }
    }

    override suspend fun clear(mediaId: Long) = dao.delete(mediaId)

    internal companion object {
        const val MIN_RESUME_MS = 5_000L
        const val FINISHED_THRESHOLD_MS = 10_000L
    }
}

class RoomPlaybackSessionRepository @Inject constructor(
    private val dao: PlaybackSessionDao,
    @param:Dispatcher(AppDispatcher.IO) private val io: CoroutineDispatcher
) : PlaybackSessionRepository {
    override suspend fun load(): PlaybackSnapshot? = withContext(io) {
        val state = dao.state() ?: return@withContext null
        PlaybackSnapshot(
            mediaIds = dao.queue(),
            currentIndex = state.currentIndex,
            positionMs = state.positionMs,
            repeatMode = state.repeatMode,
            shuffleEnabled = state.shuffleEnabled,
            speed = state.speed
        )
    }

    override suspend fun save(snapshot: PlaybackSnapshot, queueChanged: Boolean) = withContext(io) {
        val state = PlaybackStateEntity(
            currentIndex = snapshot.currentIndex,
            positionMs = snapshot.positionMs,
            repeatMode = snapshot.repeatMode,
            shuffleEnabled = snapshot.shuffleEnabled,
            speed = snapshot.speed
        )
        if (queueChanged) dao.replaceQueue(snapshot.mediaIds, state) else dao.upsertState(state)
    }

    override suspend fun clear() = withContext(io) { dao.clear() }
}
