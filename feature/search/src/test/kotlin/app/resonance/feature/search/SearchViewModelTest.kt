package app.resonance.feature.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.resonance.core.designsystem.theme.ResonanceTheme
import app.resonance.core.domain.usecase.SearchLibraryUseCase
import app.resonance.core.domain.usecase.ToggleFavoriteUseCase
import app.resonance.core.domain.usecase.TrackCommands
import app.resonance.core.model.SearchFilter
import app.resonance.core.model.SearchResults
import app.resonance.core.testing.FakeFavoritesRepository
import app.resonance.core.testing.FakeMediaLibraryRepository
import app.resonance.core.testing.FakePlaybackController
import app.resonance.core.testing.MainDispatcherRule
import app.resonance.core.testing.testSong
import app.resonance.core.testing.testVideo
import app.resonance.core.ui.TrackActions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private val library =
        FakeMediaLibraryRepository(listOf(testSong(1, title = "Night Drive"), testVideo(2, title = "Night Sky")))
    private val favorites = FakeFavoritesRepository(library)
    private val controller = FakePlaybackController()

    private fun viewModel() = SearchViewModel(
        SavedStateHandle(),
        SearchLibraryUseCase(library),
        favorites,
        controller,
        TrackCommands(controller, ToggleFavoriteUseCase(favorites))
    )

    @Test
    fun resultsArriveAfterTheDebounceAndFiltersApply() = runTest(main.testDispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onQueryChange("night")
        runCurrent()
        assertTrue("debounced: no results yet", vm.uiState.value.results.isEmpty)

        advanceTimeBy(300)
        runCurrent()
        assertEquals(listOf(1L), vm.uiState.value.results.songs.map { it.id })
        assertEquals(listOf(2L), vm.uiState.value.results.videos.map { it.id })

        vm.onFilterChange(SearchFilter.VIDEOS)
        runCurrent()
        assertEquals(SearchFilter.VIDEOS, vm.uiState.value.filter) // chip updates immediately
        advanceTimeBy(300)
        runCurrent()
        assertTrue(vm.uiState.value.results.songs.isEmpty())

        vm.playSong(0) // no songs under the video filter: nothing is played
        assertTrue(controller.commands.isEmpty())
    }
}
