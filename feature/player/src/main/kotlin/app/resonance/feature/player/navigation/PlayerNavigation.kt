package app.resonance.feature.player.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.resonance.feature.player.NowPlayingRoute
import kotlinx.serialization.Serializable

@Serializable
data object NowPlayingRoute

fun NavController.navigateToNowPlaying() = navigate(NowPlayingRoute) { launchSingleTop = true }

fun NavGraphBuilder.nowPlayingScreen(
    onCollapse: () -> Unit,
    onAddToPlaylist: (List<Long>) -> Unit
) {
    composable<NowPlayingRoute>(
        enterTransition = { slideInVertically(tween(DURATION_MS)) { it } },
        exitTransition = { slideOutVertically(tween(DURATION_MS)) { it } },
        popExitTransition = { slideOutVertically(tween(DURATION_MS)) { it } }
    ) {
        NowPlayingRoute(onCollapse = onCollapse, onAddToPlaylist = onAddToPlaylist)
    }
}

private const val DURATION_MS = 300
