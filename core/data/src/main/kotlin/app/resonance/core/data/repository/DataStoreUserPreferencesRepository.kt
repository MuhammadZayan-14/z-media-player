package app.resonance.core.data.repository

import app.resonance.core.datastore.UserPreferencesDataSource
import app.resonance.core.domain.repository.UserPreferencesRepository
import app.resonance.core.model.SongSort
import app.resonance.core.model.ThemeMode
import app.resonance.core.model.UserPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class DataStoreUserPreferencesRepository @Inject constructor(
    private val source: UserPreferencesDataSource
) : UserPreferencesRepository {
    override val preferences: Flow<UserPreferences> = source.preferences

    override suspend fun setThemeMode(mode: ThemeMode) = source.setThemeMode(mode)
    override suspend fun setUseDynamicColor(enabled: Boolean) = source.setUseDynamicColor(enabled)
    override suspend fun setHideDuplicates(enabled: Boolean) = source.setHideDuplicates(enabled)
    override suspend fun setMinAudioDurationSec(seconds: Int) = source.setMinAudioDurationSec(seconds)
    override suspend fun setIncludeNonMusicAudio(enabled: Boolean) = source.setIncludeNonMusicAudio(enabled)
    override suspend fun setSongSort(sort: SongSort) = source.setSongSort(sort)
    override suspend fun setSkipSilence(enabled: Boolean) = source.setSkipSilence(enabled)
    override suspend fun setRestoreLastSession(enabled: Boolean) = source.setRestoreLastSession(enabled)
}
