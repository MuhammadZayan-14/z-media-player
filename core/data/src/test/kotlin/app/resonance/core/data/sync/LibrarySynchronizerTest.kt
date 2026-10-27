package app.resonance.core.data.sync

import android.Manifest
import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.resonance.core.data.mediastore.MediaStoreSource
import app.resonance.core.database.ResonanceDatabase
import app.resonance.core.database.entity.MediaEntity
import app.resonance.core.database.entity.duplicateKeyOf
import app.resonance.core.datastore.LibrarySyncStateDataSource
import app.resonance.core.model.LibraryError
import app.resonance.core.model.LibraryScanState
import app.resonance.core.model.MediaKind
import app.resonance.core.permissions.MediaAccessChecker
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class LibrarySynchronizerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private lateinit var db: ResonanceDatabase
    private lateinit var source: FakeMediaStoreSource
    private lateinit var synchronizer: LibrarySynchronizer

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
        db = Room.inMemoryDatabaseBuilder(app, ResonanceDatabase::class.java).allowMainThreadQueries().build()
        source = FakeMediaStoreSource()
        val store = PreferenceDataStoreFactory.createWithPath(scope = testScope.backgroundScope) {
            File(tmp.root, "sync.preferences_pb").toOkioPath()
        }
        synchronizer = LibrarySynchronizer(
            source = source,
            mediaDao = db.mediaDao(),
            syncState = LibrarySyncStateDataSource(store),
            accessChecker = MediaAccessChecker(app),
            clock = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC),
            io = dispatcher
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun firstSyncReadsEverything() = testScope.runTest {
        source.put(song(1), generation = 1)
        source.put(song(2), generation = 2)
        source.put(video(3), generation = 3)
        source.generation = 3

        synchronizer.sync(full = false)

        assertEquals(setOf(1L, 2L, 3L), db.mediaDao().allIds().toSet())
        assertEquals(LibraryScanState.Done(1_000, 3), synchronizer.state.value)
        assertTrue("first sync must be a full read", source.queries.all { it == null })
    }

    @Test
    fun laterSyncsOnlyReadChangedRows() = testScope.runTest {
        source.put(song(1, title = "Before"), generation = 1)
        source.generation = 1
        synchronizer.sync(full = false)

        source.put(song(1, title = "After"), generation = 2)
        source.generation = 2
        source.queries.clear()
        synchronizer.sync(full = false)

        assertEquals(listOf<Long?>(1L, 1L), source.queries) // audio + video, both since generation 1
        assertEquals("After", db.mediaDao().getById(1)?.title)
    }

    @Test
    fun deletedFilesLeaveTheIndex() = testScope.runTest {
        source.put(song(1), generation = 1)
        source.put(song(2), generation = 1)
        synchronizer.sync(full = false)

        source.remove(2)
        synchronizer.sync(full = false)

        assertEquals(listOf(1L), db.mediaDao().allIds())
    }

    @Test
    fun providerFailureKeepsTheIndexAndRetriesFromTheSameGeneration() = testScope.runTest {
        source.put(song(1), generation = 1)
        source.generation = 1
        synchronizer.sync(full = false)

        source.generation = 5
        source.failure = IllegalStateException("volume unmounted")
        synchronizer.sync(full = false)

        assertEquals(LibraryScanState.Failed(LibraryError.StorageUnavailable), synchronizer.state.value)
        assertEquals(listOf(1L), db.mediaDao().allIds())

        source.failure = null
        source.queries.clear()
        synchronizer.sync(full = false)
        assertTrue("generation must not advance after a failed sync", source.queries.all { it == 1L })
    }

    @Test
    fun revokedAccessEmptiesTheIndex() = testScope.runTest {
        source.put(song(1), generation = 1)
        synchronizer.sync(full = false)

        shadowOf(app).denyPermissions(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
        synchronizer.sync(full = false)

        assertEquals(LibraryScanState.Failed(LibraryError.PermissionDenied), synchronizer.state.value)
        assertTrue(db.mediaDao().allIds().isEmpty())
    }

    @Test
    fun rebuiltMediaStoreForcesAFullRead() = testScope.runTest {
        source.put(song(1), generation = 1)
        synchronizer.sync(full = false)

        source.version = "rebuilt"
        source.queries.clear()
        synchronizer.sync(full = false)

        assertTrue(source.queries.all { it == null })
    }

    @Test
    fun audioOnlyAccessDropsVideos() = testScope.runTest {
        source.put(song(1), generation = 1)
        source.put(video(2), generation = 1)
        synchronizer.sync(full = false)

        shadowOf(app).denyPermissions(Manifest.permission.READ_MEDIA_VIDEO)
        synchronizer.sync(full = false)

        assertEquals(listOf(1L), db.mediaDao().allIds())
    }

    private class FakeMediaStoreSource : MediaStoreSource {
        var version = "v1"
        var generation = 1L
        var failure: RuntimeException? = null
        val queries = mutableListOf<Long?>()
        private val rows = mutableMapOf<Long, Pair<MediaEntity, Long>>()

        fun put(entity: MediaEntity, generation: Long) {
            rows[entity.id] = entity to generation
        }

        fun remove(id: Long) {
            rows.remove(id)
        }

        override fun version() = version
        override fun volumes() = setOf("external_primary")
        override fun generation(volume: String) = generation

        override fun query(volume: String, kind: MediaKind, sinceGeneration: Long?): List<MediaEntity> {
            failure?.let { throw it }
            queries += sinceGeneration
            return rows.values
                .filter { (entity, gen) -> entity.kind == kind && (sinceGeneration == null || gen > sinceGeneration) }
                .map { it.first }
        }

        override fun ids(volume: String, kind: MediaKind): Set<Long> {
            failure?.let { throw it }
            return rows.values.filter { it.first.kind == kind }.map { it.first.id }.toSet()
        }
    }

    private fun song(id: Long, title: String = "Song $id") = entity(id, MediaKind.AUDIO, title)

    private fun video(id: Long) = entity(id, MediaKind.VIDEO, "Video $id")

    private fun entity(id: Long, kind: MediaKind, title: String) = MediaEntity(
        id = id, kind = kind, contentUri = "content://media/external/x/$id", title = title, displayName = "$title.bin",
        artist = null, artistId = 0, album = null, albumId = 0, genre = null, durationMs = 60_000, sizeBytes = 1,
        mimeType = "", folderPath = "", dateAddedEpochSec = id, dateModifiedEpochSec = id, trackNumber = null,
        year = null, width = null, height = null, isMusic = kind == MediaKind.AUDIO,
        duplicateKey = duplicateKeyOf(kind, title, null, null, 60_000)
    )
}
