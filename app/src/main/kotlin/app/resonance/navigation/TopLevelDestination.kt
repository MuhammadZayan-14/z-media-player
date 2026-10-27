package app.resonance.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import app.resonance.R
import app.resonance.feature.home.navigation.HomeRoute
import app.resonance.feature.music.navigation.MusicRoute
import app.resonance.feature.playlists.navigation.PlaylistsRoute
import app.resonance.feature.settings.navigation.SettingsRoute
import app.resonance.feature.video.navigation.VideosRoute
import kotlin.reflect.KClass

enum class TopLevelDestination(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @param:StringRes val label: Int,
    val route: KClass<*>
) {
    HOME(Icons.Rounded.Home, Icons.Outlined.Home, R.string.nav_home, HomeRoute::class),
    MUSIC(Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic, R.string.nav_music, MusicRoute::class),
    VIDEOS(Icons.Rounded.VideoLibrary, Icons.Outlined.VideoLibrary, R.string.nav_videos, VideosRoute::class),
    PLAYLISTS(
        Icons.AutoMirrored.Rounded.QueueMusic,
        Icons.AutoMirrored.Outlined.QueueMusic,
        R.string.nav_playlists,
        PlaylistsRoute::class
    ),
    SETTINGS(Icons.Rounded.Settings, Icons.Outlined.Settings, R.string.nav_settings, SettingsRoute::class)
}
