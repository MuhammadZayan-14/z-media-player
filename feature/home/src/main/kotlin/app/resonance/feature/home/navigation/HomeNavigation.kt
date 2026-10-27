package app.resonance.feature.home.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import app.resonance.feature.home.HomeRoute
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

fun NavController.navigateToHome(navOptions: NavOptions) = navigate(HomeRoute, navOptions)

fun NavGraphBuilder.homeScreen(
    onPlayVideo: (Long) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onSearch: () -> Unit
) {
    composable<HomeRoute> {
        HomeRoute(onPlayVideo = onPlayVideo, onOpenNowPlaying = onOpenNowPlaying, onSearch = onSearch)
    }
}
