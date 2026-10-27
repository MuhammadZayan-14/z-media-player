package app.resonance.feature.player

import app.resonance.core.domain.usecase.ToggleFavoriteUseCase
import app.resonance.core.model.QueueItem
import app.resonance.core.model.RepeatMode
import app.resonance.core.testing.FakeFavoritesRepository
import app.resonance.core.testing.FakePlaybackController
import app.resonance.core.testing.MainDispatcherRule
import app.resonance.core.testing.testSong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val controller = FakePlaybackController()
    private val favorites = FakeFavoritesRepository()

    // Lazy: the ViewModel must be created after MainDispatcherRule installs the test dispatcher.
    private val viewModel by lazy { PlayerViewModel(controller, favorites, ToggleFavoriteUseCase(favorites)) }

    @Test
    fun repeatCyclesOffAllOneOff() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val seen = mutableListOf<RepeatMode>()
        repeat(3) {
            viewModel.cycleRepeatMode()
            seen += viewModel.uiState.value.playback.repeatMode
        }
        assertEquals(listOf(RepeatMode.ALL, RepeatMode.ONE, RepeatMode.OFF), seen)
    }

    @Test
    fun favoriteFollowsTheCurrentTrack() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        controller.play(listOf(testSong(1), testSong(2)), startIndex = 1)
        assertFalse(viewModel.uiState.value.isFavorite)

        viewModel.toggleFavorite()

        assertTrue(viewModel.uiState.value.isFavorite)
        assertEquals(setOf(2L), favorites.ids.value)
    }

    @Test
    fun pickedFilesCannotBeFavorited() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        controller.state.update {
            it.copy(queue = listOf(QueueItem("uri:content://doc/1", "Picked", null, null, 0)), currentIndex = 0)
        }
        assertFalse(viewModel.uiState.value.canFavorite)
        viewModel.toggleFavorite()
        assertTrue(favorites.ids.value.isEmpty())
    }

    @Test
    fun transportCommandsReachTheController() {
        viewModel.togglePlayPause()
        viewModel.seekTo(30_000)
        viewModel.setSpeed(1.5f)
        viewModel.moveQueueItem(0, 2)
        assertEquals(listOf("togglePlayPause", "seekTo(30000)", "speed(1.5)"), controller.commands.take(3))
    }
}
