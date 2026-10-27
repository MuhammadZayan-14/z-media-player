package app.resonance.feature.music.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import app.resonance.core.ui.LibraryNavigator
import app.resonance.feature.music.CollectionDetailRoute
import app.resonance.feature.music.MusicRoute
import kotlinx.serialization.Serializable

@Serializable
data object MusicRoute

/** [kind] is one of [CollectionKind]; albums and artists use [id], genres and folders use [key]. */
@Serializable
data class CollectionRoute(val kind: String, val id: Long = 0L, val key: String = "")

enum class CollectionKind { ALBUM, ARTIST, GENRE, FOLDER }

fun NavController.navigateToMusic(navOptions: NavOptions) = navigate(MusicRoute, navOptions)

fun NavController.navigateToAlbum(albumId: Long) = navigate(CollectionRoute(CollectionKind.ALBUM.name, id = albumId))

fun NavController.navigateToArtist(artistId: Long) = navigate(
    CollectionRoute(CollectionKind.ARTIST.name, id = artistId)
)

fun NavController.navigateToGenre(genre: String) = navigate(CollectionRoute(CollectionKind.GENRE.name, key = genre))

fun NavController.navigateToFolder(path: String) = navigate(CollectionRoute(CollectionKind.FOLDER.name, key = path))

fun NavGraphBuilder.musicSection(
    navigator: LibraryNavigator,
    onOpenGenre: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit
) {
    composable<MusicRoute> {
        MusicRoute(
            navigator = navigator,
            onOpenGenre = onOpenGenre,
            onOpenFolder = onOpenFolder,
            onSearch = onSearch
        )
    }
    composable<CollectionRoute> {
        CollectionDetailRoute(navigator = navigator, onBack = onBack)
    }
}
