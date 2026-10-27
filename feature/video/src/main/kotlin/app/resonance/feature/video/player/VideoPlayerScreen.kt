package app.resonance.feature.video.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import app.resonance.core.common.formatDuration
import app.resonance.core.model.PlaybackError
import app.resonance.core.model.PlaybackState
import app.resonance.feature.video.R
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

internal data class VideoScreenCallbacks(
    val onBack: () -> Unit,
    val onRotate: () -> Unit,
    val onEnterPip: (() -> Unit)?
)

@OptIn(UnstableApi::class)
@Composable
internal fun VideoPlayerScreen(
    viewModel: VideoPlayerViewModel,
    inPictureInPicture: Boolean,
    callbacks: VideoScreenCallbacks
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var gestureLabel by remember { mutableStateOf<String?>(null) }

    // Auto-hide while playing; any interaction resets the timer by re-showing.
    LaunchedEffect(controlsVisible, state.isPlaying) {
        if (controlsVisible && state.isPlaying) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("video_player")
    ) {
        ContentFrame(
            player = viewModel.player,
            contentScale = state.aspect.contentScale,
            modifier = Modifier.fillMaxSize()
        )
        if (inPictureInPicture) return@Box

        GestureLayer(
            onToggleControls = { controlsVisible = !controlsVisible },
            onSeekBack = viewModel::seekBack,
            onSeekForward = viewModel::seekForward,
            onLabel = { gestureLabel = it }
        )

        gestureLabel?.let { label ->
            Text(
                label,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (state.isBuffering && state.error == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }

        state.error?.let { error ->
            ErrorOverlay(
                error,
                onBack = callbacks.onBack,
                onRetry = viewModel::retry,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && state.error == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            ControlsOverlay(viewModel, state, callbacks)
        }
    }
}

/**
 * Tap toggles controls, double-tap on either side seeks 10 s, and a vertical drag adjusts
 * brightness (left half) or volume (right half). Each gesture also has a visible control, so
 * nothing depends on gestures alone.
 */
@Composable
private fun GestureLayer(
    onToggleControls: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onLabel: (String?) -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val activity = remember(context) { context.findActivity() }
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val brightnessFormat = R.string.video_brightness
    val volumeFormat = R.string.video_volume
    var brightness by remember {
        mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: DEFAULT_BRIGHTNESS)
    }
    var volumeAccumulator by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = { offset -> if (offset.x < size.width / 2) onSeekBack() else onSeekForward() }
                )
            }
            .pointerInput(Unit) {
                var leftSide = true
                detectVerticalDragGestures(
                    onDragStart = { leftSide = it.x < size.width / 2 },
                    onDragEnd = { onLabel(null) },
                    onDragCancel = { onLabel(null) }
                ) { change, dragAmount ->
                    change.consume()
                    val delta = -dragAmount / size.height
                    if (leftSide) {
                        brightness = (brightness + delta).coerceIn(MIN_BRIGHTNESS, 1f)
                        activity?.window?.let { window ->
                            window.attributes = window.attributes.apply { screenBrightness = brightness }
                        }
                        onLabel(resources.getString(brightnessFormat, (brightness * 100).roundToInt()))
                    } else {
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        volumeAccumulator += delta * max
                        val steps = volumeAccumulator.toInt()
                        if (steps != 0) {
                            volumeAccumulator -= steps
                            val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                            audio.setStreamVolume(AudioManager.STREAM_MUSIC, (current + steps).coerceIn(0, max), 0)
                        }
                        val percent = audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max.coerceAtLeast(1)
                        onLabel(resources.getString(volumeFormat, percent))
                    }
                }
            }
    )
}

@Composable
private fun ControlsOverlay(viewModel: VideoPlayerViewModel, state: VideoUiState, callbacks: VideoScreenCallbacks) {
    val position by remember(viewModel) { viewModel.positionMs() }.collectAsStateWithLifecycle(0L)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(8.dp)
        ) {
            IconButton(onClick = callbacks.onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.video_back), tint = Color.White)
            }
            Text(
                state.title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            SpeedButton(state.speed, viewModel::setSpeed)
            val aspectName = stringResource(
                when (state.aspect) {
                    AspectMode.FIT -> R.string.video_aspect_fit
                    AspectMode.ZOOM -> R.string.video_aspect_zoom
                    AspectMode.STRETCH -> R.string.video_aspect_stretch
                }
            )
            IconButton(onClick = viewModel::cycleAspect) {
                Icon(Icons.Rounded.AspectRatio, stringResource(R.string.video_aspect, aspectName), tint = Color.White)
            }
            IconButton(onClick = callbacks.onRotate) {
                Icon(Icons.Rounded.ScreenRotation, stringResource(R.string.video_rotate), tint = Color.White)
            }
            callbacks.onEnterPip?.let { enterPip ->
                IconButton(onClick = enterPip) {
                    Icon(Icons.Rounded.PictureInPictureAlt, stringResource(R.string.video_pip), tint = Color.White)
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.Center)
        ) {
            IconButton(onClick = viewModel::seekBack, modifier = Modifier.size(56.dp)) {
                Icon(
                    Icons.Rounded.Replay10,
                    stringResource(R.string.video_rewind),
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
            IconButton(onClick = viewModel::togglePlayPause, modifier = Modifier.size(72.dp).testTag("video_toggle")) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    stringResource(if (state.isPlaying) R.string.video_pause else R.string.video_play),
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }
            IconButton(onClick = viewModel::seekForward, modifier = Modifier.size(56.dp)) {
                Icon(
                    Icons.Rounded.Forward10,
                    stringResource(R.string.video_forward),
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            VideoSeekBar(position, state.durationMs, viewModel::seekTo)
        }
    }
}

@Composable
private fun VideoSeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1L).toFloat()
    val shown = dragging ?: (positionMs / duration).coerceIn(0f, 1f)
    val elapsed = formatDuration((shown * duration).toLong())
    val total = formatDuration(durationMs)
    val label = stringResource(R.string.video_seek)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(elapsed, color = Color.White, style = MaterialTheme.typography.labelMedium)
        Slider(
            value = shown,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * duration).toLong()) }
                dragging = null
            },
            enabled = durationMs > 0,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .semantics {
                    contentDescription = label
                    stateDescription = "$elapsed / $total"
                }
        )
        Text(total, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SpeedButton(speed: Float, onSpeed: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.Speed, stringResource(R.string.video_speed), tint = Color.White)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PlaybackState.SPEED_CHOICES.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.video_speed_value, choice.toString())) },
                    trailingIcon = if (choice == speed) {
                        { Icon(Icons.Rounded.PlayArrow, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSpeed(choice)
                    }
                )
            }
        }
    }
}

@Composable
private fun ErrorOverlay(error: PlaybackError, onBack: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val message = stringResource(
        when (error) {
            PlaybackError.FileNotFound -> R.string.video_error_not_found
            PlaybackError.UnsupportedFormat, PlaybackError.Corrupted -> R.string.video_error_unsupported
            is PlaybackError.Unknown -> R.string.video_error_generic
        }
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.padding(32.dp)) {
        Icon(Icons.Rounded.VideocamOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        Text(message, color = Color.White, modifier = Modifier.padding(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onBack) { Text(stringResource(R.string.video_back)) }
            if (error is PlaybackError.Unknown) Button(onClick = onRetry) { Text(stringResource(R.string.video_play)) }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val CONTROLS_TIMEOUT_MS = 3_000L
private const val DEFAULT_BRIGHTNESS = 0.5f
private const val MIN_BRIGHTNESS = 0.01f
