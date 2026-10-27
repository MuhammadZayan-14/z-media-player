package app.resonance.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.resonance.core.domain.playback.PlaybackController
import app.resonance.core.domain.repository.FavoritesRepository
import app.resonance.core.domain.usecase.ToggleFavoriteUseCase
import app.resonance.core.model.PlaybackState
import app.resonance.core.model.RepeatMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlayerUiState(
    val playback: PlaybackState = PlaybackState(),
    val isFavorite: Boolean = false
) {
    /** Favouriting needs a library id; files opened from the picker have none. */
    val canFavorite: Boolean get() = playback.current?.libraryId != null
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val controller: PlaybackController,
    favorites: FavoritesRepository,
    private val toggleFavorite: ToggleFavoriteUseCase
) : ViewModel() {

    val uiState: StateFlow<PlayerUiState> = combine(controller.state, favorites.favoriteIds()) {
            playback,
            favoriteIds
        ->
        PlayerUiState(playback, isFavorite = playback.current?.libraryId?.let { it in favoriteIds } == true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlayerUiState(controller.state.value))

    /** Ticks only while a seek bar is on screen. */
    val positionMs: StateFlow<Long> = controller.positionMs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0L)

    fun togglePlayPause() = controller.togglePlayPause()
    fun skipToNext() = controller.skipToNext()
    fun skipToPrevious() = controller.skipToPrevious()
    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)
    fun setSpeed(speed: Float) = controller.setSpeed(speed)
    fun toggleShuffle() = controller.setShuffleEnabled(!uiState.value.playback.shuffleEnabled)
    fun skipToQueueIndex(index: Int) = controller.skipToQueueIndex(index)
    fun removeFromQueue(index: Int) = controller.removeFromQueue(index)
    fun moveQueueItem(from: Int, to: Int) = controller.moveQueueItem(from, to)

    /** Off → all → one → off, the order most players use. */
    fun cycleRepeatMode() {
        val next = when (uiState.value.playback.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        controller.setRepeatMode(next)
    }

    fun toggleFavorite() {
        val id = uiState.value.playback.current?.libraryId ?: return
        viewModelScope.launch { toggleFavorite(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
