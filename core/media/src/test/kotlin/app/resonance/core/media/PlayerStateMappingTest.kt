package app.resonance.core.media

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.resonance.core.model.PlaybackError
import app.resonance.core.model.RepeatMode
import app.resonance.core.testing.testSong
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the Player -> PlaybackState mapping against a real ExoPlayer (no media decoded). */
@RunWith(AndroidJUnit4::class)
class PlayerStateMappingTest {
    private lateinit var player: ExoPlayer

    @Before
    fun setUp() {
        player = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext()).build()
    }

    @After
    fun tearDown() = player.release()

    @Test
    fun mediaItemsCarryIdUriAndMetadata() {
        val song = testSong(7, title = "Title", artist = "Artist", album = "Album", durationMs = 123_000)
        val item = song.toMediaItem()

        assertEquals("7", item.mediaId)
        assertEquals(song.contentUri, item.localConfiguration?.uri.toString())
        assertEquals(song.contentUri, item.requestMetadata.mediaUri.toString())
        assertEquals("Title", item.mediaMetadata.title)
        assertEquals("Album", item.mediaMetadata.albumTitle)
        assertEquals(123_000L, item.mediaMetadata.durationMs)

        val queueItem = item.toQueueItem()
        assertEquals(7L, queueItem.libraryId)
        assertEquals("Artist", queueItem.artist)
    }

    @Test
    fun externalItemsHaveNoLibraryId() {
        val item = externalMediaItem(android.net.Uri.parse("content://docs/1"), "Picked").toQueueItem()
        assertEquals(null, item.libraryId)
        assertEquals("Picked", item.title)
    }

    @Test
    fun stateReflectsQueuePositionAndModes() {
        player.setMediaItems(listOf(testSong(1), testSong(2), testSong(3)).map { it.toMediaItem() }, 1, 0)
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.shuffleModeEnabled = true
        player.setPlaybackSpeed(1.5f)

        val state = player.toPlaybackState(player.queueItems(), connected = true)

        assertEquals(listOf("1", "2", "3"), state.queue.map { it.mediaId })
        assertEquals(1, state.currentIndex)
        assertEquals("2", state.current?.mediaId)
        assertEquals(RepeatMode.ONE, state.repeatMode)
        assertTrue(state.shuffleEnabled)
        assertEquals(1.5f, state.speed)
        assertEquals(180_000L, state.durationMs) // falls back to metadata before the player knows the duration
        assertFalse("idle player shows play, not pause", state.isPlaying)
    }

    @Test
    fun emptyPlayerHasNoCurrentItem() {
        val state = player.toPlaybackState(emptyList(), connected = false)
        assertEquals(-1, state.currentIndex)
        assertFalse(state.hasMedia)
    }

    @Test
    fun repeatModesRoundTrip() {
        RepeatMode.entries.forEach { assertEquals(it, it.toPlayerRepeatMode().toRepeatMode()) }
    }

    @Test
    fun playbackErrorsMapToUserFacingCategories() {
        fun map(code: Int) = PlaybackException("x", null, code).toPlaybackError()
        assertEquals(PlaybackError.FileNotFound, map(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
        assertEquals(PlaybackError.Corrupted, map(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED))
        assertEquals(PlaybackError.UnsupportedFormat, map(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))
        assertEquals(
            PlaybackError.Unknown(PlaybackException.ERROR_CODE_REMOTE_ERROR),
            map(PlaybackException.ERROR_CODE_REMOTE_ERROR)
        )
    }
}
