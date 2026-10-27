package app.resonance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.resonance.core.domain.playback.PlaybackController
import app.resonance.core.domain.repository.UserPreferencesRepository
import app.resonance.core.model.PlaybackEvent
import app.resonance.core.model.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface MainActivityUiState {
    data object Loading : MainActivityUiState
    data class Ready(val preferences: UserPreferences) : MainActivityUiState
}

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    preferences: UserPreferencesRepository,
    controller: PlaybackController
) : ViewModel() {
    /** Loading keeps the splash screen up until the theme is known, avoiding a light/dark flash. */
    val uiState: StateFlow<MainActivityUiState> = preferences.preferences
        .map { MainActivityUiState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainActivityUiState.Loading)

    val playbackEvents: Flow<PlaybackEvent> = controller.events
}
