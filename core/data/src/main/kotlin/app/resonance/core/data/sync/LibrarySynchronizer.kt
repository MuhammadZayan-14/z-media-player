package app.resonance.core.data.sync

import app.resonance.core.common.AppDispatcher
import app.resonance.core.common.Dispatcher
import app.resonance.core.common.Log
import app.resonance.core.data.mediastore.MediaStoreSource
import app.resonance.core.database.dao.MediaDao
import app.resonance.core.database.entity.MediaEntity
import app.resonance.core.datastore.LibrarySyncState
import app.resonance.core.datastore.LibrarySyncStateDataSource
import app.resonance.core.model.LibraryError
import app.resonance.core.model.LibraryScanState
import app.resonance.core.model.MediaKind
import app.resonance.core.permissions.AccessLevel
import app.resonance.core.permissions.MediaAccess
import app.resonance.core.permissions.MediaAccessChecker
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keeps the `media` table in step with MediaStore.
 *
 * Incremental (Android 11+): only rows whose generation advanced since the last sync are
 * re-read, plus one cheap id-only query per kind to detect deletions. A full re-read happens on
 * the first sync, below Android 11, when MediaStore's database was rebuilt, or on request.
 *
 * Safety properties:
 *  - Generations are captured *before* querying, so changes that land during a scan are
 *    picked up next time rather than lost.
 *  - Nothing is deleted unless the id listing for every volume completed.
 *  - All changes commit in one transaction, and the sync bookkeeping is written only after
 *    that commit, so an interrupted scan is simply redone.
 */
@Singleton
class LibrarySynchronizer @Inject constructor(
    private val source: MediaStoreSource,
    private val mediaDao: MediaDao,
    private val syncState: LibrarySyncStateDataSource,
    private val accessChecker: MediaAccessChecker,
    private val clock: Clock,
    @param:Dispatcher(AppDispatcher.IO) private val io: CoroutineDispatcher
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<LibraryScanState>(LibraryScanState.Idle)
    val state: StateFlow<LibraryScanState> = _state.asStateFlow()

    suspend fun sync(full: Boolean) {
        // Concurrent triggers (observer + pull-to-refresh) collapse into one run.
        if (mutex.isLocked) return
        mutex.withLock {
            _state.value = LibraryScanState.Scanning
            _state.value = try {
                withContext(io) { runSync(full, accessChecker.current()) }
            } catch (e: CancellationException) {
                _state.value = LibraryScanState.Idle
                throw e
            } catch (e: SecurityException) {
                Log.w(TAG, "sync_permission_denied", e)
                LibraryScanState.Failed(LibraryError.PermissionDenied)
            } catch (e: RuntimeException) {
                // ContentProvider failures surface as assorted RuntimeExceptions (IllegalState,
                // IllegalArgument, SQLite...). Report them instead of crashing the app.
                Log.e(TAG, "sync_failed", e)
                LibraryScanState.Failed(LibraryError.StorageUnavailable)
            }
        }
    }

    private suspend fun runSync(full: Boolean, access: MediaAccess): LibraryScanState {
        if (!access.canReadAnything) {
            // Access was revoked: the index must not keep showing files we can no longer read.
            mediaDao.clear()
            syncState.reset()
            return LibraryScanState.Failed(LibraryError.PermissionDenied)
        }
        val previous = syncState.read()
        // Folding the access level into the version forces a full re-read whenever grants change:
        // newly visible files keep their old generation and would be missed incrementally.
        val version = "${source.version()}|${access.audio}|${access.video}"
        val volumes = source.volumes()
        val canIncrement = !full && previous.mediaStoreVersion == version
        val kinds = buildList {
            if (access.audio != AccessLevel.NONE) add(MediaKind.AUDIO)
            if (access.video != AccessLevel.NONE) add(MediaKind.VIDEO)
        }

        val upserts = ArrayList<MediaEntity>()
        val presentIds = HashSet<Long>()
        val generations = HashMap<String, Long>()
        var incremental = true
        for (volume in volumes) {
            val generation = source.generation(volume)
            val since = if (canIncrement) previous.generations[volume] else null
            if (since == null) incremental = false
            generation?.let { generations[volume] = it }
            for (kind in kinds) {
                // With partial video access the user can change the selection without any
                // generation moving, so selected videos (few by nature) are always re-read.
                val partial = kind == MediaKind.VIDEO && access.video == AccessLevel.PARTIAL
                upserts += source.query(volume, kind, if (partial) null else since)
                presentIds += source.ids(volume, kind)
            }
        }

        val deleted = mediaDao.allIds().filterNot { it in presentIds }
        mediaDao.applyChanges(upserts, deleted)
        val now = clock.millis()
        syncState.write(LibrarySyncState(version, generations, now))

        val total = mediaDao.count()
        Log.i(
            TAG,
            "sync_done",
            "incremental" to incremental,
            "volumes" to volumes.size,
            "upserted" to upserts.size,
            "deleted" to deleted.size,
            "total" to total
        )
        return LibraryScanState.Done(now, total)
    }

    private companion object {
        const val TAG = "LibrarySync"
    }
}
