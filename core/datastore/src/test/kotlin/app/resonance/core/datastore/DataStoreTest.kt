package app.resonance.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.resonance.core.model.SongSort
import app.resonance.core.model.ThemeMode
import app.resonance.core.model.UserPreferences
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/*
 * Robolectric so that Build.VERSION.SDK_INT is a real API level: DataStore replaces its file with
 * Files.move on API 26+, and with File.renameTo otherwise, which fails on Windows hosts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DataStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val testScope = TestScope(UnconfinedTestDispatcher())
    private val store = PreferenceDataStoreFactory.createWithPath(scope = testScope.backgroundScope) {
        File(tmp.root, "test.preferences_pb").toOkioPath()
    }

    @Test
    fun defaultsWhenNothingWasWritten() = testScope.runTest {
        assertEquals(UserPreferences(), UserPreferencesDataSource(store).preferences.first())
    }

    @Test
    fun writesRoundTrip() = testScope.runTest {
        val source = UserPreferencesDataSource(store)
        source.setThemeMode(ThemeMode.DARK)
        source.setSongSort(SongSort.DATE_ADDED)
        source.setMinAudioDurationSec(-5)
        source.setRestoreLastSession(false)

        val prefs = source.preferences.first()
        assertEquals(ThemeMode.DARK, prefs.themeMode)
        assertEquals(SongSort.DATE_ADDED, prefs.songSort)
        assertEquals(0, prefs.minAudioDurationSec)
        assertEquals(false, prefs.restoreLastSession)
    }

    @Test
    fun unknownEnumNamesFallBackToDefaults() = testScope.runTest {
        store.edit { it[stringPreferencesKey("theme_mode")] = "SEPIA" }
        assertEquals(ThemeMode.SYSTEM, UserPreferencesDataSource(store).preferences.first().themeMode)
    }

    @Test
    fun syncStateRoundTripsGenerationsPerVolume() = testScope.runTest {
        val source = LibrarySyncStateDataSource(store)
        val state = LibrarySyncState("v1|FULL|FULL", mapOf("external_primary" to 42L, "1a2b-3c4d" to 7L), 99L)
        source.write(state)
        assertEquals(state, source.read())
        source.reset()
        assertEquals(LibrarySyncState(null, emptyMap(), null), source.read())
    }

    @Test
    fun malformedGenerationEntriesAreSkipped() {
        assertEquals(mapOf("a" to 1L), LibrarySyncStateDataSource.decodeGenerations("a=1;b=x;=3;c"))
    }
}
