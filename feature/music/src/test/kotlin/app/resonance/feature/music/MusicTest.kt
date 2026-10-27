package app.resonance.feature.music

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.resonance.core.designsystem.theme.ResonanceTheme
import app.resonance.core.domain.usecase.ToggleFavoriteUseCase
import app.resonance.core.domain.usecase.TrackCommands
import app.resonance.core.model.SongSort
import app.resonance.core.testing.FakeFavoritesRepository
import app.resonance.core.testing.FakeMediaLibraryRepository
import app.resonance.core.testing.FakePlaybackController
import app.resonance.core.testing.FakeUserPreferencesRepository
import app.resonance.core.testing.MainDispatcherRule
import app.resonance.core.testing.testSong
import app.resonance.core.ui.TrackActions
import app.resonance.feature.music.navigation.CollectionKind
import app.resonance.feature.music.navigation.CollectionRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MusicViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val songs = listOf(
        testSong(1, title = "Charlie", album = "Z"),
        testSong(2, title = "alpha", album = "Y"),
        testSong(3, title = "Bravo", album = "X")
    )
    private val library = FakeMediaLibraryRepository(songs)
    private val favorites = FakeFavoritesRepository(library)
    private val controller = FakePlaybackController()
    private val preferences = FakeUserPreferencesRepository()
    private val commands = TrackCommands(controller, ToggleFavoriteUseCase(favorites))

    @Test
    fun sortChoiceIsPersistedAndApplied() = runTest {
        val vm = MusicViewModel(library, preferences, favorites, controller, commands)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(listOf(2L, 3L, 1L), vm.uiState.value.content.songs.map { it.id })

        vm.setSort(SongSort.ALBUM)

        assertEquals(SongSort.ALBUM, preferences.preferences.value.songSort)
        assertEquals(listOf(3L, 2L, 1L), vm.uiState.value.content.songs.map { it.id })
    }

    @Test
    fun playingASongQueuesTheWholeSortedListFromThatSong() = runTest {
        val vm = MusicViewModel(library, preferences, favorites, controller, commands)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.playSong(1)

        assertEquals("play([2, 3, 1], 1, shuffle=false)", controller.commands.single())
        assertEquals(3L, vm.uiState.value.currentMediaId)
    }

    @Test
    fun favoritingFromTheMenuUpdatesState() = runTest {
        val vm = MusicViewModel(library, preferences, favorites, controller, commands)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.toggleFavorite(songs[0])

        assertEquals(setOf(1L), vm.uiState.value.favoriteIds)
    }

    @Test
    fun albumDetailShowsItsTracksAndHeader() = runTest {
        val albumId = songs[0].albumId
        val vm = CollectionDetailViewModel(
            SavedStateHandle(route = CollectionRoute(CollectionKind.ALBUM.name, id = albumId)),
            library,
            favorites,
            controller,
            commands
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        val ready = vm.uiState.value as CollectionUiState.Ready
        assertEquals("Z", ready.header.title)
        assertEquals(listOf(1L), ready.tracks.map { it.id })
    }
}

@RunWith(AndroidJUnit4::class)
class MusicUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val callbacks = MusicCallbacks({}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    private val actions = TrackActions({}, {}, {}, {})

    @Test
    fun missingAccessShowsThePermissionRequestInsteadOfTheLibrary() {
        compose.setContent {
            ResonanceTheme(dynamicColor = false) {
                MusicScreen(
                    state = MusicUiState(isLoading = false),
                    hasAudioAccess = false,
                    permissionContent = { Text("permission-rationale") },
                    actions = actions,
                    callbacks = callbacks
                )
            }
        }
        compose.onNodeWithText("permission-rationale").assertIsDisplayed()
    }

    @Test
    fun songsAreListedAndTabsSwitchContent() {
        var played = -1
        val content = LibraryContent(
            songs = listOf(testSong(1, title = "Teardrop", album = "Mezzanine")),
            albums = listOf(app.resonance.core.model.Album(9, "Mezzanine", "Massive Attack", 11, 1998, ""))
        )
        compose.setContent {
            ResonanceTheme(dynamicColor = false) {
                MusicScreen(
                    state = MusicUiState(isLoading = false, content = content),
                    hasAudioAccess = true,
                    permissionContent = {},
                    actions = actions,
                    callbacks = callbacks.copy(onPlaySong = { played = it })
                )
            }
        }
        compose.onNodeWithText("Teardrop").performClick()
        assertEquals(0, played)

        compose.onNodeWithTag("tab_albums").performClick()
        compose.onNodeWithText("Massive Attack · 1998 · 11 tracks").assertIsDisplayed()
    }

    @Test
    fun emptyLibraryOffersARescan() {
        var rescans = 0
        compose.setContent {
            ResonanceTheme(dynamicColor = false) {
                MusicScreen(
                    state = MusicUiState(isLoading = false),
                    hasAudioAccess = true,
                    permissionContent = {},
                    actions = actions,
                    callbacks = callbacks.copy(onRefresh = { rescans++ })
                )
            }
        }
        compose.onNodeWithText("No music found").assertIsDisplayed()
        compose.onNodeWithText("Scan again").performClick()
        assertEquals(1, rescans)
    }
}
