package app.resonance.feature.video

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.resonance.core.designsystem.theme.ResonanceTheme
import app.resonance.feature.video.player.VideoPlayerScreen
import app.resonance.feature.video.player.VideoPlayerViewModel
import app.resonance.feature.video.player.VideoScreenCallbacks
import app.resonance.feature.video.player.VideoUiState
import dagger.hilt.android.AndroidEntryPoint

/**
 * Full-screen video playback with immersive mode, rotation handling and picture-in-picture.
 * Launched for a library video by id, or by another app with a `content://` video URI.
 */
@AndroidEntryPoint
class VideoPlayerActivity : ComponentActivity() {

    private val viewModel: VideoPlayerViewModel by viewModels()
    private var inPip by mutableStateOf(false)
    private val supportsPip by lazy { packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) }

    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_TOGGLE_PLAYBACK) viewModel.togglePlayPause()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // An ACTION_VIEW URI becomes a regular extra so the ViewModel reads one source of truth.
        if (intent.action == Intent.ACTION_VIEW && intent.data != null &&
            !intent.hasExtra(VideoPlayerViewModel.EXTRA_URI)
        ) {
            intent.putExtra(VideoPlayerViewModel.EXTRA_URI, intent.data.toString())
        }
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        ContextCompat.registerReceiver(
            this,
            pipActionReceiver,
            IntentFilter(ACTION_TOGGLE_PLAYBACK),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(state.isPlaying, state.videoAspectRatio) { onPlaybackChanged(state) }
            ResonanceTheme(darkTheme = true) {
                VideoPlayerScreen(
                    viewModel = viewModel,
                    inPictureInPicture = inPip,
                    callbacks = VideoScreenCallbacks(
                        onBack = ::finish,
                        onRotate = ::toggleOrientation,
                        onEnterPip = if (supportsPip) ::enterPip else null
                    )
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask: a different video opened while this one is in PiP restarts the screen for it.
        if (intent.getLongExtra(VideoPlayerViewModel.EXTRA_MEDIA_ID, -1L) !=
            this.intent.getLongExtra(VideoPlayerViewModel.EXTRA_MEDIA_ID, -1L) ||
            intent.data != this.intent.data
        ) {
            finish()
            startActivity(intent)
        }
    }

    private var orientationFixedForVideo = false

    private fun onPlaybackChanged(state: VideoUiState) {
        if (state.isPlaying) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        // Landscape videos open in landscape once; after that the rotate button is in charge.
        val ratio = state.videoAspectRatio
        if (!orientationFixedForVideo && ratio != null) {
            orientationFixedForVideo = true
            if (ratio > 1f) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        if (supportsPip) setPictureInPictureParams(pipParams(state))
    }

    private fun toggleOrientation() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun pipParams(state: VideoUiState = viewModel.uiState.value): PictureInPictureParams {
        val icon = if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val title = getString(if (state.isPlaying) R.string.video_pause else R.string.video_play)
        val toggle = RemoteAction(
            Icon.createWithResource(this, icon),
            title,
            title,
            PendingIntent.getBroadcast(
                this,
                0,
                Intent(ACTION_TOGGLE_PLAYBACK).setPackage(packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        return PictureInPictureParams.Builder()
            .setActions(listOf(toggle))
            .apply {
                // PiP only accepts ratios between 1:2.39 and 2.39:1.
                state.videoAspectRatio?.let { ratio ->
                    val clamped = ratio.coerceIn(MIN_PIP_RATIO, MAX_PIP_RATIO)
                    setAspectRatio(Rational((clamped * RATIO_SCALE).toInt(), RATIO_SCALE))
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(state.isPlaying)
                    setSeamlessResizeEnabled(true)
                }
            }
            .build()
    }

    private fun enterPip() {
        if (supportsPip) enterPictureInPictureMode(pipParams())
    }

    /** Below Android 12 there is no auto-enter, so PiP starts when the user leaves with Home. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && supportsPip &&
            viewModel.uiState.value.isPlaying
        ) {
            enterPip()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        if (!isInPictureInPictureMode) hideSystemBars()
    }

    override fun onStop() {
        super.onStop()
        // Leaving the screen pauses playback; in PiP the window is still visible, so keep playing.
        if (!isInPictureInPictureMode) viewModel.onBackgrounded()
    }

    override fun onDestroy() {
        unregisterReceiver(pipActionReceiver)
        super.onDestroy()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    companion object {
        private const val ACTION_TOGGLE_PLAYBACK = "app.resonance.video.TOGGLE_PLAYBACK"
        private const val MIN_PIP_RATIO = 1f / 2.39f
        private const val MAX_PIP_RATIO = 2.39f
        private const val RATIO_SCALE = 10_000

        fun intent(context: Context, mediaId: Long): Intent =
            Intent(context, VideoPlayerActivity::class.java).putExtra(VideoPlayerViewModel.EXTRA_MEDIA_ID, mediaId)

        fun intent(context: Context, uri: android.net.Uri): Intent =
            Intent(context, VideoPlayerActivity::class.java)
                .putExtra(VideoPlayerViewModel.EXTRA_URI, uri.toString())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
