package app.resonance.core.domain.usecase

import app.resonance.core.domain.playback.PlaybackController
import app.resonance.core.domain.repository.FavoritesRepository
import app.resonance.core.domain.repository.HistoryRepository
import app.resonance.core.domain.repository.MediaLibraryRepository
import app.resonance.core.domain.repository.VideoProgressRepository
import app.resonance.core.model.MediaFile
import app.resonance.core.model.MediaKind
import app.resonance.core.model.SearchFilter
import app.resonance.core.model.SearchResults
import app.resonance.core.model.VideoProgress
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryUseCasesTest {

    private val song = media(1, MediaKind.AUDIO)
    private val video = media(2, MediaKind.VIDEO)
    private val results = SearchResults(songs = listOf(song), videos = listOf(video))

    @Test
    fun `blank search queries never hit the repository`() = runTest {
        val library = mockk<MediaLibraryRepository>()
        assertEquals(SearchResults.EMPTY, SearchLibraryUseCase(library)("  ", SearchFilter.ALL).first())
        verify(exactly = 0) { library.search(any(), any()) }
    }

    @Test
    fun `search filters restrict sections and widen the limit`() = runTest {
        val library = mockk<MediaLibraryRepository> { every { search("abc", any()) } returns flowOf(results) }

        val all = SearchLibraryUseCase(library)(" abc ", SearchFilter.ALL).first()
        val videos = SearchLibraryUseCase(library)("abc", SearchFilter.VIDEOS).first()

        assertEquals(results, all)
        assertEquals(SearchResults(videos = listOf(video)), videos)
        verify { library.search("abc", SearchLibraryUseCase.PREVIEW_LIMIT) }
        verify { library.search("abc", SearchLibraryUseCase.FULL_LIMIT) }
    }

    @Test
    fun `home feed pairs in-progress videos with library videos and drops missing ones`() = runTest {
        val library = mockk<MediaLibraryRepository> {
            every { videos() } returns flowOf(listOf(video))
            every { recentlyAdded(any()) } returns flowOf(listOf(song))
        }
        val history = mockk<HistoryRepository> {
            every { recentlyPlayed(any()) } returns flowOf(listOf(song))
            every { mostPlayed(any()) } returns flowOf(emptyList())
        }
        val progress = mockk<VideoProgressRepository> {
            every { inProgress() } returns flowOf(listOf(VideoProgress(2, 1_000, 10_000), VideoProgress(99, 5, 10)))
        }

        val feed = GetHomeFeedUseCase(library, history, progress)().first()

        assertEquals(listOf(video to VideoProgress(2, 1_000, 10_000)), feed.continueWatching)
        assertEquals(listOf(song), feed.recentlyPlayed)
        assertEquals(listOf(song), feed.recentlyAdded)
        assertTrue(feed.mostPlayed.isEmpty())
    }

    @Test
    fun `toggle favorite flips the current state`() = runTest {
        val ids = MutableStateFlow(setOf(5L))
        val favorites = mockk<FavoritesRepository>(relaxUnitFun = true) { every { favoriteIds() } returns ids }

        assertEquals(false, ToggleFavoriteUseCase(favorites)(5L))
        assertEquals(true, ToggleFavoriteUseCase(favorites)(6L))
        coVerify { favorites.setFavorite(5L, false) }
        coVerify { favorites.setFavorite(6L, true) }
    }

    @Test
    fun `shuffle starts at a random track with shuffle enabled`() {
        val controller = mockk<PlaybackController>(relaxed = true)
        val tracks = (1L..10L).map { media(it, MediaKind.AUDIO) }
        val random = Random(seed = 3)
        val expectedStart = Random(seed = 3).nextInt(tracks.size)

        TrackCommands(controller, mockk()).shuffle(tracks, random)

        verify { controller.play(tracks, expectedStart, shuffle = true) }
    }

    @Test
    fun `play from clamps the index and ignores empty lists`() {
        val controller = mockk<PlaybackController>(relaxed = true)
        val commands = TrackCommands(controller, mockk())
        val tracks = listOf(media(1, MediaKind.AUDIO), media(2, MediaKind.AUDIO))

        commands.playFrom(emptyList(), 0)
        commands.playFrom(tracks, 5)

        verify(exactly = 1) { controller.play(any(), any(), any()) }
        verify { controller.play(tracks, 1, false) }
    }

    private fun media(id: Long, kind: MediaKind) = MediaFile(
        id = id, kind = kind, contentUri = "content://media/$id", title = "T$id", displayName = "t$id",
        artist = null, artistId = 0, album = null, albumId = 0, genre = null, durationMs = 1000, sizeBytes = 1,
        mimeType = "", folderPath = "", dateAddedEpochSec = 0
    )
}
