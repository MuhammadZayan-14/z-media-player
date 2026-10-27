package app.resonance.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.resonance.core.common.Log
import app.resonance.core.model.SongSort
import app.resonance.core.model.ThemeMode
import app.resonance.core.model.UserPreferences
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
class UserPreferencesDataSource @Inject constructor(
    @UserPreferencesStore private val dataStore: DataStore<Preferences>
) {
    val preferences: Flow<UserPreferences> = dataStore.data
        .catch { error ->
            // A corrupt or unreadable file must not take the app down: fall back to defaults.
            if (error is IOException) {
                Log.w(TAG, "prefs_read_failed", error)
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { it.toUserPreferences() }
        .distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.name }
    suspend fun setUseDynamicColor(enabled: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = enabled }
    suspend fun setHideDuplicates(enabled: Boolean) = edit { it[Keys.HIDE_DUPLICATES] = enabled }
    suspend fun setMinAudioDurationSec(seconds: Int) = edit { it[Keys.MIN_AUDIO_DURATION] = seconds.coerceAtLeast(0) }
    suspend fun setIncludeNonMusicAudio(enabled: Boolean) = edit { it[Keys.INCLUDE_NON_MUSIC] = enabled }
    suspend fun setSongSort(sort: SongSort) = edit { it[Keys.SONG_SORT] = sort.name }
    suspend fun setSkipSilence(enabled: Boolean) = edit { it[Keys.SKIP_SILENCE] = enabled }
    suspend fun setRestoreLastSession(enabled: Boolean) = edit { it[Keys.RESTORE_SESSION] = enabled }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private fun Preferences.toUserPreferences(): UserPreferences {
        val defaults = UserPreferences()
        return UserPreferences(
            themeMode = enumOrDefault(this[Keys.THEME_MODE], defaults.themeMode),
            useDynamicColor = this[Keys.DYNAMIC_COLOR] ?: defaults.useDynamicColor,
            hideDuplicates = this[Keys.HIDE_DUPLICATES] ?: defaults.hideDuplicates,
            minAudioDurationSec = this[Keys.MIN_AUDIO_DURATION] ?: defaults.minAudioDurationSec,
            includeNonMusicAudio = this[Keys.INCLUDE_NON_MUSIC] ?: defaults.includeNonMusicAudio,
            songSort = enumOrDefault(this[Keys.SONG_SORT], defaults.songSort),
            skipSilence = this[Keys.SKIP_SILENCE] ?: defaults.skipSilence,
            restoreLastSession = this[Keys.RESTORE_SESSION] ?: defaults.restoreLastSession
        )
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val HIDE_DUPLICATES = booleanPreferencesKey("hide_duplicates")
        val MIN_AUDIO_DURATION = intPreferencesKey("min_audio_duration_sec")
        val INCLUDE_NON_MUSIC = booleanPreferencesKey("include_non_music")
        val SONG_SORT = stringPreferencesKey("song_sort")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val RESTORE_SESSION = booleanPreferencesKey("restore_session")
    }

    private companion object {
        const val TAG = "Prefs"
    }
}

/** Unknown names (e.g. written by a newer app version) fall back to the default instead of crashing. */
internal inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
    name?.let { value -> enumValues<E>().firstOrNull { it.name == value } } ?: default
