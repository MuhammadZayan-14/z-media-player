package app.resonance.feature.search.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.resonance.core.ui.LibraryNavigator
import app.resonance.feature.search.SearchRoute
import kotlinx.serialization.Serializable

@Serializable
data object SearchRoute

fun NavController.navigateToSearch() = navigate(SearchRoute) { launchSingleTop = true }

fun NavGraphBuilder.searchScreen(
    navigator: LibraryNavigator,
    onPlayVideo: (Long) -> Unit,
    onBack: () -> Unit
) {
    composable<SearchRoute> { SearchRoute(navigator = navigator, onPlayVideo = onPlayVideo, onBack = onBack) }
}
