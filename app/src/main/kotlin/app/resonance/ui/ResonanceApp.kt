package app.resonance.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import app.resonance.R
import app.resonance.core.model.PlaybackError
import app.resonance.core.model.PlaybackEvent
import app.resonance.core.model.SmartCollection
import app.resonance.core.ui.LibraryNavigator
import app.resonance.feature.home.navigation.HomeRoute
import app.resonance.feature.home.navigation.homeScreen
import app.resonance.feature.music.navigation.musicSection
import app.resonance.feature.music.navigation.navigateToAlbum
import app.resonance.feature.music.navigation.navigateToArtist
import app.resonance.feature.music.navigation.navigateToFolder
import app.resonance.feature.music.navigation.navigateToGenre
import app.resonance.feature.player.MiniPlayer
import app.resonance.feature.player.navigation.navigateToNowPlaying
import app.resonance.feature.player.navigation.nowPlayingScreen
import app.resonance.feature.playlists.AddToPlaylistDialog
import app.resonance.feature.playlists.navigation.navigateToPlaylist
import app.resonance.feature.playlists.navigation.navigateToSmartCollection
import app.resonance.feature.playlists.navigation.playlistsSection
import app.resonance.feature.search.navigation.navigateToSearch
import app.resonance.feature.search.navigation.searchScreen
import app.resonance.feature.settings.navigation.settingsScreen
import app.resonance.feature.video.navigation.startVideoPlayer
import app.resonance.feature.video.navigation.videosScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun ResonanceApp(
    playbackEvents: Flow<PlaybackEvent>,
    appVersion: String
) {
    val navController = rememberNavController()
    val appState = remember(navController) { ResonanceAppState(navController) }
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var addToPlaylist by rememberSaveable { mutableStateOf<LongArray?>(null) }

    val navigator = remember(navController) {
        LibraryNavigator(
            onAddToPlaylist = { ids -> addToPlaylist = ids.toLongArray() },
            onOpenAlbum = navController::navigateToAlbum,
            onOpenArtist = navController::navigateToArtist
        )
    }
    val playVideo: (Long) -> Unit = remember(context) { { id -> context.startVideoPlayer(id) } }

    // Playback failures (missing/corrupt/unsupported files) are reported once each.
    LaunchedEffect(playbackEvents) {
        playbackEvents.collect { event ->
            if (event is PlaybackEvent.Failed) {
                val title = event.title ?: resources.getString(R.string.error_unknown_title)
                val message = when (event.error) {
                    PlaybackError.FileNotFound -> R.string.error_file_missing
                    PlaybackError.UnsupportedFormat -> R.string.error_unsupported
                    PlaybackError.Corrupted -> R.string.error_corrupted
                    is PlaybackError.Unknown -> R.string.error_generic
                }
                snackbarHostState.showSnackbar(resources.getString(message, title))
            }
        }
    }

    val currentDestination = appState.currentDestination
    val adaptiveLayout = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfoV2())

    NavigationSuiteScaffold(
        layoutType = if (appState.showsNavigation) adaptiveLayout else NavigationSuiteType.None,
        navigationSuiteItems = {
            appState.topLevelDestinations.forEach { destination ->
                val selected = appState.isSelected(destination, currentDestination)
                item(
                    selected = selected,
                    onClick = { appState.navigateToTopLevel(destination) },
                    icon = {
                        Icon(
                            if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(destination.label)) },
                    modifier = Modifier.testTag("nav_${destination.name.lowercase()}")
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0)
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = HomeRoute,
                    modifier = Modifier.weight(1f)
                ) {
                    homeScreen(
                        onPlayVideo = playVideo,
                        onOpenNowPlaying = navController::navigateToNowPlaying,
                        onSearch = navController::navigateToSearch
                    )
                    musicSection(
                        navigator = navigator,
                        onOpenGenre = navController::navigateToGenre,
                        onOpenFolder = navController::navigateToFolder,
                        onSearch = navController::navigateToSearch,
                        onBack = navController::popBackStack
                    )
                    videosScreen(onPlayVideo = playVideo)
                    playlistsSection(
                        navigator = navigator,
                        onOpenPlaylist = navController::navigateToPlaylist,
                        onOpenSmart = { collection: SmartCollection ->
                            navController.navigateToSmartCollection(collection)
                        },
                        onBack = navController::popBackStack
                    )
                    settingsScreen(appVersion = appVersion)
                    searchScreen(navigator = navigator, onPlayVideo = playVideo, onBack = navController::popBackStack)
                    nowPlayingScreen(
                        onCollapse = navController::popBackStack,
                        onAddToPlaylist = navigator.onAddToPlaylist
                    )
                }
                if (appState.showsMiniPlayer) {
                    MiniPlayer(onExpand = navController::navigateToNowPlaying)
                }
            }
        }
    }

    addToPlaylist?.let { ids ->
        AddToPlaylistDialog(
            mediaIds = ids.toList(),
            onDismiss = { addToPlaylist = null },
            onFinished = { message ->
                addToPlaylist = null
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
        )
    }
}
