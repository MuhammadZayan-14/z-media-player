package app.resonance.feature.playlists

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.resonance.core.domain.usecase.CreatePlaylistUseCase
import app.resonance.core.domain.usecase.PlaylistNameResult
import app.resonance.core.domain.usecase.RenamePlaylistUseCase
import app.resonance.core.domain.usecase.ToggleFavoriteUseCase
import app.resonance.core.domain.usecase.TrackCommands
import app.resonance.core.model.SmartCollection
import app.resonance.core.testing.FakeFavoritesRepository
import app.resonance.core.testing.FakeHistoryRepository
import app.resonance.core.testing.FakeMediaLibraryRepository
import app.resonance.core.testing.FakePlaybackController
import app.resonance.core.testing.FakePlaylistRepository
import app.resonance.core.testing.MainDispatcherRule
import app.resonance.core.testing.testSong
import app.resonance.feature.playlists.navigation.PlaylistDetailRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Robolectric because type-safe navigation routes are decoded from a Bundle-backed SavedStateHandle. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PlaylistsViewModelsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val library = FakeMediaLibraryRepository((1L..4L).map { testSong(it) })
    private val playlists = FakePlaylistRepository(library)
    private val favorites = FakeFavoritesRepository(library)
    private val controller = FakePlaybackController()

    @Test
    fun creatingAPlaylistClosesTheEditorAndDuplicatesKeepItOpen() = runTest {
        val viewModel = PlaylistsViewModel(playlists, CreatePlaylistUseCase(playlists))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.openCreate()
        viewModel.create("Gym")
        assertNull(viewModel.uiState.value.editor)
        assertEquals(listOf("Gym"), viewModel.uiState.value.playlists.map { it.name })

        viewModel.openCreate()
        viewModel.create(" gym ")
        assertEquals(PlaylistNameResult.Duplicate, viewModel.uiState.value.editor?.error)
    }

    @Test
    fun addToPlaylistReportsHowManySongsWereNew() = runTest {
        val id = playlists.create("Mix")
        playlists.addTracks(id, listOf(1))
        val viewModel = AddToPlaylistViewModel(playlists, CreatePlaylistUseCase(playlists))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.addTo(viewModel.uiState.value.playlists.single(), listOf(1, 2, 3))

        assertEquals(AddOutcome("Mix", 2), viewModel.uiState.value.outcome)
        viewModel.consumeOutcome()
        assertNull(viewModel.uiState.value.outcome)
    }

    @Test
    fun createAndAddRejectsBlankNames() = runTest {
        val viewModel = AddToPlaylistViewModel(playlists, CreatePlaylistUseCase(playlists))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.createAndAdd("  ", listOf(1))

        assertEquals(PlaylistNameResult.Blank, viewModel.uiState.value.nameError)
        assertTrue(playlists.playlists().first().isEmpty())
    }

    @Test
    fun userPlaylistDetailReordersRemovesAndPlays() = runTest {
        val id = playlists.create("Mix")
        playlists.addTracks(id, listOf(1, 2, 3))
        val viewModel = detail(PlaylistDetailRoute(playlistId = id))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.move(testSong(1), +1)
        viewModel.remove(testSong(3))
        val ready = viewModel.uiState.value as PlaylistDetailUiState.Ready
        assertEquals("Mix", ready.name)
        assertEquals(listOf(2L, 1L), ready.tracks.map { it.id })

        viewModel.play(1)
        assertEquals("play([2, 1], 1, shuffle=false)", controller.commands.last())
    }

    @Test
    fun deletedPlaylistBecomesGone() = runTest {
        val id = playlists.create("Temp")
        val viewModel = detail(PlaylistDetailRoute(playlistId = id))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.delete()

        assertEquals(PlaylistDetailUiState.Gone, viewModel.uiState.value)
    }

    @Test
    fun favoritesCollectionListsFavoritesAndCannotBeEdited() = runTest {
        favorites.setFavorite(4, true)
        val viewModel = detail(PlaylistDetailRoute(smart = SmartCollection.FAVORITES.name))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val ready = viewModel.uiState.value as PlaylistDetailUiState.Ready
        assertEquals(listOf(4L), ready.tracks.map { it.id })
        assertEquals(false, ready.isUserPlaylist)
    }

    private fun detail(route: PlaylistDetailRoute) = PlaylistDetailViewModel(
        savedStateHandle = SavedStateHandle(route = route),
        playlists = playlists,
        favorites = favorites,
        history = FakeHistoryRepository(library),
        library = library,
        controller = controller,
        commands = TrackCommands(controller, ToggleFavoriteUseCase(favorites)),
        renamePlaylist = RenamePlaylistUseCase(playlists)
    )
}
