package app.resonance.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.resonance.core.domain.repository.HistoryRepository
import app.resonance.core.domain.repository.MediaLibraryRepository
import app.resonance.core.domain.repository.UserPreferencesRepository
import app.resonance.core.model.LibraryScanState
import app.resonance.core.model.ThemeMode
import app.resonance.core.model.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Ready(val preferences: UserPreferences, val scan: LibraryScanState) : SettingsUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val library: MediaLibraryRepository,
    private val history: HistoryRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        combine(preferences.preferences, library.scanState) { prefs, scan -> SettingsUiState.Ready(prefs, scan) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

    fun setThemeMode(mode: ThemeMode) = launch { preferences.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = launch { preferences.setUseDynamicColor(enabled) }
    fun setSkipSilence(enabled: Boolean) = launch { preferences.setSkipSilence(enabled) }
    fun setRestoreSession(enabled: Boolean) = launch { preferences.setRestoreLastSession(enabled) }
    fun setHideDuplicates(enabled: Boolean) = launch { preferences.setHideDuplicates(enabled) }
    fun setIncludeNonMusic(enabled: Boolean) = launch { preferences.setIncludeNonMusicAudio(enabled) }
    fun setMinDuration(seconds: Int) = launch { preferences.setMinAudioDurationSec(seconds) }
    fun rescan() = launch { library.refresh(full = true) }
    fun clearHistory() = launch { history.clear() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
