package app.resonance.feature.playlists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.resonance.core.designsystem.theme.ResonanceTheme
import app.resonance.core.domain.usecase.PlaylistNameResult
import app.resonance.core.model.Playlist
import app.resonance.core.model.SmartCollection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistsUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun listShowsSmartCollectionsAndUserPlaylists() {
        val opened = mutableListOf<Any>()
        var creates = 0
        compose.setContent {
            ResonanceTheme(dynamicColor = false) {
                PlaylistsScreen(
                    state = PlaylistsUiState(isLoading = false, playlists = listOf(Playlist(3, "Road trip", 12, 0, 0))),
                    onOpenPlaylist = { opened += it },
                    onOpenSmart = { opened += it },
                    onCreate = { creates++ }
                )
            }
        }
        compose.onNodeWithText("Favorites").performClick()
        compose.onNodeWithText("Road trip").performClick()
        compose.onNodeWithText("12 tracks").assertIsDisplayed()
        compose.onNodeWithTag("create_playlist").performClick()

        assertEquals(listOf<Any>(SmartCollection.FAVORITES, 3L), opened)
        assertEquals(1, creates)
    }

    @Test
    fun emptyStateInvitesCreatingOne() {
        compose.setContent {
            ResonanceTheme(dynamicColor = false) {
                PlaylistsScreen(PlaylistsUiState(isLoading = false), {}, {}, {})
            }
        }
        compose.onNodeWithText("No playlists yet").assertIsDisplayed()
    }

    @Test
    fun nameDialogSubmitsTypedTextAndShowsValidationErrors() {
        val submitted = mutableListOf<String>()
        compose.setContent {
            ResonanceTheme(dynamicColor = false) {
                PlaylistNameDialog(
                    title = "New playlist",
                    confirmLabel = "Create",
                    initialName = "",
                    error = PlaylistNameResult.Duplicate,
                    onConfirm = { submitted += it },
                    onDismiss = {}
                )
            }
        }
        compose.onNodeWithText("A playlist with this name already exists").assertIsDisplayed()
        compose.onNodeWithTag("playlist_name_field").performTextInput("Focus")
        compose.onNodeWithTag("playlist_name_confirm").performClick()
        assertEquals(listOf("Focus"), submitted)
    }
}
