package app.resonance.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.resonance.core.common.formatDuration
import app.resonance.core.designsystem.component.Artwork
import app.resonance.core.designsystem.component.MessageState
import app.resonance.core.model.PlaybackState
import app.resonance.core.model.RepeatMode

@Composable
fun NowPlayingRoute(
    onCollapse: () -> Unit,
    onAddToPlaylist: (List<Long>) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val position by viewModel.positionMs.collectAsStateWithLifecycle()
    var showQueue by rememberSaveable { mutableStateOf(false) }

    NowPlayingScreen(
        state = state,
        positionMs = position,
        onCollapse = onCollapse,
        actions = NowPlayingActions(
            onTogglePlay = viewModel::togglePlayPause,
            onNext = viewModel::skipToNext,
            onPrevious = viewModel::skipToPrevious,
            onSeek = viewModel::seekTo,
            onShuffle = viewModel::toggleShuffle,
            onRepeat = viewModel::cycleRepeatMode,
            onSpeed = viewModel::setSpeed,
            onFavorite = viewModel::toggleFavorite,
            onAddToPlaylist = { state.playback.current?.libraryId?.let { onAddToPlaylist(listOf(it)) } },
            onShowQueue = { showQueue = true }
        )
    )
    if (showQueue) {
        QueueSheet(
            playback = state.playback,
            onDismiss = { showQueue = false },
            onSelect = viewModel::skipToQueueIndex,
            onRemove = viewModel::removeFromQueue,
            onMove = viewModel::moveQueueItem
        )
    }
}

internal data class NowPlayingActions(
    val onTogglePlay: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onShuffle: () -> Unit,
    val onRepeat: () -> Unit,
    val onSpeed: (Float) -> Unit,
    val onFavorite: () -> Unit,
    val onAddToPlaylist: () -> Unit,
    val onShowQueue: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NowPlayingScreen(
    state: PlayerUiState,
    positionMs: Long,
    onCollapse: () -> Unit,
    actions: NowPlayingActions
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onCollapse) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.player_collapse)
                        )
                    }
                },
                actions = {
                    if (state.canFavorite) {
                        IconButton(onClick = actions.onAddToPlaylist) {
                            Icon(
                                Icons.AutoMirrored.Rounded.PlaylistAdd,
                                contentDescription = stringResource(R.string.player_add_to_playlist)
                            )
                        }
                    }
                    IconButton(onClick = actions.onShowQueue) {
                        Icon(
                            Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = stringResource(R.string.player_queue)
                        )
                    }
                }
            )
        }
    ) { padding ->
        val current = state.playback.current
        if (current == null) {
            MessageState(
                icon = Icons.Rounded.MusicOff,
                title = stringResource(R.string.player_nothing_playing),
                message = stringResource(R.string.player_nothing_playing_message),
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            val wide = maxWidth > maxHeight && maxWidth >= 560.dp
            val artwork = @Composable { modifier: Modifier ->
                Artwork(
                    uri = current.artworkUri,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = modifier.aspectRatio(1f)
                )
            }
            val controls = @Composable { modifier: Modifier ->
                PlayerControls(state, positionMs, actions, modifier)
            }
            if (wide) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    artwork(Modifier.weight(1f, fill = false).fillMaxSize())
                    controls(Modifier.weight(1f))
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        artwork(Modifier.widthIn(max = 420.dp).fillMaxWidth())
                    }
                    controls(Modifier.fillMaxWidth().padding(top = 24.dp))
                }
            }
        }
    }
}

@Composable
private fun PlayerControls(
    state: PlayerUiState,
    positionMs: Long,
    actions: NowPlayingActions,
    modifier: Modifier = Modifier
) {
    val playback = state.playback
    val current = playback.current ?: return
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    current.title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("now_playing_title")
                )
                current.artist?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (state.canFavorite) {
                IconToggleButton(checked = state.isFavorite, onCheckedChange = { actions.onFavorite() }) {
                    if (state.isFavorite) {
                        Icon(
                            Icons.Rounded.Favorite,
                            stringResource(R.string.player_favorite_remove),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(Icons.Rounded.FavoriteBorder, stringResource(R.string.player_favorite_add))
                    }
                }
            }
        }

        SeekBar(positionMs = positionMs, durationMs = playback.durationMs, onSeek = actions.onSeek)

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            val shuffleLabel =
                stringResource(if (playback.shuffleEnabled) R.string.player_shuffle_on else R.string.player_shuffle_off)
            IconToggleButton(
                checked = playback.shuffleEnabled,
                onCheckedChange = { actions.onShuffle() },
                modifier = Modifier.semantics { contentDescription = shuffleLabel }
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null)
            }
            IconButton(onClick = actions.onPrevious) {
                Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.player_previous), Modifier.size(32.dp))
            }
            FilledIconButton(
                onClick = actions.onTogglePlay,
                modifier = Modifier.size(72.dp).testTag("now_playing_toggle")
            ) {
                when {
                    playback.isBuffering -> CircularProgressIndicator(Modifier.size(32.dp))

                    playback.isPlaying -> Icon(
                        Icons.Rounded.Pause,
                        stringResource(R.string.player_pause),
                        Modifier.size(40.dp)
                    )

                    else -> Icon(Icons.Rounded.PlayArrow, stringResource(R.string.player_play), Modifier.size(40.dp))
                }
            }
            IconButton(onClick = actions.onNext, enabled = playback.hasNext) {
                Icon(Icons.Rounded.SkipNext, stringResource(R.string.player_next), Modifier.size(32.dp))
            }
            val repeatLabel = stringResource(
                when (playback.repeatMode) {
                    RepeatMode.OFF -> R.string.player_repeat_off
                    RepeatMode.ALL -> R.string.player_repeat_all
                    RepeatMode.ONE -> R.string.player_repeat_one
                }
            )
            IconToggleButton(
                checked = playback.repeatMode != RepeatMode.OFF,
                onCheckedChange = { actions.onRepeat() },
                modifier = Modifier.semantics { contentDescription = repeatLabel }
            ) {
                Icon(if (playback.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, null)
            }
        }
        SpeedSelector(speed = playback.speed, onSpeed = actions.onSpeed)
    }
}

/** The slider follows the finger while dragging and seeks once, on release. */
@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1L).toFloat()
    val shown = dragging ?: (positionMs / duration).coerceIn(0f, 1f)
    val elapsed = formatDuration((shown * duration).toLong())
    val total = formatDuration(durationMs)
    val seekLabel = stringResource(R.string.player_seek)
    Column(Modifier.padding(top = 16.dp)) {
        Slider(
            value = shown,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * duration).toLong()) }
                dragging = null
            },
            enabled = durationMs > 0,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("seek_bar")
                .semantics {
                    contentDescription = seekLabel
                    stateDescription = "$elapsed / $total"
                }
        )
        Row(Modifier.fillMaxWidth()) {
            Text(elapsed, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(total, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun SpeedSelector(speed: Float, onSpeed: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(R.string.player_speed_value, speed.toString())
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(R.string.player_speed, label))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PlaybackState.SPEED_CHOICES.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.player_speed_value, choice.toString())) },
                    onClick = {
                        expanded = false
                        onSpeed(choice)
                    }
                )
            }
        }
    }
}
