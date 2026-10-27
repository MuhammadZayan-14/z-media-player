package app.resonance.core.data.repository

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.resonance.core.data.sync.LibrarySynchronizer
import app.resonance.core.database.ResonanceDatabase
import app.resonance.core.database.entity.MediaEntity
import app.resonance.core.database.entity.duplicateKeyOf
import app.resonance.core.datastore.UserPreferencesDataSource
import app.resonance.core.model.LibraryScanState
import app.resonance.core.model.MediaKind
import app.resonance.core.model.SongSort
import app.resonance.core.model.VideoProgress
import app.resonance.core.testing.testSong
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RepositoriesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val clock = Clock.fixed(Instant.ofEpochMilli(5_000), ZoneOffset.UTC)
    private lateinit var db: ResonanceDatabase
    private lateinit var preferences: UserPreferencesDataSource

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(app, ResonanceDatabase::class.java).allowMainThreadQueries().build()
        preferences = UserPreferencesDataSource(
            PreferenceDataStoreFactory.createWithPath(scope = testScope.backgroundScope) {
                File(tmp.root, "prefs.preferences_pb").toOkioPath()
            }
        )
    }

    @After
    fun tearDown() = db.close()

    private fun library(): OfflineFirstMediaLibraryRepository {
        val synchronizer =
            mockk<LibrarySynchronizer> { every { state } returns MutableStateFlow(LibraryScanState.Idle) }
        return OfflineFirstMediaLibraryRepository(db.mediaDao(), preferences, synchronizer, dispatcher, dispatcher)
    }

    @Test
    fun ftsQueryNeutralisesSyntaxAndPrefixMatchesEveryWord() {
        assertEquals("Daft* pu* nk*", OfflineFirstMediaLibraryRepository.ftsQueryOf(" Daft  pu\"nk* "))
        assertEquals("beyoncé*", OfflineFirstMediaLibraryRepository.ftsQueryOf("beyoncé")?.lowercase())
        assertNull(OfflineFirstMediaLibraryRepository.ftsQueryOf(" \"* - "))
    }

    @Test
    fun likePatternsEscapeWildcards() {
        assertEquals("100\\% \\_hits\\\\", OfflineFirstMediaLibraryRepository.escapeLike("100% _hits\\"))
    }

    @Test
    fun songSortIsLocaleAwareAndPutsUnknownsLast() {
        val songs = listOf(
            testSong(1, title = "éclair", artist = null),
            testSong(2, title = "Apple", artist = "zed"),
            testSong(3, title = "banana", artist = "Ann")
        )
        val byTitle = songs.sortedWith(OfflineFirstMediaLibraryRepository.songComparator(SongSort.TITLE)).map { it.id }
        val byArtist = songs.sortedWith(OfflineFirstMediaLibraryRepository.songComparator(SongSort.ARTIST)).map {
            it.id
        }

        assertEquals(listOf(2L, 3L, 1L), byTitle)
        assertEquals(listOf(3L, 2L, 1L), byArtist)
    }

    @Test
    fun songsFollowLibraryPreferences() = testScope.runTest {
        db.mediaDao().upsert(listOf(entity(1, 200_000), entity(2, 5_000), entity(3, 200_000, isMusic = false)))
        val repository = library()

        assertEquals(listOf(1L), repository.songs(SongSort.TITLE).first().map { it.id })

        preferences.setMinAudioDurationSec(0)
        preferences.setIncludeNonMusicAudio(true)
        assertEquals(setOf(1L, 2L, 3L), repository.songs(SongSort.TITLE).first().map { it.id }.toSet())
    }

    @Test
    fun searchUsesTheFullTextIndexAndHidesFilteredAudio() = testScope.runTest {
        db.mediaDao().upsert(listOf(entity(1, 200_000, title = "Night Drive"), entity(2, 3_000, title = "Night Clip")))

        val results = library().search("nig dri", limitPerSection = 10).first()
        val clipHidden = library().search("night", limitPerSection = 10).first()

        assertEquals(listOf(1L), results.songs.map { it.id })
        assertEquals(listOf(1L), clipHidden.songs.map { it.id })
    }

    @Test
    fun getMediaPreservesRequestedOrderAndDropsMissing() = testScope.runTest {
        db.mediaDao().upsert(listOf(entity(1, 60_000), entity(2, 60_000)))
        assertEquals(listOf(2L, 1L), library().getMedia(listOf(2, 99, 1)).map { it.id })
    }

    @Test
    fun movingTracksKeepsHiddenEntriesAndUsesVisibleIndices() = testScope.runTest {
        db.mediaDao().upsert(listOf(entity(1, 60_000), entity(2, 60_000), entity(3, 60_000)))
        val playlists = RoomPlaylistRepository(db.playlistDao(), clock)
        val id = playlists.create("Mix")
        playlists.addTracks(id, listOf(1, 99, 2, 3))

        playlists.moveTrack(id, fromIndex = 0, toIndex = 2)
        assertEquals(listOf(2L, 3L, 1L), playlists.tracks(id).first().map { it.id })

        playlists.moveTrack(id, fromIndex = 2, toIndex = 0)
        assertEquals(listOf(1L, 2L, 3L), playlists.tracks(id).first().map { it.id })
        // The entry for the missing file survives reordering.
        assertEquals(setOf(1L, 2L, 3L, 99L), db.playlistDao().allMediaIds(id).toSet())
    }

    @Test
    fun videoProgressIgnoresTheVeryStartAndEnd() = testScope.runTest {
        val progress = RoomVideoProgressRepository(db.videoProgressDao(), clock)

        progress.save(VideoProgress(1, positionMs = 2_000, durationMs = 600_000))
        progress.save(VideoProgress(2, positionMs = 595_000, durationMs = 600_000))
        progress.save(VideoProgress(3, positionMs = 120_000, durationMs = 600_000))

        assertNull(progress.get(1))
        assertNull(progress.get(2))
        assertEquals(120_000L, progress.get(3)?.positionMs)

        progress.save(VideoProgress(3, positionMs = 599_000, durationMs = 600_000))
        assertTrue(progress.inProgress().first().isEmpty())
    }

    private fun entity(id: Long, duration: Long, isMusic: Boolean = true, title: String = "Song $id") = MediaEntity(
        id = id, kind = MediaKind.AUDIO, contentUri = "content://media/external/audio/media/$id", title = title,
        displayName = "$title.mp3", artist = "A", artistId = 1, album = "B", albumId = 1, genre = null,
        durationMs = duration, sizeBytes = 1, mimeType = "audio/mpeg", folderPath = "Music/",
        dateAddedEpochSec = id, dateModifiedEpochSec = id, trackNumber = null, year = null, width = null,
        height = null, isMusic = isMusic, duplicateKey = duplicateKeyOf(MediaKind.AUDIO, title, "A", "B", duration)
    )
}
