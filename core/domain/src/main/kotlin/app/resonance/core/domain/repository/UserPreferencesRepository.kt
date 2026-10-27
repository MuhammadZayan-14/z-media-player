package app.resonance.core.domain.repository

import app.resonance.core.model.SongSort
import app.resonance.core.model.ThemeMode
import app.resonance.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setUseDynamicColor(enabled: Boolean)
    suspend fun setHideDuplicates(enabled: Boolean)
    suspend fun setMinAudioDurationSec(seconds: Int)
    suspend fun setIncludeNonMusicAudio(enabled: Boolean)
    suspend fun setSongSort(sort: SongSort)
    suspend fun setSkipSilence(enabled: Boolean)
    suspend fun setRestoreLastSession(enabled: Boolean)
}
