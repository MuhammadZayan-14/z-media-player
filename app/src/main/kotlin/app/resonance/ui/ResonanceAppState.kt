package app.resonance.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navOptions
import app.resonance.feature.home.navigation.navigateToHome
import app.resonance.feature.music.navigation.navigateToMusic
import app.resonance.feature.player.navigation.NowPlayingRoute
import app.resonance.feature.playlists.navigation.navigateToPlaylists
import app.resonance.feature.settings.navigation.navigateToSettings
import app.resonance.feature.video.navigation.navigateToVideos
import app.resonance.navigation.TopLevelDestination

/** UI-only navigation state for the app shell; no business logic lives here. */
@Stable
class ResonanceAppState(val navController: NavHostController) {

    val topLevelDestinations: List<TopLevelDestination> = TopLevelDestination.entries

    val currentDestination: NavDestination?
        @Composable get() = navController.currentBackStackEntryAsState().value?.destination

    private val isNowPlaying: Boolean
        @Composable get() = currentDestination?.hasRoute(NowPlayingRoute::class) == true

    /** The full-screen player hides the navigation bar/rail and the mini player. */
    val showsNavigation: Boolean
        @Composable get() = !isNowPlaying

    val showsMiniPlayer: Boolean
        @Composable get() = !isNowPlaying

    fun isSelected(destination: TopLevelDestination, current: NavDestination?): Boolean =
        current?.hierarchy?.any { it.hasRoute(destination.route) } == true

    /**
     * Each tab keeps its own back stack: switching saves the current one and restores the target's,
     * and re-selecting a tab does not stack duplicates.
     */
    fun navigateToTopLevel(destination: TopLevelDestination) {
        val options = navOptions {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        when (destination) {
            TopLevelDestination.HOME -> navController.navigateToHome(options)
            TopLevelDestination.MUSIC -> navController.navigateToMusic(options)
            TopLevelDestination.VIDEOS -> navController.navigateToVideos(options)
            TopLevelDestination.PLAYLISTS -> navController.navigateToPlaylists(options)
            TopLevelDestination.SETTINGS -> navController.navigateToSettings(options)
        }
    }
}
