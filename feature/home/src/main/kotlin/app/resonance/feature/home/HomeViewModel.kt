package app.resonance.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.resonance.core.domain.playback.PlaybackController
import app.resonance.core.domain.repository.MediaLibraryRepository
import app.resonance.core.domain.usecase.GetHomeFeedUseCase
import app.resonance.core.domain.usecase.TrackCommands
import app.resonance.core.model.HomeFeed
import app.resonance.core.model.MediaFile
import app.resonance.core.model.QueueItem
import app.resonance.core.model.SongSort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Ready(
        val feed: HomeFeed,
        /** The track loaded in the player (possibly restored from last time), if any. */
        val nowPlaying: QueueItem?,
        val isPlaying: Boolean
    ) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    getHomeFeed: GetHomeFeedUseCase,
    private val library: MediaLibraryRepository,
    private val controller: PlaybackController,
    private val commands: TrackCommands
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(getHomeFeed(), controller.state) { feed, playback ->
        HomeUiState.Ready(feed, playback.current, playback.isPlaying)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    fun play(section: List<MediaFile>, index: Int) = commands.playFrom(section, index)

    fun resume() {
        if (!controller.state.value.isPlaying) controller.togglePlayPause()
    }

    fun shuffleLibrary() {
        viewModelScope.launch { commands.shuffle(library.songs(SongSort.TITLE).first()) }
    }

    fun onAccessChanged() {
        viewModelScope.launch { library.refresh(full = false) }
    }
}
