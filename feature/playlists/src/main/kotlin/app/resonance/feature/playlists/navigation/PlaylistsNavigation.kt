package app.resonance.feature.playlists.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import app.resonance.core.model.SmartCollection
import app.resonance.core.ui.LibraryNavigator
import app.resonance.feature.playlists.PlaylistDetailRoute
import app.resonance.feature.playlists.PlaylistsRoute
import kotlinx.serialization.Serializable

@Serializable
data object PlaylistsRoute

/** Either a user playlist ([playlistId] >= 0) or a smart collection ([smart] = a [SmartCollection] name). */
@Serializable
data class PlaylistDetailRoute(val playlistId: Long = -1L, val smart: String? = null)

fun NavController.navigateToPlaylists(navOptions: NavOptions) = navigate(PlaylistsRoute, navOptions)

fun NavController.navigateToPlaylist(id: Long) = navigate(PlaylistDetailRoute(playlistId = id))

fun NavController.navigateToSmartCollection(collection: SmartCollection) =
    navigate(PlaylistDetailRoute(smart = collection.name))

fun NavGraphBuilder.playlistsSection(
    navigator: LibraryNavigator,
    onOpenPlaylist: (Long) -> Unit,
    onOpenSmart: (SmartCollection) -> Unit,
    onBack: () -> Unit
) {
    composable<PlaylistsRoute> {
        PlaylistsRoute(onOpenPlaylist = onOpenPlaylist, onOpenSmart = onOpenSmart)
    }
    composable<PlaylistDetailRoute> {
        PlaylistDetailRoute(navigator = navigator, onBack = onBack)
    }
}
