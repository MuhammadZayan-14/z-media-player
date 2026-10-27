package app.resonance.core.domain.usecase

import app.resonance.core.domain.repository.MediaLibraryRepository
import app.resonance.core.domain.repository.PlaybackSessionRepository
import app.resonance.core.model.MediaFile
import app.resonance.core.model.MediaKind
import app.resonance.core.model.PlaybackSnapshot
import app.resonance.core.model.RepeatMode
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestorePlaybackSessionUseCaseTest {

    private fun snapshot(ids: List<Long>, index: Int, position: Long = 42_000L) =
        PlaybackSnapshot(ids, index, position, RepeatMode.ALL, shuffleEnabled = true, speed = 1.5f)

    @Test
    fun `all items present keeps index and position`() {
        val result = RestorePlaybackSessionUseCase.resolve(snapshot(listOf(1, 2, 3), index = 1), setOf(1, 2, 3))
        assertEquals(RestorePlaybackSessionUseCase.Resolution(listOf(1L, 2L, 3L), 1, 42_000L), result)
    }

    @Test
    fun `missing earlier items shift the index but keep the position`() {
        val result = RestorePlaybackSessionUseCase.resolve(snapshot(listOf(1, 2, 3, 4), index = 2), setOf(2, 3, 4))
        assertEquals(RestorePlaybackSessionUseCase.Resolution(listOf(2L, 3L, 4L), 1, 42_000L), result)
    }

    @Test
    fun `missing current item resumes at the next survivor from the start`() {
        val result = RestorePlaybackSessionUseCase.resolve(snapshot(listOf(1, 2, 3, 4), index = 1), setOf(1, 3, 4))
        assertEquals(RestorePlaybackSessionUseCase.Resolution(listOf(1L, 3L, 4L), 1, 0L), result)
    }

    @Test
    fun `missing current item with nothing after it falls back to the last survivor`() {
        val result = RestorePlaybackSessionUseCase.resolve(snapshot(listOf(1, 2, 3), index = 2), setOf(1, 2))
        assertEquals(RestorePlaybackSessionUseCase.Resolution(listOf(1L, 2L), 1, 0L), result)
    }

    @Test
    fun `nothing left returns null`() {
        assertNull(RestorePlaybackSessionUseCase.resolve(snapshot(listOf(1, 2), index = 0), emptySet()))
    }

    @Test
    fun `out of range index is clamped`() {
        val result = RestorePlaybackSessionUseCase.resolve(snapshot(listOf(1, 2), index = 9), setOf(1, 2))
        assertEquals(1, result?.index)
    }

    @Test
    fun `invoke resolves media and carries playback settings`() = runTest {
        val sessions = mockk<PlaybackSessionRepository> {
            coEvery { load() } returns snapshot(listOf(10, 11, 12), index = 2)
        }
        val library = mockk<MediaLibraryRepository> {
            coEvery { getMedia(listOf(10L, 11L, 12L)) } returns listOf(media(10), media(12))
        }

        val restored = RestorePlaybackSessionUseCase(sessions, library)()!!

        assertEquals(listOf(10L, 12L), restored.items.map { it.id })
        assertEquals(1, restored.startIndex)
        assertEquals(42_000L, restored.startPositionMs)
        assertEquals(RepeatMode.ALL, restored.repeatMode)
        assertEquals(true, restored.shuffleEnabled)
        assertEquals(1.5f, restored.speed)
    }

    @Test
    fun `no saved session returns null`() = runTest {
        val sessions = mockk<PlaybackSessionRepository> { coEvery { load() } returns null }
        assertNull(RestorePlaybackSessionUseCase(sessions, mockk())())
    }

    private fun media(id: Long) = MediaFile(
        id = id, kind = MediaKind.AUDIO, contentUri = "content://media/external/audio/media/$id", title = "T$id",
        displayName = "t$id.mp3", artist = null, artistId = 0, album = null, albumId = 0, genre = null,
        durationMs = 1000, sizeBytes = 1, mimeType = "audio/mpeg", folderPath = "Music/", dateAddedEpochSec = 0
    )
}
