package app.resonance.feature.home

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.resonance.core.designsystem.theme.ResonanceTheme
import app.resonance.core.domain.usecase.GetHomeFeedUseCase
import app.resonance.core.domain.usecase.ToggleFavoriteUseCase
import app.resonance.core.domain.usecase.TrackCommands
import app.resonance.core.model.HomeFeed
import app.resonance.core.model.QueueItem
import app.resonance.core.testing.FakeFavoritesRepository
import app.resonance.core.testing.FakeHistoryRepository
import app.resonance.core.testing.FakeMediaLibraryRepository
import app.resonance.core.testing.FakePlaybackController
import app.resonance.core.testing.FakeVideoProgressRepository
import app.resonance.core.testing.MainDispatcherRule
import app.resonance.core.testing.testSong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val library = FakeMediaLibraryRepository((1L..3L).map { testSong(it) })
    private val history = FakeHistoryRepository(library)
    private val controller = FakePlaybackController()
    private val viewModel by lazy {
        HomeViewModel(
            GetHomeFeedUseCase(library, history, FakeVideoProgressRepository()),
            library,
            controller,
            TrackCommands(controller, ToggleFavoriteUseCase(FakeFavoritesRepository(library)))
        )
    }

    @Test
    fun feedReflectsHistoryAndTheLoadedTrack() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        history.recordPlay(2, playedAtEpochMs = 10)
        controller.play(listOf(testSong(3)), 0)

        val ready = viewModel.uiState.value as HomeUiState.Ready
        assertEquals(listOf(2L), ready.feed.recentlyPlayed.map { it.id })
        assertEquals(3, ready.feed.recentlyAdded.size)
        assertEquals("3", ready.nowPlaying?.mediaId)
    }

    @Test
    fun resumeOnlyStartsPlaybackWhenPaused() = runTest {
        controller.state.update { it.copy(isPlaying = true) }
        viewModel.resume()
        assertTrue(controller.commands.isEmpty())

        controller.state.update { it.copy(isPlaying = false) }
        viewModel.resume()
        assertEquals(listOf("togglePlayPause"), controller.commands)
    }

    @Test
    fun shuffleLibraryPlaysEverythingShuffled() = runTest {
        viewModel.shuffleLibrary()
        assertTrue(controller.commands.single().endsWith("shuffle=true)"))
    }
}

@RunWith(AndroidJUnit4::class)
class HomeUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun continueCardResumesAndRailsPlayFromTheTappedItem() {
        var resumed = 0
        val played = mutableListOf<Pair<List<Long>, Int>>()
        val added = listOf(testSong(1, title = "First"), testSong(2, title = "Second"))
        compose.setContent {
            ResonanceTheme(dynamicColor = false) {
                HomeScreen(
                    state = HomeUiState.Ready(
                        feed = HomeFeed(recentlyAdded = added),
                        nowPlaying = QueueItem("9", "Paused song", "Someone", null, 1000),
                        isPlaying = false
                    ),
                    hasAccess = true,
                    permissionContent = {},
                    onPlay = { list, index -> played += list.map { it.id } to index },
                    onPlayVideo = {},
                    onResume = { resumed++ },
                    onShuffleLibrary = {},
                    onSearch = {}
                )
            }
        }
        compose.onNodeWithText("Paused song").assertIsDisplayed()
        compose.onNodeWithTag("continue_card").performClick()
        compose.onNodeWithText("Second").performClick()

        assertEquals(1, resumed)
        assertEquals(listOf(listOf(1L, 2L) to 1), played)
    }

    @Test
    fun noAccessShowsThePermissionRequest() {
        compose.setContent {
            ResonanceTheme(dynamicColor = false) {
                HomeScreen(HomeUiState.Loading, false, { Text("grant-access") }, { _, _ -> }, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("grant-access").assertIsDisplayed()
    }

    @Test
    fun emptyLibraryExplainsWhatToDo() {
        compose.setContent {
            ResonanceTheme(dynamicColor = false) {
                HomeScreen(HomeUiState.Ready(HomeFeed(), null, false), true, {}, { _, _ -> }, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Your library is empty").assertIsDisplayed()
    }
}
