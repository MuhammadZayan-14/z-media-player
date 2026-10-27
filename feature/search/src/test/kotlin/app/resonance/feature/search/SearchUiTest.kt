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

@RunWith(AndroidJUnit4::class)
class SearchUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun noResultsStateNamesTheQuery() {
        compose.setContent {
            ResonanceTheme(dynamicColor = false) {
                SearchScreen(
                    state = SearchUiState(query = "zzz", results = SearchResults.EMPTY, hasSearched = true),
                    actions = noActions,
                    onQueryChange = {},
                    onFilterChange = {},
                    onPlaySong = {},
                    onOpenAlbum = {},
                    onOpenArtist = {},
                    onPlayVideo = {},
                    onBack = {}
                )
            }
        }
        compose.onNodeWithText("Nothing matches \"zzz\".").assertIsDisplayed()
    }

    @Test
    fun tappingASongPlaysItAndFilterChipsReport() {
        val played = mutableListOf<Int>()
        val filters = mutableListOf<SearchFilter>()
        compose.setContent {
            ResonanceTheme(dynamicColor = false) {
                SearchScreen(
                    state = SearchUiState(
                        query = "night",
                        results = SearchResults(songs = listOf(testSong(1, title = "Night Drive"))),
                        hasSearched = true
                    ),
                    actions = noActions,
                    onQueryChange = {},
                    onFilterChange = { filters += it },
                    onPlaySong = { played += it },
                    onOpenAlbum = {},
                    onOpenArtist = {},
                    onPlayVideo = {},
                    onBack = {}
                )
            }
        }
        compose.onNodeWithText("Night Drive").performClick()
        compose.onNodeWithText("Albums").performClick()
        assertEquals(listOf(0), played)
        assertEquals(listOf(SearchFilter.ALBUMS), filters)
    }

    private val noActions = TrackActions({}, {}, {}, {})
}
