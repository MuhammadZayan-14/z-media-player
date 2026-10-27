package app.resonance.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.resonance.core.designsystem.component.Artwork
import app.resonance.core.model.PlaybackState

/** Renders nothing while the queue is empty. */
@Composable
fun MiniPlayer(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (!state.playback.hasMedia) return
    val position by viewModel.positionMs.collectAsStateWithLifecycle()
    MiniPlayerContent(
        playback = state.playback,
        positionMs = position,
        onExpand = onExpand,
        onTogglePlay = viewModel::togglePlayPause,
        onNext = viewModel::skipToNext,
        modifier = modifier
    )
}

@Composable
internal fun MiniPlayerContent(
    playback: PlaybackState,
    positionMs: Long,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val current = playback.current ?: return
    val openLabel = stringResource(R.string.player_mini_label, current.title)
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        modifier = modifier.testTag("mini_player")
    ) {
        Column {
            LinearProgressIndicator(
                progress = { if (playback.durationMs > 0) positionMs.toFloat() / playback.durationMs else 0f },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onExpand)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Artwork(uri = current.artworkUri, modifier = Modifier.size(44.dp))
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        current.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    current.artist?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onTogglePlay, modifier = Modifier.testTag("mini_player_toggle")) {
                    if (playback.isPlaying) {
                        Icon(Icons.Rounded.Pause, contentDescription = stringResource(R.string.player_pause))
                    } else {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.player_play))
                    }
                }
                IconButton(onClick = onNext, enabled = playback.hasNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.player_next))
                }
            }
        }
    }
}
