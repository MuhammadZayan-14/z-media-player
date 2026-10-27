package app.resonance.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.resonance.core.database.entity.FavoriteEntity
import app.resonance.core.database.entity.MediaEntity
import app.resonance.core.database.entity.PlayHistoryEntity
import app.resonance.core.database.entity.PlaybackStateEntity
import app.resonance.core.database.entity.PlaylistEntity
import app.resonance.core.database.entity.duplicateKeyOf
import app.resonance.core.model.MediaKind
import app.resonance.core.model.RepeatMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {
    private lateinit var db: ResonanceDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ResonanceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun songsApplyDurationMusicAndDuplicateRules() = runTest {
        db.mediaDao().upsert(
            listOf(
                song(1, "Song", duration = 200_000),
                song(2, "Song", duration = 200_400), // duplicate of 1 (same second bucket)
                song(3, "Ringtone", duration = 200_000, isMusic = false),
                song(4, "Clip", duration = 3_000),
                song(5, "Other", duration = 100_000)
            )
        )
        val dao = db.mediaDao()

        val strict = dao.songs(minDurationMs = 10_000, includeNonMusic = false, hideDuplicates = true).first()
        val permissive = dao.songs(minDurationMs = 0, includeNonMusic = true, hideDuplicates = false).first()

        assertEquals(setOf(1L, 5L), strict.map { it.id }.toSet())
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), permissive.map { it.id }.toSet())
    }

    @Test
    fun albumsAndArtistsAggregateVisibleTracks() = runTest {
        db.mediaDao().upsert(
            listOf(
                song(1, "A", albumId = 10, album = "First", artistId = 100, year = 2001),
                song(2, "B", albumId = 10, album = "First", artistId = 100, year = 2001),
                song(3, "C", albumId = 11, album = "Second", artistId = 100, year = 2005)
            )
        )
        val albums = db.mediaDao().albums(0, true, false).first().associateBy { it.id }
        val artist = db.mediaDao().artist(100, 0, true, false).first()

        assertEquals(2, albums.getValue(10).trackCount)
        assertEquals(1L, albums.getValue(10).firstTrackId)
        assertEquals(3, artist?.trackCount)
        assertEquals(2, artist?.albumCount)
    }

    @Test
    fun fullTextSearchMatchesPrefixesAcrossColumns() = runTest {
        db.mediaDao().upsert(listOf(song(1, "Harder Better", artist = "Daft Punk"), song(2, "Unrelated")))

        val byArtist = db.mediaDao().searchMedia("daf*", MediaKind.AUDIO.name, 10).first()
        val byTitleWords = db.mediaDao().searchMedia("hard* bett*", MediaKind.AUDIO.name, 10).first()

        assertEquals(listOf(1L), byArtist.map { it.id })
        assertEquals(listOf(1L), byTitleWords.map { it.id })
    }

    @Test
    fun applyChangesUpsertsAndDeletesInOneStep() = runTest {
        db.mediaDao().upsert(listOf(song(1, "Old"), song(2, "Gone")))
        db.mediaDao().applyChanges(upserts = listOf(song(1, "Renamed"), song(3, "New")), deletedIds = listOf(2))

        val rows = db.mediaDao().getByIds(listOf(1, 2, 3)).associateBy { it.id }
        assertEquals("Renamed", rows.getValue(1).title)
        assertNull(rows[2])
        assertTrue(3L in rows)
    }

    @Test
    fun playlistsSkipDuplicatesHideMissingFilesAndReorder() = runTest {
        db.mediaDao().upsert(listOf(song(1, "One"), song(2, "Two"), song(3, "Three")))
        val dao = db.playlistDao()
        val id = dao.insert(PlaylistEntity(name = "Mix", createdAtEpochMs = 0, updatedAtEpochMs = 0))

        val added = dao.addTracks(id, listOf(1, 2, 2, 3, 99), now = 1)
        val again = dao.addTracks(id, listOf(1), now = 2)
        dao.reorder(id, listOf(3, 99, 1, 2), now = 3)

        assertEquals(4, added) // 99 is stored even though its file is missing
        assertEquals(0, again)
        assertEquals(listOf(3L, 1L, 2L), dao.tracks(id).first().map { it.id })
        assertEquals(3, dao.playlist(id).first()?.trackCount)
        assertTrue(dao.nameExists("mix", excludingId = -1))
    }

    @Test
    fun deletingAPlaylistCascadesToItsEntries() = runTest {
        db.mediaDao().upsert(listOf(song(1, "One")))
        val id = db.playlistDao().insert(PlaylistEntity(name = "X", createdAtEpochMs = 0, updatedAtEpochMs = 0))
        db.playlistDao().addTracks(id, listOf(1), now = 0)

        db.playlistDao().delete(id)

        assertTrue(db.playlistDao().allMediaIds(id).isEmpty())
    }

    @Test
    fun historyRanksRecentAndMostPlayedAndPrunes() = runTest {
        db.mediaDao().upsert(listOf(song(1, "One"), song(2, "Two")))
        val dao = db.historyDao()
        dao.record(PlayHistoryEntity(mediaId = 1, playedAtEpochMs = 10), keep = 100)
        dao.record(PlayHistoryEntity(mediaId = 1, playedAtEpochMs = 20), keep = 100)
        dao.record(PlayHistoryEntity(mediaId = 2, playedAtEpochMs = 30), keep = 100)

        assertEquals(listOf(2L, 1L), dao.recentlyPlayed(10).first().map { it.id })
        assertEquals(listOf(1L, 2L), dao.mostPlayed(10).first().map { it.id })

        dao.record(PlayHistoryEntity(mediaId = 2, playedAtEpochMs = 40), keep = 2)
        assertEquals(listOf(2L), dao.mostPlayed(10).first().map { it.id })
    }

    @Test
    fun favoritesJoinOnlyExistingFiles() = runTest {
        db.mediaDao().upsert(listOf(song(1, "One")))
        db.favoriteDao().upsert(FavoriteEntity(1, 0))
        db.favoriteDao().upsert(FavoriteEntity(42, 0))

        assertEquals(setOf(1L, 42L), db.favoriteDao().favoriteIds().first().toSet())
        assertEquals(listOf(1L), db.favoriteDao().favorites().first().map { it.id })
    }

    @Test
    fun playbackSessionReplacesQueueAndUpdatesStateSeparately() = runTest {
        val dao = db.playbackSessionDao()
        val state =
            PlaybackStateEntity(
                currentIndex = 1,
                positionMs = 500,
                repeatMode = RepeatMode.ONE,
                shuffleEnabled = false,
                speed = 1f
            )
        dao.replaceQueue(listOf(5, 6, 7), state)
        dao.replaceQueue(listOf(8, 9), state)
        dao.upsertState(state.copy(positionMs = 900))

        assertEquals(listOf(8L, 9L), dao.queue())
        assertEquals(900L, dao.state()?.positionMs)
        assertEquals(RepeatMode.ONE, dao.state()?.repeatMode)

        dao.clear()
        assertNull(dao.state())
    }

    private fun song(
        id: Long,
        title: String,
        duration: Long = 180_000,
        isMusic: Boolean = true,
        artist: String? = "Artist",
        album: String? = "Album",
        albumId: Long = 1,
        artistId: Long = 1,
        year: Int? = null
    ) = MediaEntity(
        id = id,
        kind = MediaKind.AUDIO,
        contentUri = "content://media/external/audio/media/$id",
        title = title,
        displayName = "$title.mp3",
        artist = artist,
        artistId = artistId,
        album = album,
        albumId = albumId,
        genre = null,
        durationMs = duration,
        sizeBytes = 1,
        mimeType = "audio/mpeg",
        folderPath = "Music/",
        dateAddedEpochSec = id,
        dateModifiedEpochSec = id,
        trackNumber = null,
        year = year,
        width = null,
        height = null,
        isMusic = isMusic,
        duplicateKey = duplicateKeyOf(MediaKind.AUDIO, title, artist, album, duration)
    )
}
