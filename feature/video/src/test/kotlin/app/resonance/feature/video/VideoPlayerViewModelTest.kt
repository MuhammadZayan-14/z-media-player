package app.resonance.feature.video

import androidx.lifecycle.SavedStateHandle
import androidx.media3.common.Player
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.resonance.core.media.video.VideoPlayerFactory
import app.resonance.core.model.PlaybackError
import app.resonance.core.model.VideoProgress
import app.resonance.core.testing.FakeMediaLibraryRepository
import app.resonance.core.testing.FakeVideoProgressRepository
import app.resonance.core.testing.MainDispatcherRule
import app.resonance.core.testing.testVideo
import app.resonance.feature.video.player.AspectMode
import app.resonance.feature.video.player.VideoPlayerViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses a real ExoPlayer (test build, no decoding) to check what the ViewModel asks of it. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class VideoPlayerViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val library = FakeMediaLibraryRepository(listOf(testVideo(5, title = "Holiday")))
    private val progress = FakeVideoProgressRepository()
    private val appScope = TestScope(main.testDispatcher)
    private val factory = object : VideoPlayerFactory {
        override fun create(): Player = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext()).build()
    }

    private fun viewModel(mediaId: Long) = VideoPlayerViewModel(
        SavedStateHandle(mapOf(VideoPlayerViewModel.EXTRA_MEDIA_ID to mediaId)),
        library,
        progress,
        appScope,
        factory
    )

    @Test
    fun reopeningAVideoResumesAtTheSavedPosition() = runTest {
        progress.save(VideoProgress(5, positionMs = 95_000, durationMs = 600_000))

        val vm = viewModel(5)

        assertEquals("Holiday", vm.uiState.value.title)
        assertEquals("5", vm.player.currentMediaItem?.mediaId)
        assertEquals(95_000L, vm.player.currentPosition)
        assertEquals(true, vm.player.playWhenReady)
    }

    @Test
    fun aVideoThatNoLongerExistsReportsFileNotFound() = runTest {
        val vm = viewModel(404)
        assertEquals(PlaybackError.FileNotFound, vm.uiState.value.error)
        assertEquals(0, vm.player.mediaItemCount)
    }

    @Test
    fun aspectModeCyclesFitZoomStretch() = runTest {
        val vm = viewModel(5)
        val seen = List(3) {
            vm.cycleAspect()
            vm.uiState.value.aspect
        }
        assertEquals(listOf(AspectMode.ZOOM, AspectMode.STRETCH, AspectMode.FIT), seen)
    }
}
