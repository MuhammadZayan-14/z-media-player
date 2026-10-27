package app.resonance.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import app.resonance.feature.settings.SettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

fun NavController.navigateToSettings(navOptions: NavOptions) = navigate(SettingsRoute, navOptions)

fun NavGraphBuilder.settingsScreen(appVersion: String) {
    composable<SettingsRoute> { SettingsRoute(appVersion = appVersion) }
}
