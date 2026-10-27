package app.resonance.feature.settings

import app.resonance.core.model.LibraryScanState
import app.resonance.core.model.ThemeMode
import app.resonance.core.testing.FakeHistoryRepository
import app.resonance.core.testing.FakeMediaLibraryRepository
import app.resonance.core.testing.FakeUserPreferencesRepository
import app.resonance.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val preferences = FakeUserPreferencesRepository()
    private val library = FakeMediaLibraryRepository()
    private val history = FakeHistoryRepository(library)
    private val viewModel by lazy { SettingsViewModel(preferences, library, history) }

    @Test
    fun settingsAreWrittenThroughAndReflected() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.setThemeMode(ThemeMode.DARK)
        viewModel.setHideDuplicates(false)
        viewModel.setMinDuration(30)
        viewModel.setSkipSilence(true)

        val prefs = (viewModel.uiState.value as SettingsUiState.Ready).preferences
        assertEquals(ThemeMode.DARK, prefs.themeMode)
        assertEquals(false, prefs.hideDuplicates)
        assertEquals(30, prefs.minAudioDurationSec)
        assertEquals(true, prefs.skipSilence)
    }

    @Test
    fun rescanAndClearHistoryReachTheirRepositories() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        history.recordPlay(1, 1)
        library.scanState.value = LibraryScanState.Done(5, 10)

        viewModel.rescan()
        viewModel.clearHistory()

        assertEquals(1, library.refreshCount)
        assertTrue(history.plays.value.isEmpty())
        assertEquals(LibraryScanState.Done(5, 10), (viewModel.uiState.value as SettingsUiState.Ready).scan)
    }
}
