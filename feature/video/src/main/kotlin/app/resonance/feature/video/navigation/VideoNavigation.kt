package app.resonance.feature.video.navigation

import android.content.Context
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import app.resonance.feature.video.VideoPlayerActivity
import app.resonance.feature.video.library.VideoLibraryRoute
import kotlinx.serialization.Serializable

@Serializable
data object VideosRoute

fun NavController.navigateToVideos(navOptions: NavOptions) = navigate(VideosRoute, navOptions)

/** The player is its own activity (for picture-in-picture), not a navigation destination. */
fun Context.startVideoPlayer(mediaId: Long) = startActivity(VideoPlayerActivity.intent(this, mediaId))

fun NavGraphBuilder.videosScreen(onPlayVideo: (Long) -> Unit) {
    composable<VideosRoute> { VideoLibraryRoute(onPlayVideo = onPlayVideo) }
}
